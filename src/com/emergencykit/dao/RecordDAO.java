package com.emergencykit.dao;

import com.emergencykit.model.EmergencyRecord;
import com.emergencykit.model.RecordType;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

// Interface defining all database operations for emergency records.
// RecordDAOImpl implements this interface using real MySQL queries.
public interface RecordDAO {

    // Add a single new record; returns the new auto-generated ID
    int add(EmergencyRecord r) throws SQLException, IOException;

    // Add many records in one transaction (all or nothing)
    int[] addAll(List<EmergencyRecord> records) throws SQLException, IOException;

    // Update an existing record; returns false if the ID didn't exist
    boolean update(EmergencyRecord r) throws SQLException, IOException;

    // Delete a record by its ID; returns false if the ID didn't exist
    boolean delete(int id) throws SQLException, IOException;

    // Find one record by ID; returns null if not found
    EmergencyRecord findById(int id) throws SQLException, IOException;

    // Get all records from the database
    List<EmergencyRecord> findAll() throws SQLException, IOException;

    // Get all records of a specific type (CONTACT, ASSEMBLY, etc.)
    List<EmergencyRecord> findByType(RecordType type) throws SQLException, IOException;

    // Search by keyword across title, description, source, etc.
    List<EmergencyRecord> search(String keyword) throws SQLException, IOException;

    // Search by keyword AND filter by type
    List<EmergencyRecord> search(String keyword, RecordType type) throws SQLException, IOException;

    // Update only the last_verified date for one record
    boolean markVerified(int id, LocalDate date) throws SQLException, IOException;

    // Check if a contact with the same title and phone already exists (for duplicate prevention)
    boolean contactExists(String title, String phone) throws SQLException, IOException;
}
