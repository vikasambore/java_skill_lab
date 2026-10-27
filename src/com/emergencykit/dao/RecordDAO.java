package com.emergencykit.dao;

import com.emergencykit.model.EmergencyRecord;
import com.emergencykit.model.RecordType;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public interface RecordDAO {

    int add(EmergencyRecord r) throws SQLException, IOException;

    int[] addAll(List<EmergencyRecord> records) throws SQLException, IOException;

    boolean update(EmergencyRecord r) throws SQLException, IOException;

    boolean delete(int id) throws SQLException, IOException;

    EmergencyRecord findById(int id) throws SQLException, IOException;

    List<EmergencyRecord> findAll() throws SQLException, IOException;

    List<EmergencyRecord> findByType(RecordType type) throws SQLException, IOException;

    List<EmergencyRecord> search(String keyword) throws SQLException, IOException;

    List<EmergencyRecord> search(String keyword, RecordType type) throws SQLException, IOException;

    boolean markVerified(int id, LocalDate date) throws SQLException, IOException;

    boolean contactExists(String title, String phone) throws SQLException, IOException;
}
