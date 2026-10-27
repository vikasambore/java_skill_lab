package com.emergencykit.util;

import com.emergencykit.exception.InvalidDataException;
import com.emergencykit.model.EmergencyContact;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Reads emergency contacts from a CSV file.
// The CSV format is: title,phone,contact_role,source,last_verified,description
public class CsvImporter {

    // Holds one parsed CSV row and its original line number (for error reporting)
    public static class ParsedRow {
        public int lineNumber;
        public EmergencyContact contact;

        public ParsedRow(int lineNumber, EmergencyContact contact) {
            this.lineNumber = lineNumber;
            this.contact = contact;
        }
    }

    // Reads and parses the CSV file. Does NOT write to the database.
    // Returns a list of parsed contacts ready for the service to validate and insert.
    public static List<ParsedRow> readContacts(String filePath) throws IOException, InvalidDataException {
        List<ParsedRow> result = new ArrayList<>();

        // try-with-resources closes the reader automatically
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip the header row
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                // Skip blank lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Split into at most 6 parts so commas inside the description stay together.
                // For example: "Title,100,Role,Source,2026-01-01,Call first, then email"
                // With limit 6: ["Title", "100", "Role", "Source", "2026-01-01", "Call first, then email"]
                String[] parts = line.split(",", 6);

                // We need at least 5 columns (description is optional)
                if (parts.length < 5) {
                    throw new InvalidDataException("Line " + lineNumber + ": expected 6 columns, got " + parts.length);
                }

                // Trim whitespace from each field
                String title = parts[0].trim();
                String phone = parts[1].trim();
                String contactRole = parts[2].trim();
                String source = parts[3].trim();
                String lastVerifiedStr = parts[4].trim();
                // Description is the 6th column (index 5), or empty if missing
                String description = (parts.length >= 6) ? parts[5].trim() : "";

                // Parse the date: blank means today
                LocalDate lastVerified;
                if (lastVerifiedStr.isEmpty()) {
                    lastVerified = DateUtil.today();
                } else {
                    try {
                        lastVerified = DateUtil.parse(lastVerifiedStr);
                    } catch (InvalidDataException e) {
                        // Include the line number in the error message
                        throw new InvalidDataException("Line " + lineNumber + ": " + e.getMessage());
                    }
                }

                // Build the contact object
                EmergencyContact contact = new EmergencyContact();
                contact.setTitle(title);
                contact.setPhone(phone);
                contact.setContactRole(contactRole);
                contact.setSource(source);
                contact.setLastVerified(lastVerified);
                contact.setDescription(description);
                // Type is set automatically by EmergencyContact constructor (CONTACT)

                result.add(new ParsedRow(lineNumber, contact));
            }
        }

        return result;
    }
}
