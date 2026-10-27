# Project Explained: Offline Emergency Information Kit

> This file explains the full architecture of the project in plain language.
> What every file does, how the classes connect, and how data moves through the app.

---

## Table of Contents

1.  Big Picture - What Does This App Do?
2.  Folder Structure - Where Is Everything?
3.  Architecture - The 5 Layers
4.  Layer 1 - model/ (Data Blueprints)
5.  Layer 2 - dao/ (Talking to the Database)
6.  Layer 3 - service/ (The Brain)
7.  Layer 4 - util/ (The Helper Tools)
8.  Layer 5 - app/ (The Menu You See)
9.  The Database Design
10. How Classes Are Connected
11. How Data Flows - Step by Step
12. Key Java Concepts and Where They Are Used
13. Configuration Files Explained
14. SQL Files Explained

---

## 1. Big Picture

This is a CONSOLE APPLICATION (text-based, no GUI) written in Java 21.

**What it does:**
- Stores emergency information for a college campus:
  phone numbers, assembly points, checklists, and instructions
- Works FULLY OFFLINE (everything stored in a local MySQL database)
- Two types of users: ADMIN (can add/edit/delete) and VIEWER (read-only)
- Can SEARCH records, FLAG STALE (outdated) entries, and EXPORT checklists to .txt files

**What it does NOT do:**
- No website, no buttons, no Swing or JavaFX GUI
- No internet needed at any point
- No Spring, no Hibernate, no Lombok -- plain Java + JDBC only

---

## 2. Folder Structure

```
SkillLab El/                        <- Project root (NetBeans runs from here)
|
+-- db.properties                   <- YOUR database password (NOT in git)
+-- db.properties.example           <- Template to copy from
+-- .gitignore                      <- Tells git what NOT to track
+-- README.md                       <- Setup instructions
+-- PROJECT_SPEC.md                 <- Original requirements document
+-- PROJECT_EXPLAINED.md            <- THIS file
|
+-- sql/
|   +-- schema.sql                  <- Creates tables (WARNING: erases data if re-run!)
|   +-- sample_data.sql             <- Fills DB with test data (safe to re-run)
|
+-- sample/
|   +-- contacts.csv                <- Test CSV for the "import contacts" feature
|
+-- exports/                        <- Created at runtime; holds exported .txt files
|
+-- src/
    +-- com/
        +-- emergencykit/           <- Root Java package
            |
            +-- model/              <- Data blueprints
            +-- dao/                <- Database access
            +-- service/            <- Business logic + validation
            +-- exception/          <- Custom error types
            +-- util/               <- Helper utilities
            +-- app/                <- Main.java (the menu)
```

**Total: 25 Java files across 6 packages.**

---

## 3. Architecture - The 5 Layers

The project uses a LAYERED architecture.
Think of it as floors in a building -- each floor only talks to the one below it.

```
+--------------------------------------------------+
|              LAYER 5: app/                       |
|          Main.java -- the text menus             |
|   User interacts here. Calls the Service layer.  |
+--------------------------------------------------+
             |                    |
             v                    v
+--------------------------------------------------+
|            LAYER 3: service/                     |
|   RecordService.java    AuthService.java         |
|   Validation, rules, permissions. Calls DAO.     |
+--------------------------------------------------+
                      |
                      v
+--------------------------------------------------+
|             LAYER 2: dao/                        |
|    RecordDAOImpl.java    UserDAO.java            |
|    Runs SQL queries. Uses DBConnection.          |
+--------------------------------------------------+
                      |
                      v
+--------------------------------------------------+
|             LAYER 4: util/                       |
|    DBConnection.java + DateUtil + others         |
|    Opens MySQL connections. Reads config.        |
+--------------------------------------------------+
                      |
                      v
+--------------------------------------------------+
|       MySQL Database: emergency_kit              |
|   tables: records, checklist_items, users        |
+--------------------------------------------------+

+--------------------------------------------------+
|             LAYER 1: model/                      |
|    EmergencyRecord, EmergencyContact, etc.       |
|    Plain Java objects -- used by ALL layers.     |
+--------------------------------------------------+
```

**Model is special:** it does not belong to one layer.
Every layer uses model objects to pass data around.
Think of them as the shared "language" all layers speak to each other.

---

## 4. Layer 1 - model/ (Data Blueprints)

**Package:** `com.emergencykit.model`

Plain Java classes and enums that describe what data looks like.
No database code, no menu code -- just fields, getters, setters, and logic.

---

### RecordType.java -- Enum

An enum is a fixed list of named constants. You cannot add new values at runtime.

```
CONTACT     -> label() returns "Emergency Contact"
ASSEMBLY    -> label() returns "Assembly Point"
CHECKLIST   -> label() returns "Checklist"
INSTRUCTION -> label() returns "Instruction"
```

Used everywhere to tag what kind of record something is.
For example, RecordDAOImpl reads the type column and calls `RecordType.valueOf("CONTACT")`.

---

### Role.java -- Enum

```
ADMIN  -> full access: add, edit, delete
VIEWER -> read-only access
```

Used in User.java and checked by AuthService.requireAdmin().

