package com.emergencykit.util;

import com.emergencykit.exception.InvalidDataException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateUtil {

    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public static String format(LocalDate date) {
        if (date == null) return "Unknown";
        return date.format(DISPLAY_FORMAT);
    }

    public static LocalDate parse(String dateStr) throws InvalidDataException {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new InvalidDataException("Date cannot be blank. Use yyyy-MM-dd, e.g. 2026-10-27");
        }
        try {
            return LocalDate.parse(dateStr.trim(), INPUT_FORMAT);
        } catch (DateTimeParseException e) {
            throw new InvalidDataException(
                "Invalid date '" + dateStr.trim() + "'. Use yyyy-MM-dd, e.g. 2026-10-27"
            );
        }
    }

    public static LocalDate today() {
        return LocalDate.now();
    }
}
