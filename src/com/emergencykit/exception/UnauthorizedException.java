package com.emergencykit.exception;

// Thrown when a user tries to do something they don't have permission for.
// For example, a VIEWER trying to add or delete a record.
public class UnauthorizedException extends Exception {

    public UnauthorizedException(String message) {
        super(message);
    }
}