---

### Printable.java -- Interface

```java
interface Printable {
    String toPrintableText();
}
```

An interface is a contract: any class that says "implements Printable"
MUST provide a toPrintableText() method.
Only Checklist implements it, because only checklists are exported to .txt files.

---

### EmergencyRecord.java -- Abstract Class (The Most Important Model)

This is the PARENT CLASS of all four record types.
`abstract` means you can never write `new EmergencyRecord()` directly.
You always create a specific subclass: EmergencyContact, AssemblyPoint, etc.

**Fields common to ALL record types:**

| Field        | Type        | Meaning                                        |
|--------------|-------------|------------------------------------------------|
| id           | int         | Auto-incremented primary key from MySQL        |
| type         | RecordType  | What kind of record (CONTACT, ASSEMBLY, etc.)  |
| title        | String      | The name/title of the record                   |
| description  | String      | Extra details or notes                         |
| source       | String      | Responsible authority ("Campus Security")      |
| lastVerified | LocalDate   | When this record was last checked for accuracy |

**Static field:**
```java
public static int STALE_DAYS = 180;
```
`static` means ALL records share this one value (not per-instance).
NOT `final` because it is read from db.properties at startup and can be configured.

**Key methods:**

| Method               | What it does                                               |
|----------------------|------------------------------------------------------------|
| `isStale()`          | true if not verified within STALE_DAYS days                |
| `daysSinceVerified()`| days since last verification (clamped at 0 for bad clocks) |
| `display()`          | ABSTRACT -- each subclass provides its own version         |
| `commonFooter()`     | PROTECTED helper: Source + Last Verified + [STALE] line    |

**Two constructors (both protected -- only subclasses can call them):**
```java
EmergencyRecord(RecordType type)                        // for NEW records (no ID yet)
EmergencyRecord(int id, RecordType type, String title,
                String description, String source,
                LocalDate lastVerified)                 // for loading from database
```
Every subclass constructor calls one of these via `super(...)`.

---

### EmergencyContact.java -- Subclass

**Extra fields:** `phone`, `contactRole`

Constructor always passes `RecordType.CONTACT` to `super(...)`.
This ensures type is NEVER null when inserting into the database.

`display()` shows: Title > Role > Phone > footer (source, date, [STALE] if needed).

---

### AssemblyPoint.java -- Subclass

**Extra field:** `locationDescription` (step-by-step directions to the point)

Constructor always passes `RecordType.ASSEMBLY` to `super(...)`.

---

### Instruction.java -- Subclass

**No extra fields.** Steps are stored in the inherited `description` field.
Each step is on a separate line, joined by `\n`.

Constructor always passes `RecordType.INSTRUCTION` to `super(...)`.

`display()` splits description by `\n` and prints each line as a step.

---

### ChecklistItem.java -- Helper Class

Represents ONE item in a checklist (e.g. "Sound the fire alarm").

**Fields:** `id`, `itemText`, `itemOrder` (1, 2, 3...)

This is NOT a subclass of EmergencyRecord.
It is a helper object OWNED BY Checklist, stored in its own DB table.

---

### Checklist.java -- Subclass + implements Printable

The most complex model class. Extends EmergencyRecord AND implements Printable.

**Extra field:** `List<ChecklistItem> items` (starts empty)

**Item management:**
- `addItem(String text)` -- adds item, auto-assigns order number
- `clearItems()` -- removes all (used when re-entering during update)
- `removeItem(int index)` -- removes one, re-numbers the rest

**Because it implements Printable**, `toPrintableText()` formats the checklist
as a 46-column ASCII box for file export:

```
==============================================
 FIRE EVACUATION CHECKLIST
==============================================
 Source        : Campus Security Office
 Last verified : 27 Oct 2026
 Status        : CURRENT
----------------------------------------------
 [ ] 1. Sound the fire alarm immediately
 [ ] 2. Evacuate via the nearest exit
----------------------------------------------
 Printed on    : 27 Oct 2026
==============================================
```

Constructor always passes `RecordType.CHECKLIST` to `super(...)`.

---

### User.java -- Login Account

**Fields:** `id`, `username`, `passwordHash` (SHA-256 hex, never plain text), `Role role`

Key method: `isAdmin()` returns true if role == Role.ADMIN.

---

## 5. Layer 2 - dao/ (Talking to the Database)

**Package:** `com.emergencykit.dao`

**DAO = Data Access Object.**
This layer's ONLY job is to talk to MySQL.
It knows SQL. It knows nothing about menus or business rules.

---

### RecordDAO.java -- Interface

Defines what database operations are possible:

```
add(record)            -> insert one record, return the new auto-generated ID
addAll(list)           -> insert many records in ONE transaction (all or nothing)
update(record)         -> update by ID; returns false if ID not found
delete(id)             -> delete by ID; returns false if ID not found
findById(id)           -> return one record or null
findAll()              -> return all records
findByType(type)       -> return all records of one type
search(keyword)        -> keyword search across many columns
search(keyword, type)  -> keyword search + type filter (method OVERLOADING in DAO)
markVerified(id, date) -> update only the last_verified column
contactExists(t, p)    -> true if title+phone already exists (duplicate check)
```

