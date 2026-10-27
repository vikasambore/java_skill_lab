package com.emergencykit.exception;

// Represents authentication failure or insufficient role permissions.
// Used by AuthService and RecordService to guard admin-only operations.
public class UnauthorizedException extends Exception {

    public UnauthorizedException(String message) {
        super(message);
    }
}
