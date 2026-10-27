package com.emergencykit.exception;

// Thrown when someone tries to find a record by ID but it doesn't exist.
// This is a checked exception so callers must handle it.
public class RecordNotFoundException extends Exception {

    public RecordNotFoundException(String message) {
        super(message);
    }
}