**Why an interface?**
If you ever switch from MySQL to PostgreSQL or a file-based system,
you ONLY rewrite RecordDAOImpl. RecordService and Main stay unchanged.

---

### RecordDAOImpl.java -- The Real Database Worker

Implements every method in RecordDAO using JDBC.

**Key design decision 1: PreparedStatement everywhere**
```java
// WRONG (SQL injection risk):
"SELECT * FROM records WHERE title = '" + userInput + "'"

// RIGHT (safe):
PreparedStatement ps = conn.prepareStatement("SELECT * FROM records WHERE title = ?");
ps.setString(1, userInput);
```

**Key design decision 2: LocalDate instead of java.sql.Date**
```java
// WRONG (timezone can shift date by one day):
new java.sql.Date(...)

// RIGHT (timezone-safe):
ps.setObject(1, localDate);                        // writing
rs.getObject("last_verified", LocalDate.class);    // reading
```

**Key design decision 3: mapRow(ResultSet) -- Polymorphism in action**

Reads the `type` column and creates the correct subclass:
```java
switch (type) {
    case CONTACT:     return new EmergencyContact(id, title, ...);
    case ASSEMBLY:    return new AssemblyPoint(id, title, ...);
    case CHECKLIST:   // items loaded separately afterwards
                      return new Checklist(id, title, ...);
    case INSTRUCTION: return new Instruction(id, title, ...);
}
```
The calling code holds an `EmergencyRecord` reference but the actual object
is one of the four subclasses. This is POLYMORPHISM.

**Key design decision 4: Transactions for Checklists**

Adding/updating a checklist touches TWO tables. If one write fails, both must be undone:
```java
conn.setAutoCommit(false);
try {
    insertRecord(...);
    insertChecklistItems(...);
    conn.commit();         // both OK: make permanent
} catch (SQLException e) {
    conn.rollback();       // one failed: undo everything
} finally {
    conn.setAutoCommit(true);   // always restore
}
```

**Key design decision 5: Wildcard escaping in search**

Searching for `%` would match EVERYTHING in a SQL LIKE clause without escaping:
```java
String escaped = keyword
    .replace("!", "!!")   // escape our own escape char first
    .replace("%", "!%")   // escape percent
    .replace("_", "!_");  // escape underscore
String pattern = "%" + escaped + "%";
// SQL uses: LIKE ? ESCAPE '!'
```

**Key design decision 6: Loop-bound parameters (no hard-coded indexes)**

The search query has 7 identical `?` placeholders:
```java
List<Object> params = new ArrayList<>();
params.add(pattern);  // title
params.add(pattern);  // description
params.add(pattern);  // source
params.add(pattern);  // phone
params.add(pattern);  // contact_role
params.add(pattern);  // location_description
params.add(pattern);  // checklist_items subquery
// if type filter: params.add(type.name());

for (int i = 0; i < params.size(); i++) {
    ps.setObject(i + 1, params.get(i));  // JDBC index starts at 1
}
```
If the SQL changes, you only update the List -- not scattered `setString(4, ...)` calls.

---

### UserDAO.java

One key method: `findByUsername(String username)` -- returns User or null.
Used by AuthService during login.

---

## 6. Layer 3 - service/ (The Brain)

**Package:** `com.emergencykit.service`

The brain of the application:
- Enforces business rules (validation)
- Checks permissions (who can do what)
- Coordinates between DAO and the menu

**Rule:** Main calls Service. Service calls DAO. Main NEVER talks to DAO directly.

---

### AuthService.java

Tracks who is currently logged in.

**Key field:** `private User currentUser;` (null = nobody logged in)

**login(username, password):**
1. UserDAO.findByUsername() -- get stored user
2. PasswordUtil.hash(enteredPassword) -- hash what user typed
3. Compare hashes -- match = set currentUser; no match = throw exception
4. Same error message for wrong username OR wrong password (security)

**requireAdmin():**
```java
public void requireAdmin() throws UnauthorizedException {
    if (currentUser == null) {           // check null FIRST -- avoid NullPointerException
        throw new UnauthorizedException("Please log in first.");
    }
    if (!currentUser.isAdmin()) {
        throw new UnauthorizedException("Admin access required for this action.");
    }
}
```

---

### RecordService.java

**Constructor:** `RecordService(RecordDAO dao, AuthService authService)`

**Read methods (all logged-in users):**

| Method                | What it does                                                |
|-----------------------|-------------------------------------------------------------|
| `getAll()`            | All records from DAO                                        |
| `getByType(type)`     | Records of one type                                         |
| `search(keyword)`     | Keyword search -- overloaded version 1                      |
| `search(keyword,type)`| Keyword + type filter -- overloaded version 2               |
| `getStaleRecords()`   | Stream filter + sort oldest-first                           |
| `groupByType()`       | Stream + EnumMap for ordered grouping by type               |
| `getById(id)`         | Converts DAO's null return into RecordNotFoundException      |
| `getChecklist(id)`    | Like getById but instanceof check before cast               |

