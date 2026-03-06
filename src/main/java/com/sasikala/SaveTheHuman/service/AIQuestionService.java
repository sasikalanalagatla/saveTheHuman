package com.sasikala.SaveTheHuman.service;

import com.sasikala.SaveTheHuman.entity.Question;
import com.sasikala.SaveTheHuman.repository.QuestionRepository;
import com.sasikala.SaveTheHuman.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AIQuestionService {

    @Autowired
    private QuestionRepository questionRepository;

    @org.springframework.beans.factory.annotation.Value("${ai.api.key}")
    private String apiKey;

    @org.springframework.beans.factory.annotation.Value("${ai.api.url}")
    private String apiUrl;

    @org.springframework.beans.factory.annotation.Value("${ai.api.model}")
    private String model;

    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public void generateInitialQuestionsForUser(User user) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) {
            throw new RuntimeException("AI API Key is missing. Please configure 'ai.api.key' in application.properties.");
        }

        System.out.println("Starting synchronous generation for Level 1-5 for user: " + user.getUsername());
        // Sync: Levels 1-5 (Initial 25 questions)
        List<Question> initialBatch = fetchBatchFromAI(user, 1, 5);
        questionRepository.saveAll(initialBatch);
        System.out.println("Synchronous generation complete for user: " + user.getUsername());
    }

    @org.springframework.scheduling.annotation.Async
    public void generateRemainingQuestionsAsync(User user) {
        System.out.println("Starting background generation for Level 6-50 for user: " + user.getUsername());
        List<Question> allRemaining = new ArrayList<>();
        // Batches of 5 levels (Levels 6-50)
        for (int batch = 1; batch < 10; batch++) {
            int startLevel = (batch * 5) + 1;
            int endLevel = (batch + 1) * 5;
            try {
                allRemaining.addAll(fetchBatchFromAI(user, startLevel, endLevel));
                // Small delay to avoid hitting rate limits too hard
                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println("Background batch failed for " + startLevel + "-" + endLevel + ": " + e.getMessage());
            }
        }
        if (!allRemaining.isEmpty()) {
            questionRepository.saveAll(allRemaining);
            System.out.println("Background generation complete for user: " + user.getUsername());
        }
    }

    private List<Question> fetchBatchFromAI(User user, int start, int end) {
        String prompt = String.format(
            "Generate exactly 5 General Knowledge (non-political) questions for each level from %d to %d. " +
            "For each question, provide a 'word' (one word, uppercase) and a 'hint' (a clear descriptive sentence describing the word). " +
            "Hints must be clear clues. Return ONLY a JSON array of objects with keys: level (int), word (string), hint (string). " +
            "No preamble, no markdown formatting, just the raw JSON array.",
            start, end
        );

        // Gemini Request Format
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        
        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));
        
        Map<String, Object> request = new HashMap<>();
        request.put("contents", List.of(content));

        // Gemini uses API Key in the URL
        String fullUrl = apiUrl + apiKey;

        try {
            Map<String, Object> response = restTemplate.postForObject(fullUrl, request, Map.class);
            
            // Navigate Gemini Response: candidates[0].content.parts[0].text
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> firstCandidate = candidates.get(0);
            Map<String, Object> candidateContent = (Map<String, Object>) firstCandidate.get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) candidateContent.get("parts");
            String responseText = (String) parts.get(0).get("text");
            
            // Clean markdown blocks if present
            responseText = responseText.replaceAll("```json", "").replaceAll("```", "").trim();
            
            List<Map<String, Object>> items = objectMapper.readValue(responseText, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>(){});
            
            List<Question> questions = new ArrayList<>();
            for (Map<String, Object> item : items) {
                questions.add(new Question((String) item.get("word"), (String) item.get("hint"), (Integer) item.get("level"), user));
            }
            return questions;
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            throw new RuntimeException("Gemini API Key invalid or restricted: " + e.getResponseBodyAsString());
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            throw new RuntimeException("Gemini API Quota Exceeded (429). Please check your Google Cloud billing.");
        } catch (Exception e) {
            throw new RuntimeException("Gemini Batch Fetch Failed for levels " + start + "-" + end + ": " + e.getMessage());
        }
    }
}

