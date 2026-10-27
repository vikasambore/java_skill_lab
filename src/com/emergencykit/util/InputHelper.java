package com.emergencykit.util;

import com.emergencykit.exception.InvalidDataException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// Wraps standard console input with validation and error-handling prompts.
// Used throughout Main for safely reading user strings, numbers, and dates.
public class InputHelper {

    private Scanner scanner;

    public InputHelper() {
        this.scanner = new Scanner(System.in);
    }

    public String readLine(String prompt) {
        System.out.print(prompt);
        String line = scanner.nextLine().trim();
        if (line.startsWith("\"") && line.endsWith("\"") && line.length() >= 2) {
            line = line.substring(1, line.length() - 1).trim();
        }
        return line;
    }

    public String readRequired(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("This field cannot be blank. Please try again.");
        }
    }

    public String readOptional(String prompt) {
        return readLine(prompt);
    }

    public int readInt(String prompt, int min, int max) {
        while (true) {
            String line = readLine(prompt);
            try {
                int value = Integer.parseInt(line.trim());
                if (value >= min && value <= max) {
                    return value;
                }
                System.out.println("Please enter a number between " + min + " and " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number between " + min + " and " + max + ".");
            }
        }
    }

    public boolean readYesNo(String prompt) {
        while (true) {
            String answer = readLine(prompt).toLowerCase();
            if (answer.equals("y") || answer.equals("yes")) return true;
            if (answer.equals("n") || answer.equals("no")) return false;
            System.out.println("Please type y or n.");
        }
    }

    public LocalDate readDate(String prompt, LocalDate defaultValue) {
        while (true) {
            String input = readLine(prompt);
            if (input.isEmpty()) {
                return defaultValue;
            }
            try {
                return DateUtil.parse(input);
            } catch (InvalidDataException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public List<String> readMultiLine(String prompt) {
        System.out.println(prompt);
        List<String> lines = new ArrayList<>();
        while (true) {
            String line = readLine("  > ");
            if (line.isEmpty()) {
                break;
            }
            lines.add(line);
        }
        return lines;
    }
}
