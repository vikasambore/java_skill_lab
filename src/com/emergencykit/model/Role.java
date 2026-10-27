package com.emergencykit.model;

// Defines system authorization levels such as ADMIN and VIEWER.
// Used by User, UserDAO, and AuthService to enforce access control.
public enum Role {
    ADMIN,
    VIEWER
}
