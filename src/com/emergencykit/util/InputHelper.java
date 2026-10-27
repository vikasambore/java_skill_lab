package com.emergencykit.util;

import com.emergencykit.exception.InvalidDataException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// All console input goes through this class.
// There is ONE Scanner for the whole program -- never create another one.
// NEVER use nextInt() or nextDouble() -- they leave a newline in the buffer
// which breaks the next read. Always use nextLine() and parse manually.
public class InputHelper {

    // The shared Scanner -- created ONCE and NEVER closed until the program exits
    private Scanner scanner;

    public InputHelper() {
        // One Scanner for the whole program
        this.scanner = new Scanner(System.in);
    }

    // Reads a line, trims it, and strips surrounding double quotes.
    // Stripping quotes lets Windows users paste "Copy as path" output directly.
    // e.g. "C:\Users\Vikas\contacts.csv" becomes C:\Users\Vikas\contacts.csv
    public String readLine(String prompt) {
        System.out.print(prompt);
        String line = scanner.nextLine().trim();
        // Strip surrounding double quotes if present
        if (line.startsWith("\"") && line.endsWith("\"") && line.length() >= 2) {
            line = line.substring(1, line.length() - 1).trim();
        }
        return line;
    }

    // Reads a line but keeps asking until the user enters something non-blank
    public String readRequired(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("This field cannot be blank. Please try again.");
        }
    }

    // Reads a line; returns "" if the user just presses Enter.
    // Used for "press Enter to keep the old value" in update screens.
    public String readOptional(String prompt) {
        return readLine(prompt);
    }

    // Reads an integer in a given range.
    // Keeps re-prompting if the user types something non-numeric or out of range.
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

    // Reads a yes/no answer. Accepts y, yes, n, no (case-insensitive).
    // Keeps asking if the user types anything else.
    public boolean readYesNo(String prompt) {
        while (true) {
            String answer = readLine(prompt).toLowerCase();
            if (answer.equals("y") || answer.equals("yes")) return true;
            if (answer.equals("n") || answer.equals("no")) return false;
            System.out.println("Please type y or n.");
        }
    }

    // Reads a date. Pressing Enter returns the defaultValue (usually today).
    // If the user types a date, we parse it with DateUtil.
    // Re-prompts if the date is invalid (wrong format, doesn't exist, etc.)
    public LocalDate readDate(String prompt, LocalDate defaultValue) {
        while (true) {
            String input = readLine(prompt);
            if (input.isEmpty()) {
                return defaultValue; // Enter = use default
            }
            try {
                return DateUtil.parse(input);
            } catch (InvalidDataException e) {
                System.out.println(e.getMessage()); // show the friendly error message
                // loop and ask again
            }
        }
    }

    // Reads multiple lines until the user enters a blank line.
    // Used for entering checklist items or instruction steps.
    public List<String> readMultiLine(String prompt) {
        System.out.println(prompt);
        List<String> lines = new ArrayList<>();
        while (true) {
            String line = readLine("  > ");
            if (line.isEmpty()) {
                break; // blank line = done
            }
            lines.add(line);
        }
        return lines;
    }
}
