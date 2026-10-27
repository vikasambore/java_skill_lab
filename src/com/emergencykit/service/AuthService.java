package com.emergencykit.service;

import com.emergencykit.dao.UserDAO;
import com.emergencykit.exception.UnauthorizedException;
import com.emergencykit.model.User;
import com.emergencykit.util.PasswordUtil;

import java.io.IOException;
import java.sql.SQLException;

// Handles user login authentication, session state, and permission validation.
// Used by Main and RecordService to verify credentials and enforce admin access.
public class AuthService {

    private User currentUser;
    private UserDAO userDAO;

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public User login(String username, String plainPassword)
            throws UnauthorizedException, SQLException, IOException {

        User user = userDAO.findByUsername(username);
        String enteredHash = PasswordUtil.hash(plainPassword);

        if (user == null || !user.getPasswordHash().equals(enteredHash)) {
            throw new UnauthorizedException("Invalid username or password.");
        }

        this.currentUser = user;
        return user;
    }

    public void logout() {
        this.currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public void requireAdmin() throws UnauthorizedException {
        if (currentUser == null) {
            throw new UnauthorizedException("Please log in first.");
        }
        if (!currentUser.isAdmin()) {
            throw new UnauthorizedException("Admin access required for this action.");
        }
    }
}
