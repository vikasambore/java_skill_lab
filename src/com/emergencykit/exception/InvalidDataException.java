package com.emergencykit.exception;

// Thrown when user input data is invalid.
// For example: blank title, invalid date format, phone number with wrong characters.
public class InvalidDataException extends Exception {

    public InvalidDataException(String message) {
        super(message);
    }
}
