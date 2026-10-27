package com.emergencykit.util;

import com.emergencykit.exception.InvalidDataException;
import com.emergencykit.model.EmergencyContact;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CsvImporter {

    public static class ParsedRow {
        public int lineNumber;
        public EmergencyContact contact;

        public ParsedRow(int lineNumber, EmergencyContact contact) {
            this.lineNumber = lineNumber;
            this.contact = contact;
        }
    }

    public static List<ParsedRow> readContacts(String filePath) throws IOException, InvalidDataException {
        List<ParsedRow> result = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",", 6);

                if (parts.length < 5) {
                    throw new InvalidDataException("Line " + lineNumber + ": expected 6 columns, got " + parts.length);
                }

                String title = parts[0].trim();
                String phone = parts[1].trim();
                String contactRole = parts[2].trim();
                String source = parts[3].trim();
                String lastVerifiedStr = parts[4].trim();
                String description = (parts.length >= 6) ? parts[5].trim() : "";

                LocalDate lastVerified;
                if (lastVerifiedStr.isEmpty()) {
                    lastVerified = DateUtil.today();
                } else {
                    try {
                        lastVerified = DateUtil.parse(lastVerifiedStr);
                    } catch (InvalidDataException e) {
                        throw new InvalidDataException("Line " + lineNumber + ": " + e.getMessage());
                    }
                }

                EmergencyContact contact = new EmergencyContact();
                contact.setTitle(title);
                contact.setPhone(phone);
                contact.setContactRole(contactRole);
                contact.setSource(source);
                contact.setLastVerified(lastVerified);
                contact.setDescription(description);

                result.add(new ParsedRow(lineNumber, contact));
            }
        }

        return result;
    }
}
