package com.emergencykit.util;

import com.emergencykit.exception.InvalidDataException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// Utility class for date operations.
// All methods are static so we can call them without creating an object.
public class DateUtil {

    // The date format we accept and display: e.g. "2026-10-27"
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // The display format: e.g. "27 Oct 2026"
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // Formats a LocalDate for display (e.g. "27 Oct 2026")
    public static String format(LocalDate date) {
        if (date == null) return "Unknown";
        return date.format(DISPLAY_FORMAT);
    }

    // Parses a date string in "yyyy-MM-dd" format.
    // Throws InvalidDataException if the format is wrong or the date doesn't exist
    // (e.g. 2026-13-45 would be caught here and rethrown as a friendly error).
    public static LocalDate parse(String dateStr) throws InvalidDataException {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new InvalidDataException("Date cannot be blank. Use yyyy-MM-dd, e.g. 2026-10-27");
        }
        try {
            // Using strict mode to catch invalid dates like month 13
            return LocalDate.parse(dateStr.trim(), INPUT_FORMAT);
        } catch (DateTimeParseException e) {
            // Rethrow with a friendly message instead of a technical error
            throw new InvalidDataException(
                "Invalid date '" + dateStr.trim() + "'. Use yyyy-MM-dd, e.g. 2026-10-27"
            );
        }
    }

    // Returns today's date
    public static LocalDate today() {
        return LocalDate.now();
    }
}