**groupByType() uses EnumMap -- important detail:**
```java
return all.stream().collect(
    Collectors.groupingBy(
        EmergencyRecord::getType,
        () -> new EnumMap<>(RecordType.class),   // factory ensures ENUM ORDER
        Collectors.toList()
    )
);
```
A regular HashMap gives RANDOM order. EnumMap always gives:
CONTACT -> ASSEMBLY -> CHECKLIST -> INSTRUCTION.

**getChecklist() uses instanceof before casting -- avoids ClassCastException:**
```java
EmergencyRecord record = getById(id);
if (!(record instanceof Checklist)) {
    throw new InvalidDataException("Record " + id + " is not a checklist.");
}
return (Checklist) record;   // safe -- we already verified
```

**Admin-only methods (each starts with authService.requireAdmin()):**

| Method                | What it does                                    |
|-----------------------|-------------------------------------------------|
| `addRecord(r)`        | validate(r) then dao.add(r)                     |
| `updateRecord(r)`     | validate(r) then dao.update(r); throws if false |
| `deleteRecord(id)`    | dao.delete(id); throws if false                 |
| `markVerified(id)`    | dao.markVerified(id, today)                     |
| `importContacts(path)`| CSV import -- see 5-step flow below             |

**validate(record) -- private, checks all rules:**

| Rule                                       | Type         |
|--------------------------------------------|--------------|
| title not blank, max 150 chars             | All          |
| source not blank, max 150 chars            | All          |
| description max 5000 chars                 | All          |
| lastVerified not null, not in the future   | All          |
| description not blank                      | Instruction  |
| phone matches `[0-9+\-() ]{3,30}`         | Contact      |
| contactRole max 100 chars                  | Contact      |
| locationDescription not blank              | Assembly     |
| at least 1 item; each item max 255 chars   | Checklist    |

**CSV Import -- 5-step flow:**
```
Step 1: CsvImporter.readContacts(path)
        Parse ALL rows (nothing written to DB yet)

Step 2: validate() each row
        Any invalid row ABORTS the entire import (nothing inserted)

Step 3: For each row check:
        a) dao.contactExists(title, phone) -- already in database?
        b) seenInBatch.contains(key)       -- duplicate within this file?
        Mark as skipped if either is true

Step 4: dao.addAll(toInsert)
        Insert all remaining rows in ONE transaction

Step 5: Return ImportResult(imported, skipped)
        Main prints "Imported X, skipped Y duplicates"
```

**Generic filter method:**
```java
public static <T> List<T> filter(List<T> items, Predicate<T> rule) {
    List<T> result = new ArrayList<>();
    for (T item : items) {
        if (rule.test(item)) result.add(item);
    }
    return result;
}
```
`<T>` is a TYPE PARAMETER -- this method works with any type.
`filter(records, r -> r.isStale())` -- filter for stale records.
`filter(names, s -> s.startsWith("A"))` -- filter for names starting with A.

**Nested class ImportResult:**
```java
public static class ImportResult {
    public int imported;
    public int skipped;
}
```
A small class inside RecordService to return two numbers together.

---

## 7. Layer 4 - util/ (The Helper Tools)

**Package:** `com.emergencykit.util`

Helper classes used by all other layers. Each does exactly ONE job.

---

### DBConnection.java

Manages database connections. **Critical rule: NO static initializer block.**

**Why no static block?**
If db.properties is missing and we load it in `static {}`, Java throws
`ExceptionInInitializerError` -- uncatchable, ugly crash with stack trace.
Instead we use LAZY LOADING:
```java
private static Properties props = null;  // not loaded yet

public static Connection getConnection() throws SQLException, IOException {
    if (props == null) {        // first call? load now
        loadConfig();
    }
    return DriverManager.getConnection(url, user, password);
}
```

`testConnection()` -- called at startup. Opens and immediately closes a connection.
If MySQL is down, Main catches the exception and prints a helpful message.

`loadConfig()` also reads stale.days:
- `"abc"` or `"0"` -> invalid -> use 180 and print a one-line warning
- `"90"` -> valid -> set `EmergencyRecord.STALE_DAYS = 90`

---

### DateUtil.java

Static helpers for dates. No instances needed.

| Method            | Does                                                             |
|-------------------|------------------------------------------------------------------|
| `format(date)`    | Returns "27 Oct 2026" format                                     |
| `parse(str)`      | Parses "2026-10-27"; throws InvalidDataException if bad or impossible |
| `today()`         | Returns LocalDate.now()                                          |

`parse()` catches DateTimeParseException and rethrows as a friendly message:
```java
try {
    return LocalDate.parse(dateStr.trim(), INPUT_FORMAT);
} catch (DateTimeParseException e) {
    throw new InvalidDataException(
        "Invalid date '" + dateStr + "'. Use yyyy-MM-dd, e.g. 2026-10-27"
    );
}
```
Catches both format errors (`"abc"`) and impossible dates (`"2026-13-45"`).

---

### PasswordUtil.java

One static method: `hash(String plainPassword)`
Converts a plain password to a 64-character lowercase hex SHA-256 hash.

