package com.emergencykit.exception;

// Represents validation errors for missing or malformed input data.
// Used across services and utility classes to signal invalid user entries.
public class InvalidDataException extends Exception {

    public InvalidDataException(String message) {
        super(message);
    }
}
