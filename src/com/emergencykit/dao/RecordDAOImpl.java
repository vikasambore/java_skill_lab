package com.emergencykit.dao;

import com.emergencykit.model.*;
import com.emergencykit.util.DBConnection;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// This class does the actual database work for emergency records.
// It implements RecordDAO using PreparedStatements (never string concatenation for SQL).
public class RecordDAOImpl implements RecordDAO {

    // Builds the correct subclass object from a ResultSet row.
    // This is polymorphism: we check the 'type' column and create the matching class.
    private EmergencyRecord mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String typeStr = rs.getString("type");
        String title = rs.getString("title");
        String description = rs.getString("description");
        String source = rs.getString("source");

        // Read the date using getObject to avoid timezone issues with java.sql.Date
        LocalDate lastVerified = rs.getObject("last_verified", LocalDate.class);

        RecordType type = RecordType.valueOf(typeStr);

        // Create the correct subclass based on the record type
        switch (type) {
            case CONTACT:
                String phone = rs.getString("phone");
                String contactRole = rs.getString("contact_role");
                return new EmergencyContact(id, title, description, phone, contactRole, source, lastVerified);

            case ASSEMBLY:
                String locationDesc = rs.getString("location_description");
                return new AssemblyPoint(id, title, description, locationDesc, source, lastVerified);

            case CHECKLIST:
                // Items are loaded separately after this method returns
                return new Checklist(id, title, description, source, lastVerified);

            case INSTRUCTION:
                return new Instruction(id, title, description, source, lastVerified);

            default:
                // Should never happen, but safe to handle
                throw new SQLException("Unknown record type: " + typeStr);
        }
    }

    // Loads checklist items for a given checklist record from the database.
    private void loadChecklistItems(Checklist checklist, Connection conn) throws SQLException {
        String sql = "SELECT id, item_text, item_order FROM checklist_items " +
                     "WHERE record_id = ? ORDER BY item_order";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, checklist.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int itemId = rs.getInt("id");
                    String itemText = rs.getString("item_text");
                    int itemOrder = rs.getInt("item_order");
                    checklist.getItems().add(new ChecklistItem(itemId, itemText, itemOrder));
                }
            }
        }
    }

    @Override
    public int add(EmergencyRecord r) throws SQLException, IOException {
        String sql = "INSERT INTO records (type, title, description, phone, contact_role, " +
                     "location_description, source, last_verified) VALUES (?,?,?,?,?,?,?,?)";

        Connection conn = DBConnection.getConnection();
        // Use try-with-resources to ensure connection is always closed
        try (conn) {
            if (r instanceof Checklist) {
                // Checklist needs a transaction to insert both the record AND its items
                conn.setAutoCommit(false);
                try {
                    int newId = insertRecord(r, conn, sql);
                    insertChecklistItems((Checklist) r, newId, conn);
                    conn.commit();
                    return newId;
                } catch (SQLException e) {
                    conn.rollback(); // undo everything if anything fails
                    throw e;
                } finally {
                    conn.setAutoCommit(true); // always restore auto-commit
                }
            } else {
                return insertRecord(r, conn, sql);
            }
        }
    }

    // Helper: inserts one record row and returns the generated ID
    private int insertRecord(EmergencyRecord r, Connection conn, String sql) throws SQLException {
        // RETURN_GENERATED_KEYS lets us get the auto-incremented ID back
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getType().name());
            ps.setString(2, r.getTitle());
            ps.setString(3, r.getDescription());

            // Type-specific fields (null for types that don't use them)
            if (r instanceof EmergencyContact) {
                EmergencyContact c = (EmergencyContact) r;
                ps.setString(4, c.getPhone());
                ps.setString(5, c.getContactRole());
            } else {
                ps.setNull(4, Types.VARCHAR);
                ps.setNull(5, Types.VARCHAR);
            }

            if (r instanceof AssemblyPoint) {
                AssemblyPoint a = (AssemblyPoint) r;
                ps.setString(6, a.getLocationDescription());
            } else {
                ps.setNull(6, Types.VARCHAR);
            }

            ps.setString(7, r.getSource());
            // setObject with LocalDate avoids timezone problems (no java.sql.Date)
            ps.setObject(8, r.getLastVerified());

            ps.executeUpdate();

            // Get the newly generated ID
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to get generated key after insert.");
    }

    // Helper: inserts all checklist items for a given checklist record
    private void insertChecklistItems(Checklist checklist, int recordId, Connection conn) throws SQLException {
        String sql = "INSERT INTO checklist_items (record_id, item_text, item_order) VALUES (?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ChecklistItem item : checklist.getItems()) {
                ps.setInt(1, recordId);
                ps.setString(2, item.getItemText());
                ps.setInt(3, item.getItemOrder());
                ps.addBatch(); // batch all items for efficiency
            }
            ps.executeBatch();
        }
    }

    @Override
    public int[] addAll(List<EmergencyRecord> records) throws SQLException, IOException {
        // Insert all records in one transaction: all succeed or all fail
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String sql = "INSERT INTO records (type, title, description, phone, contact_role, " +
                             "location_description, source, last_verified) VALUES (?,?,?,?,?,?,?,?)";
                int[] ids = new int[records.size()];
                for (int i = 0; i < records.size(); i++) {
                    EmergencyRecord r = records.get(i);
                    int newId = insertRecord(r, conn, sql);
                    ids[i] = newId;
                    if (r instanceof Checklist) {
                        insertChecklistItems((Checklist) r, newId, conn);
                    }
                }
                conn.commit();
                return ids;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean update(EmergencyRecord r) throws SQLException, IOException {
        String sql = "UPDATE records SET title=?, description=?, phone=?, contact_role=?, " +
                     "location_description=?, source=?, last_verified=? WHERE id=?";

        try (Connection conn = DBConnection.getConnection()) {
            if (r instanceof Checklist) {
                // Updating a checklist also replaces all its items
                conn.setAutoCommit(false);
                try {
                    boolean updated = updateRecord(r, conn, sql);
                    if (updated) {
                        // Delete old items then insert current ones
                        deleteChecklistItems(r.getId(), conn);
                        insertChecklistItems((Checklist) r, r.getId(), conn);
                    }
                    conn.commit();
                    return updated;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } else {
                return updateRecord(r, conn, sql);
            }
        }
    }

    private boolean updateRecord(EmergencyRecord r, Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getTitle());
            ps.setString(2, r.getDescription());

            if (r instanceof EmergencyContact) {
                EmergencyContact c = (EmergencyContact) r;
                ps.setString(3, c.getPhone());
                ps.setString(4, c.getContactRole());
            } else {
                ps.setNull(3, Types.VARCHAR);
                ps.setNull(4, Types.VARCHAR);
            }

            if (r instanceof AssemblyPoint) {
                AssemblyPoint a = (AssemblyPoint) r;
                ps.setString(5, a.getLocationDescription());
            } else {
                ps.setNull(5, Types.VARCHAR);
            }

            ps.setString(6, r.getSource());
            ps.setObject(7, r.getLastVerified());
            ps.setInt(8, r.getId());

            // Returns false if no row was found with that ID
            return ps.executeUpdate() > 0;
        }
    }

    private void deleteChecklistItems(int recordId, Connection conn) throws SQLException {
        String sql = "DELETE FROM checklist_items WHERE record_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recordId);
            ps.executeUpdate();
        }
    }

    @Override
    public boolean delete(int id) throws SQLException, IOException {
        String sql = "DELETE FROM records WHERE id = ?";
        // The ON DELETE CASCADE in the schema handles checklist_items automatically
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public EmergencyRecord findById(int id) throws SQLException, IOException {
        String sql = "SELECT * FROM records WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    EmergencyRecord record = mapRow(rs);
                    // Load items if it's a checklist
                    if (record instanceof Checklist) {
                        loadChecklistItems((Checklist) record, conn);
                    }
                    return record;
                }
            }
        }
        return null; // not found
    }

    @Override
    public List<EmergencyRecord> findAll() throws SQLException, IOException {
        String sql = "SELECT * FROM records ORDER BY type, title";
        List<EmergencyRecord> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                EmergencyRecord record = mapRow(rs);
                if (record instanceof Checklist) {
                    loadChecklistItems((Checklist) record, conn);
                }
                result.add(record);
            }
        }
        return result;
    }

    @Override
    public List<EmergencyRecord> findByType(RecordType type) throws SQLException, IOException {
        String sql = "SELECT * FROM records WHERE type = ? ORDER BY title";
        List<EmergencyRecord> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EmergencyRecord record = mapRow(rs);
                    if (record instanceof Checklist) {
                        loadChecklistItems((Checklist) record, conn);
                    }
                    result.add(record);
                }
            }
        }
        return result;
    }

    @Override
    public List<EmergencyRecord> search(String keyword) throws SQLException, IOException {
        return searchInternal(keyword, null);
    }

    @Override
    public List<EmergencyRecord> search(String keyword, RecordType type) throws SQLException, IOException {
        return searchInternal(keyword, type);
    }

    // Internal search method used by both overloads.
    // Escapes wildcard characters in the keyword to prevent accidental "match all" queries.
    private List<EmergencyRecord> searchInternal(String keyword, RecordType type) throws SQLException, IOException {
        // Escape the special LIKE characters so searching for "%" doesn't match everything
        // We use "!" as the escape character
        String escaped = keyword
            .replace("!", "!!")   // escape our own escape character first
            .replace("%", "!%")   // escape percent
            .replace("_", "!_");  // escape underscore
        String pattern = "%" + escaped + "%";

        // Build the SQL query. We search across many columns.
        StringBuilder sqlBuilder = new StringBuilder(
            "SELECT * FROM records WHERE (" +
            "title LIKE ? ESCAPE '!' OR " +
            "description LIKE ? ESCAPE '!' OR " +
            "source LIKE ? ESCAPE '!' OR " +
            "phone LIKE ? ESCAPE '!' OR " +
            "contact_role LIKE ? ESCAPE '!' OR " +
            "location_description LIKE ? ESCAPE '!' OR " +
            "EXISTS (SELECT 1 FROM checklist_items ci WHERE ci.record_id = records.id AND ci.item_text LIKE ? ESCAPE '!')" +
            ")"
        );

        // Collect all parameter values in a list so we can bind them in a loop
        // (avoids hard-coded index numbers which cause bugs when SQL changes)
        List<Object> params = new ArrayList<>();
        params.add(pattern); // title
        params.add(pattern); // description
        params.add(pattern); // source
        params.add(pattern); // phone
        params.add(pattern); // contact_role
        params.add(pattern); // location_description
        params.add(pattern); // checklist_items subquery

        // Optionally filter by type
        if (type != null) {
            sqlBuilder.append(" AND type = ?");
            params.add(type.name());
        }

        sqlBuilder.append(" ORDER BY type, title");

        List<EmergencyRecord> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlBuilder.toString())) {

            // Bind each parameter using a loop (index starts at 1 in JDBC)
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EmergencyRecord record = mapRow(rs);
                    if (record instanceof Checklist) {
                        loadChecklistItems((Checklist) record, conn);
                    }
                    result.add(record);
                }
            }
        }
        return result;
    }

    @Override
    public boolean markVerified(int id, LocalDate date) throws SQLException, IOException {
        String sql = "UPDATE records SET last_verified = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, date);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean contactExists(String title, String phone) throws SQLException, IOException {
        String sql = "SELECT COUNT(*) FROM records WHERE type = 'CONTACT' AND title = ? AND phone = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
}
