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

public class RecordService {

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

    public List<EmergencyRecord> getAll() throws SQLException, IOException {
        return dao.findAll();
    }

    public List<EmergencyRecord> getByType(RecordType type) throws SQLException, IOException {
        return dao.findByType(type);
    }

    public List<EmergencyRecord> search(String keyword)
            throws SQLException, IOException, InvalidDataException {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new InvalidDataException("Please enter a search keyword.");
        }
        return dao.search(keyword.trim());
    }

    public List<EmergencyRecord> search(String keyword, RecordType type)
            throws SQLException, IOException, InvalidDataException {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new InvalidDataException("Please enter a search keyword.");
        }
        return dao.search(keyword.trim(), type);
    }

    public List<EmergencyRecord> getStaleRecords() throws SQLException, IOException {
        List<EmergencyRecord> all = dao.findAll();
        return all.stream()
                  .filter(r -> r.isStale())
                  .sorted(Comparator.comparing(EmergencyRecord::getLastVerified))
                  .collect(Collectors.toList());
    }

    public Map<RecordType, List<EmergencyRecord>> groupByType() throws SQLException, IOException {
        List<EmergencyRecord> all = dao.findAll();
        return all.stream().collect(
            Collectors.groupingBy(
                EmergencyRecord::getType,
                () -> new EnumMap<>(RecordType.class),
                Collectors.toList()
            )
        );
    }

    public static <T> List<T> filter(List<T> items, Predicate<T> rule) {
        List<T> result = new ArrayList<>();
        for (T item : items) {
            if (rule.test(item)) {
                result.add(item);
            }
        }
        return result;
    }

    public EmergencyRecord getById(int id)
            throws RecordNotFoundException, SQLException, IOException {
        EmergencyRecord record = dao.findById(id);
        if (record == null) {
            throw new RecordNotFoundException("No record with ID " + id);
        }
        return record;
    }

    public Checklist getChecklist(int id)
            throws RecordNotFoundException, InvalidDataException, SQLException, IOException {
        EmergencyRecord record = getById(id);
        if (!(record instanceof Checklist)) {
            throw new InvalidDataException("Record " + id + " is not a checklist.");
        }
        return (Checklist) record;
    }

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
            throw new RecordNotFoundException("No record with ID " + r.getId());
        }
    }

    public void deleteRecord(int id)
            throws UnauthorizedException, RecordNotFoundException, SQLException, IOException {
        authService.requireAdmin();
        boolean deleted = dao.delete(id);
        if (!deleted) {
            throw new RecordNotFoundException("No record with ID " + id);
        }
    }

    public void markVerified(int id)
            throws UnauthorizedException, RecordNotFoundException, SQLException, IOException {
        authService.requireAdmin();
        boolean done = dao.markVerified(id, DateUtil.today());
        if (!done) {
            throw new RecordNotFoundException("No record with ID " + id);
        }
    }

    public ImportResult importContacts(String csvPath)
            throws UnauthorizedException, InvalidDataException, SQLException, IOException {
        authService.requireAdmin();

        List<CsvImporter.ParsedRow> rows = CsvImporter.readContacts(csvPath);

        for (CsvImporter.ParsedRow row : rows) {
            try {
                validate(row.contact);
            } catch (InvalidDataException e) {
                throw new InvalidDataException("Line " + row.lineNumber + ": " + e.getMessage());
            }
        }

        List<EmergencyRecord> toInsert = new ArrayList<>();
        int skipped = 0;
        Set<String> seenInBatch = new HashSet<>();

        for (CsvImporter.ParsedRow row : rows) {
            String key = row.contact.getTitle() + "|" + row.contact.getPhone();

            if (dao.contactExists(row.contact.getTitle(), row.contact.getPhone())
                    || seenInBatch.contains(key)) {
                skipped++;
            } else {
                seenInBatch.add(key);
                toInsert.add(row.contact);
            }
        }

        if (!toInsert.isEmpty()) {
            dao.addAll(toInsert);
        }

        return new ImportResult(toInsert.size(), skipped);
    }

    private void validate(EmergencyRecord r) throws InvalidDataException {
        if (r.getTitle() == null || r.getTitle().trim().isEmpty()) {
            throw new InvalidDataException("Title cannot be blank.");
        }
        if (r.getTitle().length() > 150) {
            throw new InvalidDataException("Title is too long (max 150 characters).");
        }

        if (r.getSource() == null || r.getSource().trim().isEmpty()) {
            throw new InvalidDataException("Source/authority cannot be blank.");
        }
        if (r.getSource().length() > 150) {
            throw new InvalidDataException("Source is too long (max 150 characters).");
        }

        if (r.getDescription() != null && r.getDescription().length() > 5000) {
            throw new InvalidDataException("Description is too long (max 5000 characters).");
        }
        if (r instanceof Instruction) {
            if (r.getDescription() == null || r.getDescription().trim().isEmpty()) {
                throw new InvalidDataException("Instruction steps cannot be blank.");
            }
        }

        if (r.getLastVerified() == null) {
            throw new InvalidDataException("Last verified date is required.");
        }
        if (r.getLastVerified().isAfter(LocalDate.now())) {
            throw new InvalidDataException("Last verified date cannot be in the future.");
        }

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

        if (r instanceof AssemblyPoint) {
            AssemblyPoint ap = (AssemblyPoint) r;
            if (ap.getLocationDescription() == null || ap.getLocationDescription().trim().isEmpty()) {
                throw new InvalidDataException("Location description cannot be blank for assembly points.");
            }
            if (ap.getLocationDescription().length() > 5000) {
                throw new InvalidDataException("Location description is too long (max 5000 characters).");
            }
        }

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
