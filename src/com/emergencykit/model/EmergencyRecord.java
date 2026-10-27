package com.emergencykit.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;

// Abstract base class for all emergency records.
// We use 'abstract' because we never create a plain EmergencyRecord directly --
// we always create one of its subclasses (EmergencyContact, AssemblyPoint, etc.)
public abstract class EmergencyRecord {

    // --- Static configurable field ---
    // This is NOT final because it is set from db.properties at startup.
    // All subclass instances share this same value.
    public static int STALE_DAYS = 180;

    // --- Private fields (encapsulation: only accessible through getters/setters) ---
    private int id;
    private RecordType type;      // What kind of record this is
    private String title;
    private String description;
    private String source;        // The responsible authority (e.g. "Campus Security Office")
    private LocalDate lastVerified; // When this record was last checked for accuracy

    // --- Protected constructors (only subclasses can call these) ---

    // Constructor used when creating a new record (no id yet)
    // Each subclass calls this with its own fixed type
    protected EmergencyRecord(RecordType type) {
        this.type = type;
    }

    // Full constructor used when loading from the database
    protected EmergencyRecord(int id, RecordType type, String title, String description,
                               String source, LocalDate lastVerified) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.description = description;
        this.source = source;
        this.lastVerified = lastVerified;
    }

    // --- Getters ---
    public int getId() { return id; }
    public RecordType getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSource() { return source; }
    public LocalDate getLastVerified() { return lastVerified; }

    // --- Setters (no setter for type: a record's type never changes) ---
    public void setId(int id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setSource(String source) { this.source = source; }
    public void setLastVerified(LocalDate lastVerified) { this.lastVerified = lastVerified; }

    // --- Business logic methods ---

    // Returns true if this record has not been verified recently enough.
    // A record verified exactly STALE_DAYS days ago is NOT stale (boundary is exclusive).
    public boolean isStale() {
        if (lastVerified == null) return true;
        // "before" means strictly before, so exactly STALE_DAYS days = not stale
        return lastVerified.isBefore(LocalDate.now().minusDays(STALE_DAYS));
    }

    // How many days since the record was last verified.
    // We clamp at 0 in case the clock is wrong and returns a negative number.
    public long daysSinceVerified() {
        if (lastVerified == null) return 0;
        long days = ChronoUnit.DAYS.between(lastVerified, LocalDate.now());
        return days < 0 ? 0 : days; // never show negative days
    }

    // Each subclass MUST provide its own display() implementation.
    // This is polymorphism: the correct display() is called based on the actual type.
    public abstract String display();

    // Helper that subclasses include at the bottom of their display() output.
    // Shows source, last verified date, and a [STALE] warning if needed.
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

    // One-line summary for list views
    // Format: [ID] TYPE | Title | Verified: dd MMM yyyy | [STALE]
    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        String verifiedStr = lastVerified != null ? lastVerified.format(fmt) : "Unknown";
        String staleFlag = isStale() ? " [STALE]" : "";
        return String.format("[%d] %-12s | %-40s | Verified: %s%s",
                id, type.name(), title, verifiedStr, staleFlag);
    }
}
