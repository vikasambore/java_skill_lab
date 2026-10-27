package com.emergencykit.model;

// Represents a user who can log into the application.
// Users are either ADMIN or VIEWER.
public class User {

    private int id;
    private String username;
    private String passwordHash; // stored as SHA-256 hex, never the real password
    private Role role;

    // Constructor for creating a user object (typically when loading from database)
    public User(int id, String username, String passwordHash, Role role) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    // Getters (no setters needed for user objects after creation)
    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }

    // Convenient check used in AuthService
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
