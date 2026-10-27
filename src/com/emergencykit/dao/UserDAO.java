package com.emergencykit.dao;

import com.emergencykit.model.Role;
import com.emergencykit.model.User;
import com.emergencykit.util.DBConnection;

import java.io.IOException;
import java.sql.*;

// Handles database operations for users (login lookup).
public class UserDAO {

    // Finds a user by their username. Returns null if the username doesn't exist.
    // Used by AuthService during login to look up the stored password hash.
    public User findByUsername(String username) throws SQLException, IOException {
        String sql = "SELECT id, username, password_hash, role FROM users WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String uname = rs.getString("username");
                    String hash = rs.getString("password_hash");
                    Role role = Role.valueOf(rs.getString("role")); // convert DB string to enum
                    return new User(id, uname, hash, role);
                }
            }
        }
        return null; // username not found
    }

    // Adds a new user to the database.
    // Returns the auto-generated id.
    public int add(User u) throws SQLException, IOException {
        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getUsername());
            ps.setString(2, u.getPasswordHash());
            ps.setString(3, u.getRole().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }
}
