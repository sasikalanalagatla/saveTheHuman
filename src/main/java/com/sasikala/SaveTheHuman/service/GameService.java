package com.sasikala.SaveTheHuman.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class GameService {

    public String getDisplayWord(String targetWord, Set<Character> guessedLetters) {
        StringBuilder display = new StringBuilder();
        for (char c : targetWord.toCharArray()) {
            if (guessedLetters.contains(c)) {
                display.append(c).append(" ");
            } else if (c == ' ') {
                display.append("  ");
            } else {
                display.append("_ ");
            }
        }
        return display.toString().trim();
    }

    public boolean isGameWon(String targetWord, Set<Character> guessedLetters) {
        for (char c : targetWord.toCharArray()) {
            if (Character.isLetter(c) && !guessedLetters.contains(c)) {
                return false;
            }
        }
        return true;
    }
}
