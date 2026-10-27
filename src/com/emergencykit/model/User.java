package com.emergencykit.model;

// Encapsulates user account credentials and assigned system role.
// Used by UserDAO and AuthService to represent the logged-in user.
public class User {

    private int id;
    private String username;
    private String passwordHash;
    private Role role;

    public User(int id, String username, String passwordHash, Role role) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
