package com.emergencykit.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;

public abstract class EmergencyRecord {

    public static int STALE_DAYS = 180;

    private int id;
    private RecordType type;
    private String title;
    private String description;
    private String source;
    private LocalDate lastVerified;

    protected EmergencyRecord(RecordType type) {
        this.type = type;
    }

    protected EmergencyRecord(int id, RecordType type, String title, String description,
                               String source, LocalDate lastVerified) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.description = description;
        this.source = source;
        this.lastVerified = lastVerified;
    }

    public int getId() { return id; }
    public RecordType getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSource() { return source; }
    public LocalDate getLastVerified() { return lastVerified; }

    public void setId(int id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setSource(String source) { this.source = source; }
    public void setLastVerified(LocalDate lastVerified) { this.lastVerified = lastVerified; }

    public boolean isStale() {
        if (lastVerified == null) return true;
        return lastVerified.isBefore(LocalDate.now().minusDays(STALE_DAYS));
    }

    public long daysSinceVerified() {
        if (lastVerified == null) return 0;
        long days = ChronoUnit.DAYS.between(lastVerified, LocalDate.now());
        return days < 0 ? 0 : days;
    }

    public abstract String display();

    protected String commonFooter() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        StringBuilder sb = new StringBuilder();
        sb.append("  Source        : ").append(source).append("\n");
        sb.append("  Last verified : ").append(lastVerified != null ? lastVerified.format(fmt) : "Unknown");
        sb.append(" (").append(daysSinceVerified()).append(" days ago)\n");
        if (isStale()) {
            sb.append("  [STALE] needs re-verification\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        String verifiedStr = lastVerified != null ? lastVerified.format(fmt) : "Unknown";
        String staleFlag = isStale() ? " [STALE]" : "";
        return String.format("[%d] %-12s | %-40s | Verified: %s%s",
                id, type.name(), title, verifiedStr, staleFlag);
    }
}