**Critical: why `String.format("%02x", b)` and not `Integer.toHexString()`:**
```java
// WRONG -- drops leading zero for bytes 0-15:
Integer.toHexString(b & 0xff)   // byte value 5 becomes "5", not "05"

// RIGHT -- always exactly 2 hex digits:
String.format("%02x", b)        // byte value 5 becomes "05"
```
About 6% of byte values (0x00 through 0x0F) would produce wrong hashes.
Login would fail for those users even with the correct password.
The hash also would not match MySQL's `SHA2('password', 256)`.
The output is always EXACTLY 64 characters (32 bytes x 2 hex digits).

---

### InputHelper.java

**The gatekeeper for ALL console input.**
Every single read in the entire application goes through this class.

**Why one central class?**
- Guarantees exactly ONE Scanner on System.in
- Guarantees `nextLine()` is always used (never `nextInt()` which leaves `\n` in buffer)
- All input is trimmed consistently
- Surrounding double quotes are always stripped (Windows "Copy as path" compatibility)

**Scanner is NEVER closed:**
```java
// NEVER do this -- closes System.in, breaks all future reads:
try (Scanner scanner = new Scanner(System.in)) { ... }

// CORRECT -- lives for the whole program:
private Scanner scanner = new Scanner(System.in);
// JVM closes it implicitly on exit
```

**Methods:**

| Method                    | What it does                                              |
|---------------------------|-----------------------------------------------------------|
| `readLine(prompt)`        | Read, trim, strip surrounding double quotes               |
| `readRequired(prompt)`    | Like readLine but loops until non-blank                   |
| `readOptional(prompt)`    | Returns `""` on Enter (keep-old-value in updates)         |
| `readInt(prompt,min,max)` | Loops until valid integer in [min,max]                    |
| `readYesNo(prompt)`       | Accepts y/yes/n/no; loops on anything else                |
| `readDate(prompt,default)`| Enter = default; otherwise parse with DateUtil            |
| `readMultiLine(prompt)`   | Reads lines until blank line; returns List of Strings     |

---

### FileExporter.java

Exports checklists to .txt files and reads them back.

**exportChecklist(checklist):**
1. Creates `exports/` if it does not exist
2. Filename: `checklist_<id>_<slug>.txt`
3. Calls `checklist.toPrintableText()` for content
4. Writes with `BufferedWriter` in try-with-resources
5. Returns the `Path` to the created file

**slug(title):** makes a safe filename from a title
```
"Fire Evacuation Checklist!" -> "fire-evacuation-checklist"
Steps: lowercase > replace non-alphanumeric runs with "-" > trim dashes > cut to 40 chars
```

**listExports() -- important Windows detail:**
```java
// WRONG -- folder stays LOCKED on Windows after this:
return Files.list(path).filter(...).collect(...);

// RIGHT -- Stream closed by try-with-resources, folder unlocked:
try (Stream<Path> stream = Files.list(path)) {
    return stream.filter(...).sorted().collect(Collectors.toList());
}
```

---

### CsvImporter.java

Reads a CSV file and returns parsed EmergencyContact objects.
Does NOT write to the database (RecordService handles that).

**Key parsing trick -- `split(",", 6)`:**
```
Input: "Title,100,Role,Source,2026-01-01,Call first, then email, then visit"

split(",")    -> 8 parts -- commas in description split it  WRONG
split(",", 6) -> 6 parts -- commas in description preserved RIGHT
```
The `6` is the LIMIT -- split into at most 6 parts.
Everything after the 5th comma goes into the 6th part.

**Nested static class ParsedRow:**
```java
public static class ParsedRow {
    public int lineNumber;           // for error messages: "Line 3: invalid date"
    public EmergencyContact contact;
}
```

---

## 8. Layer 5 - app/ (The Menu You See)

**Package:** `com.emergencykit.app`

One file: `Main.java` -- the entry point and entire menu system.

**Startup sequence:**
```
1. Create ONE InputHelper (and its ONE Scanner)
2. Print welcome banner
3. DBConnection.testConnection() -- exit cleanly if DB is down
4. Create UserDAO, AuthService, RecordDAOImpl, RecordService
5. doLogin() -- up to 3 attempts, then exit
6. mainMenuLoop() -- runs until user logs out
```

**Outer safety net:**
```java
try {
    doLogin();
    mainMenuLoop();
} catch (NoSuchElementException e) {
    // Ctrl+D or Ctrl+Z -- input stream was closed
    System.out.println("Input closed. Goodbye.");
} catch (Exception e) {
    // Any unexpected crash -- friendly message, no stack trace
    System.out.println("Something went wrong: " + e.getMessage());
}
```

**Menu options in String[] arrays:**
```java
private static final String[] MAIN_MENU = {
    "1. Search records",
    "2. Browse by category",
    ...
    "9. Logout / Exit"
};
for (String option : MAIN_MENU) {
    System.out.println("  " + option);
}
```

**Every switch has a `default:` case:**
```java
switch (choice) {
    case 1: doSearch(); break;
    ...
    default: System.out.println("Invalid choice. Please try again.");
}
```

