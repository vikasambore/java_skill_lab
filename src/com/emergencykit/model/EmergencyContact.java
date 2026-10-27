package com.emergencykit.model;

import java.time.LocalDate;

// Represents an emergency contact (phone number + role).
// Extends EmergencyRecord and always has type = CONTACT.
public class EmergencyContact extends EmergencyRecord {

    private String phone;       // e.g. "0120-2345678"
    private String contactRole; // e.g. "Chief Security Officer"

    // Constructor for creating a NEW contact (no ID yet)
    public EmergencyContact() {
        // Always pass CONTACT type to the parent
        super(RecordType.CONTACT);
    }

    // Constructor for loading from the database
    public EmergencyContact(int id, String title, String description,
                             String phone, String contactRole,
                             String source, LocalDate lastVerified) {
        super(id, RecordType.CONTACT, title, description, source, lastVerified);
        this.phone = phone;
        this.contactRole = contactRole;
    }

    // Getters and setters
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getContactRole() { return contactRole; }
    public void setContactRole(String contactRole) { this.contactRole = contactRole; }

    // Provides a nicely formatted multi-line view of this contact
    @Override
    public String display() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Emergency Contact\n");
        sb.append("  -----------------\n");
        sb.append("  Title       : ").append(getTitle()).append("\n");
        sb.append("  Role        : ").append(contactRole != null ? contactRole : "N/A").append("\n");
        sb.append("  Phone       : ").append(phone != null ? phone : "N/A").append("\n");
        if (getDescription() != null && !getDescription().isEmpty()) {
            sb.append("  Description : ").append(getDescription()).append("\n");
        }
        sb.append(commonFooter());
        return sb.toString();
    }
}
