package com.emergencykit.service;

import com.emergencykit.dao.UserDAO;
import com.emergencykit.exception.UnauthorizedException;
import com.emergencykit.model.User;
import com.emergencykit.util.PasswordUtil;

import java.io.IOException;
import java.sql.SQLException;

// Handles login, logout, and permission checks.
// Keeps track of who is currently logged in.
public class AuthService {

    // The currently logged-in user, or null if nobody is logged in
    private User currentUser;

    private UserDAO userDAO;

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    // Attempts to log in with the given username and password.
    // Hashes the plain password and compares it to the stored hash.
    // Throws UnauthorizedException with the same message for wrong username OR wrong password
    // (we don't tell the attacker which one was wrong).
    public User login(String username, String plainPassword)
            throws UnauthorizedException, SQLException, IOException {

        User user = userDAO.findByUsername(username);

        // Hash the entered password to compare with stored hash
        String enteredHash = PasswordUtil.hash(plainPassword);

        // Reject if user not found OR if password hash doesn't match
        if (user == null || !user.getPasswordHash().equals(enteredHash)) {
            throw new UnauthorizedException("Invalid username or password.");
        }

        // Login successful
        this.currentUser = user;
        return user;
    }

    // Logs out the current user
    public void logout() {
        this.currentUser = null;
    }

    // Returns the currently logged-in user (or null)
    public User getCurrentUser() {
        return currentUser;
    }

    // Returns true if someone is logged in
    public boolean isLoggedIn() {
        return currentUser != null;
    }

    // Used by admin-only methods to verify the current user has admin rights.
    // IMPORTANT: We check for null FIRST to avoid a NullPointerException.
    public void requireAdmin() throws UnauthorizedException {
        if (currentUser == null) {
            throw new UnauthorizedException("Please log in first.");
        }
        if (!currentUser.isAdmin()) {
            throw new UnauthorizedException("Admin access required for this action.");
        }
    }
}