**Exception handling inside menu loop (specific -> general):**
```java
try {
    // service method call
} catch (InvalidDataException e) {
    System.out.println("Input error: " + e.getMessage());
} catch (RecordNotFoundException e) {
    System.out.println("Not found: " + e.getMessage());
} catch (UnauthorizedException e) {
    System.out.println("Access denied: " + e.getMessage());
} catch (SQLException | IOException e) {
    System.out.println("Error: " + e.getMessage());
} catch (Exception e) {
    System.out.println("Something went wrong: " + e.getMessage());
}
// After ANY catch: menu loops again. User NEVER sees a stack trace.
```

**Security -- path traversal prevention on "View exported file":**
```java
// We NEVER ask the user to type a path. We list files by number.
List<Path> exports = FileExporter.listExports();
int choice = input.readInt("Choose file number: ", 1, exports.size());
// Prevents: "../db.properties" -> seeing the database password
```

**Update flow -- pressing Enter keeps old value:**
```java
// In buildContact(existing):
System.out.println("  Current Title: " + existing.getTitle());
String val = input.readOptional("  New Title (Enter to keep): ");
if (!val.isEmpty()) contact.setTitle(val);
// Empty string = keep the old value
```

**Update also asks separately about re-verification:**
```java
boolean markVerified = input.readYesNo("Mark as re-verified today? (y/n): ");
if (markVerified) {
    updated.setLastVerified(LocalDate.now());
}
// Updating a record does NOT automatically change last_verified.
```

---

## 9. The Database Design

Database name: `emergency_kit`

### Table: users
```
id            INT AUTO    Primary key
username      VARCHAR(50) Unique login name
password_hash CHAR(64)    SHA-256 hex -- NEVER the plain password
role          ENUM        'ADMIN' or 'VIEWER'
```

### Table: records

One table holds ALL FOUR record types.
Columns that do not apply to a type are stored as NULL.

```
id                   INT AUTO     Primary key
type                 ENUM         CONTACT / ASSEMBLY / CHECKLIST / INSTRUCTION
title                VARCHAR(150) Always used
description          TEXT         INSTRUCTION: steps separated by \n. Others: notes.
phone                VARCHAR(30)  CONTACT only
contact_role         VARCHAR(100) CONTACT only
location_description TEXT         ASSEMBLY only
source               VARCHAR(150) Always required: who approved this information
last_verified        DATE         Always required: when this was last checked
```

### Table: checklist_items
```
id          INT AUTO    Primary key
record_id   INT         Foreign key -> records.id (ON DELETE CASCADE)
item_text   VARCHAR(255)The checklist item text
item_order  INT         1, 2, 3... keeps items ordered
```

**ON DELETE CASCADE:** When a record is deleted from `records`,
MySQL automatically deletes all its items from `checklist_items`.
No orphan rows, no manual cleanup needed.

**Why one table for all types?**
This is the "Single Table Inheritance" pattern.
Simpler than 4 separate tables with joins. Trade-off: some nullable columns.
For a student project this is perfectly acceptable.

---

## 10. How Classes Are Connected

**Inheritance (IS-A):**
```
EmergencyRecord (abstract)
    +-- EmergencyContact    (passes CONTACT to super)
    +-- AssemblyPoint       (passes ASSEMBLY to super)
    +-- Instruction         (passes INSTRUCTION to super)
    +-- Checklist           (passes CHECKLIST to super)
            |
            +-- implements --> Printable (interface)
            |
            +-- has-many --> ChecklistItem
```

**Uses-A relationships:**
```
User                  uses  Role (enum)
EmergencyRecord       uses  RecordType (enum)
```

**Interface implementation:**
```
RecordDAO (interface)   <-- implemented by -- RecordDAOImpl
Printable (interface)   <-- implemented by -- Checklist
```

**Dependencies (what each class needs):**
```
RecordDAOImpl  --needs--> DBConnection.getConnection()
UserDAO        --needs--> DBConnection.getConnection()
AuthService    --needs--> UserDAO, PasswordUtil
RecordService  --needs--> RecordDAO, AuthService, CsvImporter, DateUtil
Main           --needs--> RecordService, AuthService, InputHelper, FileExporter
```

---

## 11. How Data Flows - Step by Step

### Example A: Searching for "fire"

```
User types "fire"
    |
    v  [InputHelper.readRequired()]
Main.doSearch()
    calls input for type filter -> Enter (all types)
    calls recordService.search("fire")
    |
    v
RecordService.search("fire")
    checks keyword is not blank
    calls dao.search("fire")
    |
    v
RecordDAOImpl.searchInternal("fire", null)
    escapes "fire" -> pattern = "%fire%"
    builds SQL with 7 LIKE clauses
    binds 7 params in a loop
    executes SELECT against MySQL
    for each row in ResultSet:
        mapRow(rs) -> creates EmergencyContact, Checklist, etc.
        if Checklist: loadChecklistItems()
    returns List<EmergencyRecord>
    |
    v
Main.printRecordList(results)
    prints "Found 3 record(s):"
    for each: record.toString() -> one-line summary
    [STALE] appears automatically where isStale() == true
```

