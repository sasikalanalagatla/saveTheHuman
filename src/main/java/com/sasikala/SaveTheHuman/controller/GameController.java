package com.sasikala.SaveTheHuman.controller;

import com.sasikala.SaveTheHuman.entity.Question;
import com.sasikala.SaveTheHuman.entity.User;
import com.sasikala.SaveTheHuman.repository.QuestionRepository;
import com.sasikala.SaveTheHuman.repository.UserRepository;
import com.sasikala.SaveTheHuman.service.AIQuestionService;
import com.sasikala.SaveTheHuman.service.GameService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Controller
public class GameController {

    @Autowired
    private GameService gameService;

    @Autowired
    private AIQuestionService aiQuestionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final Random random = new Random();

    @GetMapping("/")
    public String index() {
        return "home";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam String username, @RequestParam String password, HttpSession session) {
        if (userRepository.findByUsername(username).isPresent()) {
            return "redirect:/register?error=exists";
        }
        User user = new User(username, passwordEncoder.encode(password));
        userRepository.save(user);
        // Removed immediate AI generation - will happen after difficulty selection
        return "redirect:/login?registered=true";
    }

    @GetMapping("/difficulty-select")
    public String difficultySelect() {
        return "difficulty-select";
    }

    @PostMapping("/set-difficulty")
    @jakarta.transaction.Transactional
    public String setDifficulty(@RequestParam String difficulty, 
                               @AuthenticationPrincipal UserDetails userDetails,
                               HttpSession session) {
        User user = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (user == null) return "redirect:/login";

        user.setDifficultyLevel(difficulty);
        userRepository.save(user);

        // Clear old questions
        questionRepository.deleteByUser(user);
        questionRepository.flush(); // Ensure deletion is committed before generation

        // Generate new questions
        try {
            aiQuestionService.generateInitialQuestionsForUser(user);
            aiQuestionService.generateRemainingQuestionsAsync(user);
        } catch (Exception e) {
            session.setAttribute("message", "AI Generation Error: " + e.getMessage());
        }

        return "redirect:/level-select";
    }

    @GetMapping("/start-auth")
    public String startAuthTransition(HttpSession session) {
        return "redirect:/difficulty-select";
    }

    @GetMapping("/level-select")
    public String levelSelect(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (user == null) return "redirect:/login";
        
        model.addAttribute("userCurrentLevel", user.getCurrentLevel());
        model.addAttribute("username", user.getUsername());
        return "level-select";
    }

    @PostMapping("/start")
    public String startGame(@RequestParam(value = "level", defaultValue = "1") int level, 
                           @AuthenticationPrincipal UserDetails userDetails,
                           HttpSession session) {
        User user = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (user == null) {
            System.err.println("User not found: " + userDetails.getUsername());
            return "redirect:/login";
        }

        System.out.println("User " + user.getUsername() + " starting Level " + level);
        List<Question> levelQuestions = questionRepository.findByUserAndLevel(user, level);
        
        if (levelQuestions.isEmpty()) {
            try {
                aiQuestionService.generateInitialQuestionsForUser(user);
                aiQuestionService.generateRemainingQuestionsAsync(user);
                levelQuestions = questionRepository.findByUserAndLevel(user, level);
            } catch (Exception e) {
                session.setAttribute("message", "AI Error: " + e.getMessage());
                return "redirect:/";
            }
        }

        if (levelQuestions.isEmpty()) {
             session.setAttribute("message", "System Error: No questions available. AI API might be out of quota.");
             return "redirect:/";
        }

        List<Question> uncompletedQuestions = levelQuestions.stream()
                .filter(q -> !q.isCompleted())
                .toList();

        Question activeQuestion;
        if (!uncompletedQuestions.isEmpty()) {
            activeQuestion = uncompletedQuestions.get(random.nextInt(uncompletedQuestions.size()));
        } else {
            // If all are completed, just pick a random one to replay
            activeQuestion = levelQuestions.get(random.nextInt(levelQuestions.size()));
        }

        session.setAttribute("activeQuestionId", activeQuestion.getId());
        session.setAttribute("targetWord", activeQuestion.getWord());
        session.setAttribute("hint", activeQuestion.getHint());
        session.setAttribute("guessedLetters", new HashSet<Character>());
        session.setAttribute("wrongGuesses", 0);
        session.setAttribute("message", "");
        session.setAttribute("currentLevel", level);
        session.setAttribute("isLevelCompleted", activeQuestion.isCompleted());
        
        return "redirect:/game";
    }

