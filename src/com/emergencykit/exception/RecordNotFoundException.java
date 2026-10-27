package com.emergencykit.exception;

// Signals that a requested emergency record ID does not exist in the database.
// Used by RecordService when lookups, updates, or deletions fail to find a target.
public class RecordNotFoundException extends Exception {

    public RecordNotFoundException(String message) {
        super(message);
    }
}