---

### Example B: Admin adds a contact

```
User: Admin menu -> Add record -> 1 (Contact)
    |
    v
Main.buildContact(null)
    readRequired("Title: ")        -> "Police Station"
    readRequired("Phone: ")        -> "100"
    readOptional("Contact role: ") -> "Police"
    readRequired("Source: ")       -> "Local Police Department"
    readDate(...)                  -> today (Enter pressed)
    creates new EmergencyContact()
    sets all fields via setters
    returns contact
    |
    v
Main calls recordService.addRecord(contact)
    |
    v
RecordService.addRecord(contact)
    authService.requireAdmin()     -> OK (user is admin)
    validate(contact):
        title not blank            -> OK
        source not blank           -> OK
        phone matches regex        -> OK ("100" is valid)
        date not in future         -> OK
    dao.add(contact)
    |
    v
RecordDAOImpl.add(contact)
    opens connection
    prepares INSERT SQL (RETURN_GENERATED_KEYS)
    sets 8 parameters: type, title, description, phone, contactRole,
                       null(locationDesc), source, date
    executeUpdate()
    getGeneratedKeys() -> new ID = 16
    closes connection
    returns 16
    |
    v
Main prints: "Record added successfully with ID: 16"
```

---

### Example C: Viewer tries the Admin menu

```
Viewer selects option 8 (Admin menu)
    |
    v
Main.doAdminMenu()
    calls authService.requireAdmin()
    |
    v
AuthService.requireAdmin()
    currentUser != null (viewer is logged in) -- OK
    currentUser.isAdmin() -> false
    throws UnauthorizedException("Admin access required for this action.")
    |
    v
mainMenuLoop() catch block:
    catch (UnauthorizedException e)
        System.out.println("Access denied: Admin access required for this action.")
    // menu continues, no crash, no stack trace
```

---

### Example D: Login flow

```
User types: username = "admin", password = "admin123"
    |
    v
Main.doLogin()
    calls authService.login("admin", "admin123")
    |
    v
AuthService.login("admin", "admin123")
    calls userDAO.findByUsername("admin")
    |
    v
UserDAO.findByUsername("admin")
    opens connection
    SELECT ... WHERE username = ? (bound: "admin")
    row found: id=1, hash="240be5...", role="ADMIN"
    returns new User(1, "admin", "240be5...", Role.ADMIN)
    |
    v
AuthService continues:
    PasswordUtil.hash("admin123")
        SHA-256("admin123") using UTF-8 bytes
        String.format("%02x", b) for each of 32 bytes
        -> "240be518fabd2724..." (64 chars)
    compare entered hash with stored hash -> MATCH
    currentUser = user
    returns user
    |
    v
Main prints: "Welcome, admin! Role: ADMIN"
```

---

## 12. Key Java Concepts and Where They Are Used

| Concept                  | File(s)                                 | How it is used                                       |
|--------------------------|-----------------------------------------|------------------------------------------------------|
| Classes + encapsulation  | All model classes                       | Private fields, public getters/setters               |
| Constructors + this      | EmergencyRecord subclasses              | `this.title = title` in every constructor            |
| Static members           | DBConnection, DateUtil, PasswordUtil    | Static methods called without creating an instance   |
|                          | EmergencyRecord.STALE_DAYS              | One shared value for all record objects              |
| Inheritance              | EmergencyContact, AssemblyPoint,        | All extend EmergencyRecord                           |
|                          | Checklist, Instruction                  |                                                      |
| Method overriding        | display() in each subclass              | Each provides its own formatted output               |
| Polymorphism             | RecordDAOImpl.mapRow()                  | EmergencyRecord variable holds any subclass          |
|                          | Main.printRecordList()                  | record.toString() calls the correct version          |
| Abstract class           | EmergencyRecord                         | Cannot be instantiated; forces subclasses            |
| Interface                | Printable, RecordDAO                    | Contracts that enable substitution                   |
| Packages                 | All 6 packages                          | Organizes code by responsibility                     |
| Method overloading       | RecordService.search()                  | Same name, different parameter list                  |
| Arrays + Strings         | Main (menu arrays)                      | String[] for menus; format, split, trim, join        |
| Custom exceptions        | 3 exception classes                     | Checked; each has a specific message                 |
| try-with-resources       | RecordDAOImpl, FileExporter, CsvImporter| Auto-close DB connections, Streams, readers          |
| Collections + generics   | RecordService                           | List<EmergencyRecord>, Map<RecordType, List<...>>    |
| EnumMap                  | RecordService.groupByType()             | Keeps category order: CONTACT, ASSEMBLY, CHECKLIST.. |
| Generic method           | RecordService.filter()                  | `<T> List<T> filter(List<T>, Predicate<T>)`          |
| File write               | FileExporter                            | BufferedWriter writes .txt checklist files           |
| File read                | DBConnection (FileReader)               | Reads .properties config                             |
|                          | CsvImporter (BufferedReader)            | Reads CSV line by line                               |
|                          | FileExporter (Files.readString)         | Reads exported file for display                      |
| Lambdas                  | RecordService.getStaleRecords()         | `r -> r.isStale()`                                   |
|                          | RecordService.groupByType()             | `Comparator.comparing(...)`                          |
| Stream API               | RecordService                           | filter, sorted, collect, Collectors.groupingBy       |
| JDBC                     | RecordDAOImpl, UserDAO                  | PreparedStatement, ResultSet, transactions           |
| CRUD                     | RecordDAOImpl                           | add, findAll/findById, update, delete                |

