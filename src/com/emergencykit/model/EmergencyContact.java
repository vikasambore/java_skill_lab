package com.emergencykit.model;

import java.time.LocalDate;

public class EmergencyContact extends EmergencyRecord {

    private String phone;
    private String contactRole;

    public EmergencyContact() {
        super(RecordType.CONTACT);
    }

    public EmergencyContact(int id, String title, String description,
                             String phone, String contactRole,
                             String source, LocalDate lastVerified) {
        super(id, RecordType.CONTACT, title, description, source, lastVerified);
        this.phone = phone;
        this.contactRole = contactRole;
    }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getContactRole() { return contactRole; }
    public void setContactRole(String contactRole) { this.contactRole = contactRole; }

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
