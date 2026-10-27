package com.emergencykit.service;

import com.emergencykit.dao.RecordDAO;
import com.emergencykit.exception.InvalidDataException;
import com.emergencykit.exception.RecordNotFoundException;
import com.emergencykit.exception.UnauthorizedException;
import com.emergencykit.model.*;
import com.emergencykit.util.CsvImporter;
import com.emergencykit.util.DateUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// Business logic layer for emergency records.
// Sits between the menu (Main) and the database (RecordDAO).
// This is where validation, admin checks, and complex logic live.
public class RecordService {

    // Nested class to report the result of a CSV import
    public static class ImportResult {
        public int imported;
        public int skipped;

        public ImportResult(int imported, int skipped) {
            this.imported = imported;
            this.skipped = skipped;
        }
    }

    private RecordDAO dao;
    private AuthService authService;

    public RecordService(RecordDAO dao, AuthService authService) {
        this.dao = dao;
        this.authService = authService;
    }

    // --- Read methods (available to all logged-in users) ---

    public List<EmergencyRecord> getAll() throws SQLException, IOException {
        return dao.findAll();
    }

    public List<EmergencyRecord> getByType(RecordType type) throws SQLException, IOException {
        return dao.findByType(type);
    }

    // Search all records by keyword (method overloading: two versions of search)
    public List<EmergencyRecord> search(String keyword)
            throws SQLException, IOException, InvalidDataException {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new InvalidDataException("Please enter a search keyword.");
        }
        return dao.search(keyword.trim());
    }

    // Same search but filtered by record type (overloaded version)
    public List<EmergencyRecord> search(String keyword, RecordType type)
            throws SQLException, IOException, InvalidDataException {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new InvalidDataException("Please enter a search keyword.");
        }
        return dao.search(keyword.trim(), type);
    }

    // Returns all records that are stale, sorted oldest-first
    public List<EmergencyRecord> getStaleRecords() throws SQLException, IOException {
        List<EmergencyRecord> all = dao.findAll();
        // Use a lambda (Predicate) to filter only stale records
        // Then sort by lastVerified ascending (oldest = most stale = first in list)
        return all.stream()
                  .filter(r -> r.isStale())
                  .sorted(Comparator.comparing(EmergencyRecord::getLastVerified))
                  .collect(Collectors.toList());
    }

    // Groups all records by type using an EnumMap.
    // EnumMap ensures categories always appear in enum declaration order:
    // CONTACT, ASSEMBLY, CHECKLIST, INSTRUCTION -- never random order.
    public Map<RecordType, List<EmergencyRecord>> groupByType() throws SQLException, IOException {
        List<EmergencyRecord> all = dao.findAll();
        return all.stream().collect(
            Collectors.groupingBy(
                EmergencyRecord::getType,
                () -> new EnumMap<>(RecordType.class), // factory ensures order
                Collectors.toList()
            )
        );
    }

    // Generic helper: filters any list using a given rule (Predicate).
    // This demonstrates the use of generics + lambdas.
    public static <T> List<T> filter(List<T> items, Predicate<T> rule) {
        List<T> result = new ArrayList<>();
        for (T item : items) {
            if (rule.test(item)) {
                result.add(item);
            }
        }
        return result;
    }

    // Looks up a record by ID; converts null (not found) to an exception
    public EmergencyRecord getById(int id)
            throws RecordNotFoundException, SQLException, IOException {
        EmergencyRecord record = dao.findById(id);
        if (record == null) {
            throw new RecordNotFoundException("No record with ID " + id);
        }
        return record;
    }

    // Like getById but specifically for checklists.
    // Throws InvalidDataException if the record exists but isn't a Checklist.
    // This avoids a ClassCastException.
    public Checklist getChecklist(int id)
            throws RecordNotFoundException, InvalidDataException, SQLException, IOException {
        EmergencyRecord record = getById(id);
        // Check the actual type BEFORE casting (never cast blindly!)
        if (!(record instanceof Checklist)) {
            throw new InvalidDataException("Record " + id + " is not a checklist.");
        }
        return (Checklist) record;
    }

    // --- Admin-only methods (each starts with a permission check) ---

    public int addRecord(EmergencyRecord r)
            throws UnauthorizedException, InvalidDataException, SQLException, IOException {
        authService.requireAdmin();
        validate(r);
        return dao.add(r);
    }

    public void updateRecord(EmergencyRecord r)
            throws UnauthorizedException, RecordNotFoundException, InvalidDataException, SQLException, IOException {
        authService.requireAdmin();
        validate(r);
        boolean updated = dao.update(r);
        if (!updated) {
            // The DAO returned false meaning no row matched that ID
            throw new RecordNotFoundException("No record with ID " + r.getId());
        }
        // Note: updating does NOT change last_verified.
        // The admin is asked separately (in the menu) whether to mark it re-verified.
    }

    public void deleteRecord(int id)
            throws UnauthorizedException, RecordNotFoundException, SQLException, IOException {
        authService.requireAdmin();
        boolean deleted = dao.delete(id);
        if (!deleted) {
            throw new RecordNotFoundException("No record with ID " + id);
        }
        // Never say "deleted" when nothing was actually deleted
    }

    public void markVerified(int id)
            throws UnauthorizedException, RecordNotFoundException, SQLException, IOException {
        authService.requireAdmin();
        boolean done = dao.markVerified(id, DateUtil.today());
        if (!done) {
            throw new RecordNotFoundException("No record with ID " + id);
        }
    }

    // Imports contacts from a CSV file.
    // Parses all rows first, validates all, THEN inserts (all-or-nothing).
    // Skips duplicates (same title + phone).
    public ImportResult importContacts(String csvPath)
            throws UnauthorizedException, InvalidDataException, SQLException, IOException {
        authService.requireAdmin();

        // Step 1: Parse all rows (nothing written to DB yet)
        List<CsvImporter.ParsedRow> rows = CsvImporter.readContacts(csvPath);

        // Step 2: Validate each row; any invalid row aborts the whole import
        for (CsvImporter.ParsedRow row : rows) {
            try {
                validate(row.contact);
            } catch (InvalidDataException e) {
                // Re-throw with the line number prepended
                throw new InvalidDataException("Line " + row.lineNumber + ": " + e.getMessage());
            }
        }

        // Step 3: Skip duplicates (already in DB or repeated in the same file)
        List<EmergencyRecord> toInsert = new ArrayList<>();
        int skipped = 0;

        // Track titles+phones we've already seen in this import batch
        Set<String> seenInBatch = new HashSet<>();

        for (CsvImporter.ParsedRow row : rows) {
            String key = row.contact.getTitle() + "|" + row.contact.getPhone();

            // Check both: already in DB, and already seen in this file
            if (dao.contactExists(row.contact.getTitle(), row.contact.getPhone())
                    || seenInBatch.contains(key)) {
                skipped++;
            } else {
                seenInBatch.add(key);
                toInsert.add(row.contact);
            }
        }

        // Step 4: Insert remaining rows in one transaction
        if (!toInsert.isEmpty()) {
            dao.addAll(toInsert);
        }

        return new ImportResult(toInsert.size(), skipped);
    }

    // --- Validation ---

    // Checks that a record has all required fields and they meet length/format rules.
    // Throws InvalidDataException with a specific message for each problem.
    private void validate(EmergencyRecord r) throws InvalidDataException {
        // Title is required and max 150 chars
        if (r.getTitle() == null || r.getTitle().trim().isEmpty()) {
            throw new InvalidDataException("Title cannot be blank.");
        }
        if (r.getTitle().length() > 150) {
            throw new InvalidDataException("Title is too long (max 150 characters).");
        }

        // Source (responsible authority) is required and max 150 chars
        if (r.getSource() == null || r.getSource().trim().isEmpty()) {
            throw new InvalidDataException("Source/authority cannot be blank.");
        }
        if (r.getSource().length() > 150) {
            throw new InvalidDataException("Source is too long (max 150 characters).");
        }

        // Description max 5000 chars; instructions MUST have a description
        if (r.getDescription() != null && r.getDescription().length() > 5000) {
            throw new InvalidDataException("Description is too long (max 5000 characters).");
        }
        if (r instanceof Instruction) {
            if (r.getDescription() == null || r.getDescription().trim().isEmpty()) {
                throw new InvalidDataException("Instruction steps cannot be blank.");
            }
        }

        // Last verified date is required and cannot be in the future
        if (r.getLastVerified() == null) {
            throw new InvalidDataException("Last verified date is required.");
        }
        if (r.getLastVerified().isAfter(LocalDate.now())) {
            throw new InvalidDataException("Last verified date cannot be in the future.");
        }

        // Contact-specific validation
        if (r instanceof EmergencyContact) {
            EmergencyContact contact = (EmergencyContact) r;
            String phone = contact.getPhone();
            if (phone == null || !phone.matches("[0-9+\\-() ]{3,30}")) {
                throw new InvalidDataException(
                    "Phone must be 3-30 characters and contain only digits, +, -, (, ), or spaces."
                );
            }
            if (contact.getContactRole() != null && contact.getContactRole().length() > 100) {
                throw new InvalidDataException("Contact role is too long (max 100 characters).");
            }
        }

        // Assembly point must have a location description
        if (r instanceof AssemblyPoint) {
            AssemblyPoint ap = (AssemblyPoint) r;
            if (ap.getLocationDescription() == null || ap.getLocationDescription().trim().isEmpty()) {
                throw new InvalidDataException("Location description cannot be blank for assembly points.");
            }
            if (ap.getLocationDescription().length() > 5000) {
                throw new InvalidDataException("Location description is too long (max 5000 characters).");
            }
        }

        // Checklist must have at least one item, and each item max 255 chars
        if (r instanceof Checklist) {
            Checklist checklist = (Checklist) r;
            if (checklist.getItems().isEmpty()) {
                throw new InvalidDataException("A checklist must have at least one item.");
            }
            for (ChecklistItem item : checklist.getItems()) {
                if (item.getItemText() == null || item.getItemText().trim().isEmpty()) {
                    throw new InvalidDataException("Checklist items cannot be blank.");
                }
                if (item.getItemText().length() > 255) {
                    throw new InvalidDataException("Checklist item text is too long (max 255 characters).");
                }
            }
        }
    }
}
