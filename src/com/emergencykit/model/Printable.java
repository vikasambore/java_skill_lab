package com.emergencykit.model;

// Interface for records that can be printed/exported as formatted text.
// Only Checklist implements this.
public interface Printable {

    // Returns the record formatted as a printable text string
    String toPrintableText();
}