---

## 13. Configuration Files Explained

### db.properties (project root -- NOT committed to git)

```properties
db.url=jdbc:mysql://localhost:3306/emergency_kit?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata
db.user=root
db.password=YOUR_MYSQL_PASSWORD_HERE
stale.days=180
```

| Setting                   | Meaning                                                    |
|---------------------------|------------------------------------------------------------|
| `db.url`                  | Where MySQL is, which database, and connection options     |
| `useSSL=false`            | No SSL needed for a local connection                       |
| `allowPublicKeyRetrieval` | Required for MySQL 8.x authentication                      |
| `serverTimezone`          | Prevents timezone-related date shifts (Indian servers)     |
| `db.user`                 | MySQL username                                             |
| `db.password`             | MySQL password -- why this file is excluded from git!      |
| `stale.days`              | Days until a record is considered stale (default 180)      |

Read by: `DBConnection.loadConfig()` -- called lazily on first connection attempt.

### db.properties.example (committed to git)

Identical content but with `db.password=CHANGE_ME`.
Students copy this file to `db.properties` and fill in their real password.

### .gitignore
```
db.properties    <- never commit real passwords
exports/         <- generated files, no need to version-control
*.class          <- compiled bytecode, always regenerated by javac
```

---

## 14. SQL Files Explained

### sql/schema.sql

Creates the database structure. Run this ONCE.

```sql
CREATE DATABASE IF NOT EXISTS emergency_kit;
USE emergency_kit;

-- Drop order: child before parent (foreign key constraint)
DROP TABLE IF EXISTS checklist_items;
DROP TABLE IF EXISTS records;
DROP TABLE IF EXISTS users;

CREATE TABLE users (...);
CREATE TABLE records (...);
CREATE TABLE checklist_items (...);   -- last, because it references records
```

> **WARNING: Running this again DROPS ALL TABLES and ERASES ALL DATA.**

### sql/sample_data.sql

Fills the database with test data. Safe to run multiple times.
Deletes all rows first, then re-inserts.

```sql
USE emergency_kit;
DELETE FROM checklist_items;   -- child first
DELETE FROM records;
DELETE FROM users;

INSERT INTO users (username, password_hash, role) VALUES
    ('admin',  SHA2('admin123', 256), 'ADMIN'),
    ('viewer', SHA2('viewer123', 256), 'VIEWER');

INSERT INTO records (id, type, title, ...) VALUES
    (1, 'CONTACT', 'Campus Security', ...),
    ...;

INSERT INTO checklist_items (record_id, item_text, item_order) VALUES
    (10, 'Sound the fire alarm immediately', 1),
    ...;
```

**Uses relative dates so records never silently expire:**
```sql
DATE_SUB(CURDATE(), INTERVAL 30 DAY)   -- 30 days ago = current (< 180)
DATE_SUB(CURDATE(), INTERVAL 200 DAY)  -- 200 days ago = STALE (> 180)
DATE_SUB(CURDATE(), INTERVAL 420 DAY)  -- very stale
```
Hard-coded dates like `'2024-01-15'` would go stale silently as time passes.
Relative dates always make the same sense no matter when the script runs.

### sample/contacts.csv

Test file for the CSV import feature.
```
title,phone,contact_role,source,last_verified,description
```

4 rows testing different edge cases:
1. Explicit past date -- normal date parsing
2. Blank `last_verified` -- defaults to today
3. Commas inside description -- tests `split(",", 6)` logic
4. Empty trailing description -- tests optional last column

---

## Summary

The complete flow from user to database and back:

```
User types something
    |-> InputHelper reads safely (ONE Scanner, only nextLine, trimmed)
    |-> Main interprets the menu choice
    |-> RecordService / AuthService applies rules and validates
    |-> RecordDAOImpl runs SQL with PreparedStatement
    |-> DBConnection opens a MySQL connection (lazy config load)
    |-> MySQL stores or retrieves data
    |-> Data returns as model objects (EmergencyRecord subclasses)
    |-> Main prints to console (polymorphic record.toString() or display())
    |-> User sees the result
```

**Every layer has ONE responsibility:**

| Layer        | Responsibility                                      |
|--------------|-----------------------------------------------------|
| `model/`     | What data looks like                                |
| `dao/`       | How to store and retrieve it from MySQL             |
| `service/`   | What rules apply and who has permission             |
| `util/`      | Tools that help every other layer                   |
| `exception/` | Named error types with specific friendly messages   |
| `app/`       | What the user sees and interacts with               |

This separation (called "separation of concerns") keeps each class:
- **Short** -- easier to read
- **Focused** -- does one thing well
- **Explainable** -- you can explain any class in a viva without needing to explain them all
