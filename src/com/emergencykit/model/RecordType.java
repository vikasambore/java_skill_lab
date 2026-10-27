package com.emergencykit.model;

// Enumerates the supported categories of emergency records in the system.
// Used in models, DAO queries, and menus to classify and filter records.
public enum RecordType {

    CONTACT("Emergency Contact"),
    ASSEMBLY("Assembly Point"),
    CHECKLIST("Checklist"),
    INSTRUCTION("Instruction");

    private String friendlyLabel;

    RecordType(String friendlyLabel) {
        this.friendlyLabel = friendlyLabel;
    }

    public String label() {
        return friendlyLabel;
    }
}