    @GetMapping("/game")
    public String game(HttpSession session, Model model, @AuthenticationPrincipal UserDetails userDetails) {
        String targetWord = (String) session.getAttribute("targetWord");
        if (targetWord == null) {
            // Auto-start Level 1 if session is empty but user is logged in
            return "redirect:/"; 
        }

        @SuppressWarnings("unchecked")
        Set<Character> guessedLetters = (Set<Character>) session.getAttribute("guessedLetters");
        int wrongGuesses = (int) session.getAttribute("wrongGuesses");
        String hint = (String) session.getAttribute("hint");
        Integer currentLevel = (Integer) session.getAttribute("currentLevel");
        Boolean isLevelCompleted = (Boolean) session.getAttribute("isLevelCompleted");

        model.addAttribute("displayWord", gameService.getDisplayWord(targetWord, guessedLetters));
        model.addAttribute("hint", hint);
        model.addAttribute("wrongGuesses", wrongGuesses);
        model.addAttribute("maxChances", 6);
        model.addAttribute("message", session.getAttribute("message"));
        model.addAttribute("currentLevel", currentLevel != null ? currentLevel : 1);
        model.addAttribute("isAlreadyCompleted", isLevelCompleted);
        model.addAttribute("username", userDetails.getUsername());
        model.addAttribute("guessedLetters", guessedLetters);
        model.addAttribute("alphabet", "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray());

        if (gameService.isGameWon(targetWord, guessedLetters)) {
            User user = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
            if (user == null) return "redirect:/login";

            Long qId = (Long) session.getAttribute("activeQuestionId");
            questionRepository.findById(qId).ifPresent(q -> {
                q.setCompleted(true);
                questionRepository.save(q);
            });

            // Re-fetch questions to check progress
            List<Question> levelQuestions = questionRepository.findByUserAndLevel(user, currentLevel);
            long completedCount = levelQuestions.stream().filter(Question::isCompleted).count();
            boolean levelCleared = completedCount >= 5;

            if (levelCleared && user.getCurrentLevel() == currentLevel) {
                user.setCurrentLevel(currentLevel + 1);
                userRepository.save(user);
            }

            model.addAttribute("status", "win");
            model.addAttribute("displayWord", targetWord);
            model.addAttribute("levelCleared", levelCleared);
            model.addAttribute("isMasterLevel", currentLevel != null && currentLevel >= 50 && levelCleared);
            model.addAttribute("currentLevel", currentLevel);
            return "result";
        }

        if (wrongGuesses >= 6) {
            model.addAttribute("status", "lose");
            model.addAttribute("targetWord", targetWord);
            return "result";
        }

        return "game";
    }

    @PostMapping("/guess")
    public String guess(@RequestParam("letter") String letterStr, HttpSession session) {
        String targetWord = (String) session.getAttribute("targetWord");
        if (targetWord == null || letterStr == null || letterStr.isEmpty()) {
            return "redirect:/game";
        }

        char letter = letterStr.toUpperCase().charAt(0);
        @SuppressWarnings("unchecked")
        Set<Character> guessedLetters = (Set<Character>) session.getAttribute("guessedLetters");
        int wrongGuesses = (int) session.getAttribute("wrongGuesses");

        if (guessedLetters.contains(letter)) {
            // No message needed per user request, just redirect
        } else if (!Character.isLetter(letter)) {
             session.setAttribute("message", "Please enter a valid letter.");
        } else {
            guessedLetters.add(letter);
            if (targetWord.indexOf(letter) == -1) {
                wrongGuesses++;
                session.setAttribute("wrongGuesses", wrongGuesses);
                session.setAttribute("message", "Incorrect!");
            } else {
                session.setAttribute("message", "Correct!");
            }
        }

        return "redirect:/game";
    }
}
