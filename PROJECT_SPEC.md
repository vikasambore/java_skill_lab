# Offline Emergency Information Kit: Build Specification (v2)

> **For the coding agent:** Read this whole file, then build the project exactly as specified. Where this file makes a decision, follow it; do not guess. Work through the build order in Section 12 and make sure the project compiles after every step. Do not add frameworks or libraries beyond those listed.
>
> **v2 note:** this version resolves earlier contradictions and adds explicit rules for input handling, silent bugs, startup failures and file handling (Sections 8 to 10). Those sections are mandatory, not suggestions.

---

## 1. Project summary

A **console (text menu) Java application** that stores institution-approved emergency information in a local MySQL database so it works fully offline. Each record carries its **source / responsible authority** and a **last-verified date**. The app can search, flag stale entries, export printable checklists, and lets only authorised users maintain records.

**Original requirement (verbatim):**
> Store institution-approved emergency contacts, assembly-point descriptions, preparedness checklists, and verified instructions for offline access. Each record should include its source or responsible authority and last-verified date. Features: search information offline; display verification dates; flag stale entries; provide printable checklists; allow authorized maintenance of records.

## 2. Constraints

- **Language:** Java 17 or later (avoid features newer than Java 17).
- **IDE:** Apache NetBeans, standard **Java Application** project (Ant or Maven). Must compile and run from NetBeans.
- **Database:** MySQL running locally. Driver: `mysql-connector-j` (add the JAR to the project Libraries, or as a Maven dependency).
- **No GUI, no Spring, no Hibernate, no Lombok.** Plain JDBC with `PreparedStatement`. The students only studied: OOP, packages, methods/overloading, arrays/strings, exceptions, collections, generics, file handling, lambdas, basic Stream API, JDBC and CRUD.
- **Code style:** Readable and beginner-friendly, with short comments explaining *why*. Students must be able to explain every class in a viva.
- **Output characters:** Use **plain ASCII only** in all console output and exported files (the NetBeans output window and Windows consoles mangle symbols). The stale marker is the text **`[STALE]`** everywhere. Never use the warning-sign symbol.
- **Package root:** `com.emergencykit`
- **Working directory:** The app expects to be run from the **project root** (NetBeans Run does this). `db.properties`, `exports/` and `sample/` are relative paths. Say so in the README.

## 3. Syllabus concepts that MUST appear in the code

| Concept | Where to use it |
|---|---|
| Classes, constructors, `this`, encapsulation | All model classes: private fields, getters/setters, constructors using `this.x = x` |
| Static members | `DBConnection.getConnection()`, `DateUtil` helpers, `EmergencyRecord.STALE_DAYS` (a static, configurable field set at startup; it is deliberately **not** `final`) |
| Inheritance, overriding, polymorphism | Subclasses of `EmergencyRecord` override `display()`; lists hold mixed types through `EmergencyRecord` references |
| Abstraction, interfaces | Abstract `EmergencyRecord`; interfaces `RecordDAO` and `Printable` |
| Packages | The layout in Section 4 |
| Method overloading | `RecordService.search(String keyword)` and `RecordService.search(String keyword, RecordType type)` |
| Arrays and strings | `String[]` for menu options; `String.format`, `StringBuilder`, `trim`, `toLowerCase`, `split` |
| Exception handling and custom exceptions | Three custom exceptions; try/catch and try-with-resources around JDBC and file I/O |
| Collections and generics | `List`, `Map<RecordType, List<EmergencyRecord>>` for grouping by type; one small generic method (`RecordService.filter`) |
| File handling (read AND write) | Write: export checklist to `.txt`. Read: load `db.properties`, import contacts from CSV, view an exported file |
| Lambdas and Streams | Stale filter, keyword filter, sort by date, group by type |
| JDBC | `DBConnection`, `RecordDAOImpl`, `UserDAO` with `PreparedStatement` and `ResultSet` |
| CRUD | Add, view, update, delete on records |

## 4. Package and file layout

```
src/com/emergencykit/
├── model/
│   ├── EmergencyRecord.java      (abstract)
│   ├── EmergencyContact.java
│   ├── AssemblyPoint.java
│   ├── Checklist.java            (implements Printable)
│   ├── ChecklistItem.java
│   ├── Instruction.java
│   ├── Printable.java            (interface)
│   ├── RecordType.java           (enum)
│   ├── Role.java                 (enum)
│   └── User.java
├── dao/
│   ├── RecordDAO.java            (interface)
│   ├── RecordDAOImpl.java
│   └── UserDAO.java
├── service/
│   ├── RecordService.java
│   └── AuthService.java
├── exception/
│   ├── RecordNotFoundException.java
│   ├── UnauthorizedException.java
│   └── InvalidDataException.java
├── util/
│   ├── DBConnection.java
│   ├── DateUtil.java
│   ├── PasswordUtil.java
│   ├── InputHelper.java          (all console input; see Section 8)
│   ├── CsvImporter.java
│   └── FileExporter.java
└── app/
    └── Main.java
db.properties                      (project root, NOT committed to git)
db.properties.example              (committed template with a dummy password)
.gitignore                         (must list db.properties and exports/)
sql/schema.sql
sql/sample_data.sql
exports/                           (created at runtime)
sample/contacts.csv
README.md
```

All custom exceptions extend `Exception` (checked), each with a message constructor.

## 5. Database design

Database name: `emergency_kit`.

### 5.1 `sql/schema.sql`

```sql
CREATE DATABASE IF NOT EXISTS emergency_kit;
USE emergency_kit;

-- WARNING: re-running this script DROPS the tables and erases all data.
DROP TABLE IF EXISTS checklist_items;
DROP TABLE IF EXISTS records;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash CHAR(64)     NOT NULL,            -- SHA-256 hex, lowercase
    role          ENUM('ADMIN','VIEWER') NOT NULL
);

CREATE TABLE records (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    type                  ENUM('CONTACT','ASSEMBLY','CHECKLIST','INSTRUCTION') NOT NULL,
    title                 VARCHAR(150) NOT NULL,
    description           TEXT,
    phone                 VARCHAR(30)  NULL,        -- CONTACT only
    contact_role          VARCHAR(100) NULL,        -- CONTACT only
    location_description  TEXT         NULL,        -- ASSEMBLY only
    source                VARCHAR(150) NOT NULL,    -- responsible authority
    last_verified         DATE         NOT NULL
);

CREATE TABLE checklist_items (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    record_id   INT NOT NULL,
    item_text   VARCHAR(255) NOT NULL,
    item_order  INT NOT NULL,
    FOREIGN KEY (record_id) REFERENCES records(id) ON DELETE CASCADE
);
```

One `records` table holds all four types. Columns that don't apply to a type stay `NULL`. `INSTRUCTION` uses `description` for its steps (multi-line text, lines separated by `\n`).

### 5.2 `sql/sample_data.sql`

Requirements for this script:

1. Starts with `USE emergency_kit;`.
2. Is **safe to run more than once**: its first statements are `DELETE FROM checklist_items; DELETE FROM records; DELETE FROM users;`, followed by the inserts.
3. Inserts records with **explicit `id` values** (1, 2, 3, ...) so `checklist_items.record_id` can reference known ids.
4. Uses **relative dates**, never hard-coded calendar dates, so the data does not silently change meaning over time or break when the lab PC clock differs:
   - Recent records: `DATE_SUB(CURDATE(), INTERVAL 5 DAY)` up to `INTERVAL 90 DAY`.
   - Stale records (at least 4): `DATE_SUB(CURDATE(), INTERVAL 200 DAY)`, `250`, `320`, `420`.
5. At least **14 records** covering all four types.

Required users (hash with MySQL `SHA2`, which matches the Java hash output from `PasswordUtil`):

```sql
INSERT INTO users (username, password_hash, role) VALUES
 ('admin',  SHA2('admin123', 256),  'ADMIN'),
 ('viewer', SHA2('viewer123', 256), 'VIEWER');
```

Required content (realistic for a college campus):
- **Contacts:** Campus Security, Fire Station, Ambulance/Medical Centre, Hospital Emergency, Disaster Management Cell, Hostel Warden Emergency. Each with a phone and a `contact_role`.
- **Assembly points:** Main Ground, Library Front Lawn, Hostel Parking Area, each with a clear location description.
- **Checklists (4 to 6 ordered items each):** Fire Evacuation, Earthquake Preparedness, Medical Emergency.
- **Instructions:** How to use a fire extinguisher, Basic CPR steps, What to do during a lockdown. Steps stored as multi-line text.
- `source` values such as "Campus Security Office", "Fire & Rescue Services", "Health Centre", "Disaster Management Cell".

### 5.3 `sample/contacts.csv`

Header exactly: `title,phone,contact_role,source,last_verified,description`

Contains 4 data rows to exercise the importer:
- one normal row with a blank `last_verified` (blank means **today**),
- one row with an explicit past date in `yyyy-MM-dd` format,
- one row whose `description` **contains commas** (for example "Call first, then email, then visit"),
- one row with an **empty trailing description**.

Do not use future dates (they are rejected by validation).

## 6. Configuration

`db.properties` (project root) and an identical `db.properties.example` with the password replaced by `CHANGE_ME`:

```
db.url=jdbc:mysql://localhost:3306/emergency_kit?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata
db.user=root
db.password=CHANGE_ME
stale.days=180
```

Rules:
- `.gitignore` must contain `db.properties` and `exports/`.
- `stale.days` is read at startup into the static field `EmergencyRecord.STALE_DAYS`. A value is **invalid if it is non-numeric or <= 0**; in that case use the default 180 and print a one-line warning.
- **Do not load the file in a `static {}` block.** If the file is missing, a static initialiser would throw `ExceptionInInitializerError`, which nothing catches. Load lazily inside a method (`DBConnection.loadConfig()`), and throw a normal checked exception (`IOException` or `InvalidDataException`) with a message telling the user to copy `db.properties.example` to `db.properties`. `Main` catches it, prints the message, and exits cleanly.

## 7. Class specifications

### 7.1 `model`

**`RecordType` (enum):** `CONTACT, ASSEMBLY, CHECKLIST, INSTRUCTION`. Add `label()` returning a friendly name ("Emergency Contact", ...).

**`Role` (enum):** `ADMIN, VIEWER`.

**`Printable` (interface):** `String toPrintableText();`

**`EmergencyRecord` (abstract):**
- Private fields: `int id`, `RecordType type`, `String title`, `String description`, `String source`, `LocalDate lastVerified`.
- `public static int STALE_DAYS = 180;` (static configurable field, not final, set at startup).
- **Constructors:** a protected constructor `EmergencyRecord(RecordType type)` and a protected all-common-fields constructor that also takes `type`. **There is no public no-arg constructor without a type.** Every subclass constructor passes its own fixed type to `super(...)` (`EmergencyContact` passes `RecordType.CONTACT`, etc.), so `type` can never be `null` when inserting.
- Getters and setters for everything (no setter for `type`; a record's type never changes).
- `public boolean isStale()`: true if `lastVerified` is **before** `LocalDate.now().minusDays(STALE_DAYS)`. A record verified exactly `STALE_DAYS` days ago is therefore **not** stale (mention this in the viva).
- `public long daysSinceVerified()`: `ChronoUnit.DAYS.between(lastVerified, LocalDate.now())`. Never display a negative number; if it is negative (clock skew), show 0.
- `public abstract String display()`: multi-line formatted description.
- Protected helper `String commonFooter()` returning `Source: ...`, `Last verified: dd MMM yyyy (N days ago)`, and the line `[STALE] needs re-verification` when stale.

**`EmergencyContact`**: adds `phone`, `contactRole`. `display()` shows title, role, phone, footer.

**`AssemblyPoint`**: adds `locationDescription`. `display()` shows title, location, footer.

**`Instruction`**: no extra fields. `display()` shows title, steps from `description`, footer.

**`ChecklistItem`**: `id`, `itemText`, `itemOrder`; constructor, getters/setters, `toString()`.

**`Checklist`** (extends `EmergencyRecord`, implements `Printable`): adds `List<ChecklistItem> items` (initialised to an empty `ArrayList`), `addItem(String text)` (assigns the next order number), `getItems()`, `clearItems()`, `removeItem(int index)`. `display()` shows numbered items with `[ ]` and the footer. `toPrintableText()` is described in 7.5.

**`User`**: `id`, `username`, `passwordHash`, `Role role`; getters; `isAdmin()`.

### 7.2 `exception`

`RecordNotFoundException(String)`, `UnauthorizedException(String)`, `InvalidDataException(String)`.

### 7.3 `util`

**`DBConnection`**
- `static Connection getConnection() throws SQLException, IOException` (or a wrapped checked exception): loads config lazily on first call and caches the `Properties`; returns a **new connection each call**; callers close with try-with-resources.
- `static void loadConfig() throws IOException`: reads `db.properties` with `FileReader` in try-with-resources, and applies `stale.days`.
- `static void testConnection() throws SQLException, IOException`: used by `Main` at startup.

**`DateUtil`** (static helpers)
- `format(LocalDate)` returns `dd MMM yyyy`.
- `parse(String)` accepts `yyyy-MM-dd` and **must catch `DateTimeParseException`** and rethrow `InvalidDataException("Invalid date '...'. Use yyyy-MM-dd, e.g. 2026-10-27")`. It must also reject real-calendar errors such as `2026-13-45` (the same exception covers it).
- `today()`.

**`PasswordUtil`**
- `static String hash(String plain)`: SHA-256 via `MessageDigest`, input bytes from `plain.getBytes(StandardCharsets.UTF_8)`, output as lowercase hex built with `String.format("%02x", b)` for each byte. **Never** use `Integer.toHexString(b & 0xff)` (it drops leading zeros, so about 6% of hashes would not match MySQL `SHA2()`). Output is always exactly 64 characters.

**`InputHelper`**: see Section 8.

**`FileExporter`**
- `static Path exportChecklist(Checklist c) throws IOException`: creates `exports/` if needed; file name is `checklist_<id>_<slug>.txt`; writes with `BufferedWriter` in try-with-resources; returns the path.
- `static String slug(String title)`: lowercase, replace every run of characters that are not `[a-z0-9]` with `-`, trim leading/trailing `-`, cut to 40 characters; if the result is empty (for example a non-English title), return `"checklist"`.
- `static List<Path> listExports() throws IOException`: uses `Files.list(...)` **inside try-with-resources** (the returned `Stream` must be closed or the folder stays locked on Windows); returns only regular `.txt` files, sorted by name; returns an empty list if `exports/` does not exist.
- `static String readFile(Path p) throws IOException`: reads the whole file with `Files.readString` or a `BufferedReader`.

**`CsvImporter`**
- Nested static class `ParsedRow { int lineNumber; EmergencyContact contact; }`.
- `static List<ParsedRow> readContacts(String filePath) throws IOException, InvalidDataException`: reads with `BufferedReader` in try-with-resources, skips the header line, **skips blank lines**, and splits each line with **`line.split(",", 6)`** so that (a) commas inside the last column (`description`) stay inside it, and (b) a trailing empty description is kept as an empty string rather than dropped. If a row has fewer than 5 columns after splitting, throw `InvalidDataException("Line N: expected 6 columns")` (a row with exactly 5 columns, missing description, is treated as an empty description). Trim every field. Blank `last_verified` means `DateUtil.today()`; otherwise parse with `DateUtil.parse`, wrapping errors as `Line N: ...`. Sets `type` automatically (contacts). Does **not** write to the database.

### 7.4 `dao`

**`RecordDAO` (interface)**
```java
int add(EmergencyRecord r) throws SQLException, IOException;
int[] addAll(List<EmergencyRecord> records) throws SQLException, IOException;   // single transaction
boolean update(EmergencyRecord r) throws SQLException, IOException;
boolean delete(int id) throws SQLException, IOException;
EmergencyRecord findById(int id) throws SQLException, IOException;               // returns null if not found
List<EmergencyRecord> findAll() throws SQLException, IOException;
List<EmergencyRecord> findByType(RecordType type) throws SQLException, IOException;
List<EmergencyRecord> search(String keyword) throws SQLException, IOException;
List<EmergencyRecord> search(String keyword, RecordType type) throws SQLException, IOException;
boolean markVerified(int id, LocalDate date) throws SQLException, IOException;
boolean contactExists(String title, String phone) throws SQLException, IOException;
```
(The agent may wrap `IOException` into `SQLException` inside `DBConnection` to simplify signatures, as long as it is done consistently.)

Return conventions: `update`, `delete` and `markVerified` return **`false` when no row matched**. `findById` returns `null` when absent. The **service layer** converts these into `RecordNotFoundException` (see 7.5).

**`RecordDAOImpl`**
- **`PreparedStatement` everywhere.** No user input concatenated into SQL.
- **Dates:** write with `ps.setObject(i, localDate)` and read with `rs.getObject("last_verified", LocalDate.class)`. **Never use `java.sql.Date`** (timezone mismatches between the machine and the connection can shift dates by a day).
- `mapRow(ResultSet)` builds the correct subclass from the `type` column (polymorphism).
- For `CHECKLIST` rows, load items with `SELECT ... FROM checklist_items WHERE record_id = ? ORDER BY item_order`.
- `add` of a checklist inserts the record and its items **in one transaction** (`setAutoCommit(false)`, commit, rollback on failure, restore auto-commit in `finally`), using `Statement.RETURN_GENERATED_KEYS`.
- `addAll` inserts every record (and checklist items) in **one transaction**: all or nothing.
- `update` of a checklist replaces its items (delete old, insert current) inside a transaction.
- `delete` relies on `ON DELETE CASCADE`.
- **Search:**
  - Match the keyword against `title`, `description`, `source`, `phone`, `contact_role`, `location_description`, **and** checklist item text (`OR EXISTS (SELECT 1 FROM checklist_items ci WHERE ci.record_id = records.id AND ci.item_text LIKE ? ESCAPE '!')`).
  - **Escape wildcards:** before wrapping in `%...%`, escape the characters `!`, `%` and `_` in the user's keyword by prefixing each with `!`, and use `ESCAPE '!'` on every `LIKE`. Otherwise typing `%` or `_` matches every record.
  - **Parameter binding:** the query has many `?` placeholders. Build the SQL, then bind them with a **loop** over a `List<Object>` (or a loop that sets the same pattern string for each of the N placeholders), never with hand-numbered indexes. The `RecordType` overload appends `AND type = ?` and binds one more parameter.
  - Sort results by `type`, then `title`.

**`UserDAO`**
- `User findByUsername(String username)`: returns `null` if not found.
- `int add(User u)`: optional.

### 7.5 `service`

**`AuthService`**
- `private User currentUser;`
- `User login(String username, String plainPassword) throws UnauthorizedException, SQLException, IOException`: hashes the password with `PasswordUtil`, compares with the stored hash, sets `currentUser`. On failure throws `UnauthorizedException("Invalid username or password")` (same message for unknown user and wrong password).
- `void logout()`, `User getCurrentUser()`, `boolean isLoggedIn()`.
- `void requireAdmin() throws UnauthorizedException`: **checks `currentUser == null` first** and throws `UnauthorizedException("Please log in first")`; then, if the user is not an admin, throws `UnauthorizedException("Admin access required for this action")`. Never dereference a null user.

**`RecordService`** (constructor takes a `RecordDAO` and an `AuthService`)
- `List<EmergencyRecord> getAll()`, `getByType(RecordType)`.
- **Overloaded search:**
  - `List<EmergencyRecord> search(String keyword)`
  - `List<EmergencyRecord> search(String keyword, RecordType type)`
  - Both trim the keyword and throw `InvalidDataException("Please enter a search keyword")` if it is blank.
- `List<EmergencyRecord> getStaleRecords()`: stream filter on `isStale`, sorted by `lastVerified` ascending (oldest first).
- `Map<RecordType, List<EmergencyRecord>> groupByType()`: **must use an `EnumMap`** so categories always print in enum order:
  ```java
  Collectors.groupingBy(EmergencyRecord::getType,
                        () -> new EnumMap<>(RecordType.class),
                        Collectors.toList())
  ```
- `static <T> List<T> filter(List<T> items, Predicate<T> rule)`: small generic helper; used by `getStaleRecords` or the menu.
- `EmergencyRecord getById(int id) throws RecordNotFoundException`: DAO returns `null` becomes `RecordNotFoundException("No record with ID N")`.
- `Checklist getChecklist(int id) throws RecordNotFoundException, InvalidDataException`: fetches by id, then **`instanceof Checklist` check before casting**; if it is not a checklist throw `InvalidDataException("Record N is not a checklist")`. Never cast blindly (no `ClassCastException`).
- **Admin-only methods** (each begins with `authService.requireAdmin()`):
  - `int addRecord(EmergencyRecord r)`: `validate(r)` then DAO add.
  - `void updateRecord(EmergencyRecord r) throws RecordNotFoundException, ...`: `validate(r)`; if `dao.update` returns `false`, throw `RecordNotFoundException`. **Updating does not change `lastVerified`** unless the caller explicitly sets it (see menu flow 7.6).
  - `void deleteRecord(int id)`: if `dao.delete` returns `false`, throw `RecordNotFoundException`. Never report "deleted" when nothing was deleted.
  - `void markVerified(int id)`: sets `last_verified` to today; if `false`, throw `RecordNotFoundException`.
  - `ImportResult importContacts(String csvPath)`: see below.
- **CSV import behaviour:**
  1. `CsvImporter.readContacts` parses all rows first (nothing written yet).
  2. For each `ParsedRow`, call `validate(contact)`; catch `InvalidDataException` and rethrow as `InvalidDataException("Line " + lineNumber + ": " + e.getMessage())`. Any invalid row aborts the whole import (nothing inserted).
  3. Skip rows that duplicate an existing contact (same title and phone, via `contactExists`) **and** duplicates within the same file; count them.
  4. Insert the remaining rows with `dao.addAll` (one transaction).
  5. Return a small `ImportResult` (nested class with `imported` and `skipped` counts). The menu prints "Imported X, skipped Y duplicates".
- **`validate(EmergencyRecord r)`** (private; throws `InvalidDataException` with a specific message each time):
  - `title`: not blank, **max 150 chars**.
  - `source`: not blank, **max 150 chars**.
  - `description`: max 5000 chars (the column is `TEXT`); for `INSTRUCTION` records it must be non-blank.
  - `lastVerified`: not null, **not in the future**.
  - `CONTACT`: `phone` must match the regex `[0-9+\-() ]{3,30}`; `contactRole` max 100 chars.
  - `ASSEMBLY`: `locationDescription` not blank, max 5000 chars.
  - `CHECKLIST`: at least 1 item; each item not blank and **max 255 chars**.

**Printable checklist format** (`Checklist.toPrintableText()`): ASCII only, 46 columns wide.
```
==============================================
 FIRE EVACUATION CHECKLIST
==============================================
 Source        : Campus Security Office
 Last verified : 12 Aug 2026
 Status        : CURRENT            (or: [STALE] verify before use)
----------------------------------------------
 [ ] 1. First item
 [ ] 2. Second item
----------------------------------------------
 Printed on    : 27 Oct 2026
==============================================
```
Use `StringBuilder` and `String.format`. Add a private helper `fit(String text, int width)` that **truncates with `...`** (or wraps onto continuation lines for checklist items) so long titles, sources or items never overflow the box.

### 7.6 `app.Main`

Console menu application. Menu option labels are held in `String[]` arrays. Uses `InputHelper` for **all** input (Section 8).

**Startup**
1. Create the single shared `InputHelper` (which owns the one `Scanner`).
2. Call `DBConnection.testConnection()` inside try/catch. On failure print a helpful message (is MySQL running? does `db.properties` exist and have the right password?) and exit cleanly.
3. Run the whole program inside `try { ... } catch (Exception e)` as a **safety net** (Section 8): the user must never see a stack trace.

**Login:** username and password, **3 attempts**, then exit. Login is required (no guest mode). Password is visible as typed in NetBeans (see limitations, Section 10).

**Main menu**
1. Search records (keyword, then the optional type filter)
2. Browse by category
3. View all records (grouped by type via `groupByType()`, in enum order)
4. Stale records report (oldest first, with days since verified)
5. View a record in full (by ID)
6. Export a checklist to a text file
7. View an exported file (numbered list, see Section 10)
8. Admin menu (visible to everyone; a viewer who selects it gets the `UnauthorizedException` message, on purpose, so it can be demonstrated)
9. Logout / Exit

**Admin menu** (calls `requireAdmin()` on entry)
1. **Add record:** ask the type first, then type-specific fields. Dates: Enter defaults to today. For a checklist, prompt for items until a blank line. For an instruction, enter steps line by line until a blank line (stored joined with `\n`).
2. **Update record:** show current values; **pressing Enter keeps the old value** for each simple field (title, description, source, phone, role, location). The record type cannot be changed.
   - **Checklist items** use a sub-menu: (1) keep items as they are, (2) re-enter all items (clears and re-asks until a blank line), (3) add one item, (4) remove one item (choose by number).
   - **Instruction steps:** Enter keeps the old steps; otherwise re-enter all steps until a blank line.
   - **An update does not change `last_verified`.** After the edit, ask separately: `Mark as re-verified today? (y/n)`. Only `y` sets the date to today.
3. **Delete record:** ask for confirmation `(y/n)`; the service throws `RecordNotFoundException` if the id is absent.
4. **Re-verify record:** sets last verified to today.
5. **Import contacts from CSV:** prompt for the path; Enter defaults to `sample/contacts.csv`; surrounding double quotes are stripped (Section 8). Print `Imported X, skipped Y duplicates`.
6. Back

**Search type filter (exact behaviour):** after the keyword, show `Filter by type? 1) Contact 2) Assembly 3) Checklist 4) Instruction (Enter = all types)`. Enter means all types and calls `search(keyword)`. A number 1 to 4 calls `search(keyword, type)`. Anything else (`abc`, `7`, `0`) prints "Invalid choice" and asks again (do not silently fall back to all types).

**Display rules**
- List views, one line per record: `[ID] TYPE | Title | Verified: dd MMM yyyy | [STALE]` (the `[STALE]` flag only when stale).
- Full view uses `record.display()` (polymorphic call).
- No results: print "No matching records found." Otherwise print the result count.

## 8. Input handling rules (mandatory)

All console input goes through **`util/InputHelper`**, constructed once and passed around (or stored in a static field of `Main`). Rules:

1. **One shared `Scanner(System.in)`**, created exactly once for the whole program. **Never** create a `Scanner` inside a method and **never** put it in a try-with-resources (closing it closes `System.in` and every later read fails). Do not close it until the program exits.
2. **Only `nextLine()` is ever used.** Never call `nextInt()`, `nextDouble()` or `next()` anywhere (they leave the newline in the buffer and break the next read). Numbers are read as a line and parsed.
3. **Trim before parsing:** `Integer.parseInt(line.trim())`.
4. `InputHelper` methods (each loops until valid or until a defined default applies):
   - `String readLine(String prompt)`: trimmed line, with **surrounding double quotes stripped** (so Windows "Copy as path" works).
   - `String readRequired(String prompt)`: re-prompts if blank.
   - `String readOptional(String prompt)`: returns `""` on Enter (used for "keep old value").
   - `int readInt(String prompt, int min, int max)`: re-prompts on non-numeric or out-of-range input with a message such as `Enter a number between 1 and 9`.
   - `boolean readYesNo(String prompt)`: accepts `y`/`yes`/`n`/`no` (case-insensitive); anything else re-prompts.
   - `LocalDate readDate(String prompt, LocalDate defaultValue)`: Enter returns the default (today); otherwise `DateUtil.parse`, and on `InvalidDataException` prints the message and re-prompts.
   - `List<String> readMultiLine(String prompt)`: reads lines until a blank line.
5. **Every** prompt uses these helpers: menu choices, record ids, type selection, `y/n`, login fields, dates, CSV path. There are no raw reads elsewhere.
6. **Menu `switch` statements always have a `default:` case** that prints `Invalid choice. Please try again.` (covers `0`, `99`, `-1`).
7. **End of input:** if `nextLine()` throws `NoSuchElementException` (Ctrl+D / Ctrl+Z, or closed input), `InputHelper` lets it propagate as an unchecked exception and `Main`'s outer catch prints "Input closed. Goodbye." and exits cleanly. No stack trace.
8. **Safety net:** the main loop body is wrapped so that, after the specific catches (`InvalidDataException`, `UnauthorizedException`, `RecordNotFoundException`, `SQLException`, `IOException`), a final `catch (Exception e)` prints `Something went wrong: <short message>` and returns to the menu (or exits if input is closed). The user never sees a stack trace. Stack traces may be printed only behind a `DEBUG` flag that is off by default.
9. **Never trust the database column size.** Lengths are validated in `RecordService.validate()` (Section 7.5) **before** any insert or update, so the app never relies on MySQL strict mode to catch an over-long value.

## 9. Silent wrong-behaviour checklist (each item must be handled)

| Risk | Required handling |
|---|---|
| SHA-256 hex drops leading zeros | `String.format("%02x", b)` and `StandardCharsets.UTF_8` (7.3) |
| `java.sql.Date` timezone shifts | `setObject(LocalDate)` / `getObject(..., LocalDate.class)`; URL uses `serverTimezone=Asia/Kolkata` (7.4, 6) |
| Hard-coded sample dates go stale | Relative dates with `DATE_SUB(CURDATE(), ...)`; CSV blank date = today (5.2, 5.3) |
| `groupingBy` gives random order | `EnumMap` factory (7.5) |
| `type` null on insert | Subclass constructors set their own type; no typeless public constructor (7.1) |
| DELETE/UPDATE of missing id says success | DAO returns `false`; service throws `RecordNotFoundException` (7.5) |
| `ClassCastException` on export of non-checklist | `instanceof Checklist` check in `getChecklist` (7.5) |
| `requireAdmin()` NPE before login | Null check first (7.5) |
| `%` or `_` in search matches all | Escape with `!` and `ESCAPE '!'` (7.4) |
| Search placeholder index mismatch | Bind in a loop over a parameter list (7.4) |
| Stale boundary | Exactly `STALE_DAYS` days old is **not** stale; documented (7.1) |
| Invalid `stale.days` | Non-numeric or <= 0 falls back to 180 with a warning (6) |
| Negative "days ago" from a wrong clock | Clamp at 0 for display (7.1) |
| Partial CSV import | Parse all, validate all, insert in one transaction (7.5) |
| Duplicate CSV import | Skip same title + phone (7.5) |
| Over-long text, phone format | Length limits and phone regex in `validate()` (7.5) |

## 10. Startup, environment and file safety

**Startup failures**
- No `static {}` config loading (see Section 6). A missing `db.properties` produces a friendly message and a clean exit, not `ExceptionInInitializerError`.
- Relative paths require running from the project root; the README must say so. If `exports/` does not exist, create it.

**Exported-file viewing (path traversal prevention)**
- The "View an exported file" option **never accepts a typed file name or path**. It calls `FileExporter.listExports()`, prints a **numbered list**, and the user picks a number (`readInt`). This prevents input such as `../db.properties` from displaying the database password.
- If the list is empty, print "No exported files yet."

**Printing safety**
- Slug generation per 7.3 so Windows-invalid characters (`/ : ? * " < > |`) never reach a file name.
- Long titles/sources/items are truncated or wrapped (7.5).

**SQL scripts**
- `schema.sql` drops tables. The README must warn that **re-running it erases all data**. `sample_data.sql` is re-runnable (5.2).

**Known limitations to state in the README and viva**
- NetBeans' output window is not a real console, so the **password is visible as typed**. (Optional improvement: if `System.console() != null`, use `readPassword`; otherwise fall back to the visible read.)
- `STALE_DAYS` is a static configurable field, not a true constant.
- Passwords use unsalted SHA-256; real systems should use bcrypt or Argon2.
- Role checks live in the service layer (`requireAdmin()`), so they can't be bypassed by the menu.

## 11. Acceptance criteria (definition of done)

1. The project compiles in NetBeans with the MySQL connector on the classpath and runs from `Main`.
2. `schema.sql` then `sample_data.sql` creates the DB with 14+ records (4+ stale) and 2 users; `sample_data.sql` can be run a second time without errors or duplicates.
3. Login works for `admin/admin123` and `viewer/viewer123` (all 64-character hashes match, including for any other test passwords). Wrong credentials give a clear message; after 3 failures the app exits.
4. Searching "fire" returns the fire contact, fire checklist and fire extinguisher instruction. A word found only in a checklist item finds that checklist. Searching `%` or `_` does **not** return everything.
5. List and detail views show source and verification date; stale records show `[STALE]`; the stale report lists only stale records, oldest first. Category order is always CONTACT, ASSEMBLY, CHECKLIST, INSTRUCTION.
6. Exporting a checklist creates a readable `.txt` in `exports/` (ASCII only, no overflow); option 7 lists exports by number and displays the chosen one. Exporting the ID of a non-checklist shows a message, not an exception.
7. As admin: add, update (including the checklist sub-menu and the separate re-verify question), delete, re-verify and CSV import all work. After re-verifying a stale record it disappears from the stale report. Deleting or updating a non-existent id reports "not found". Importing `sample/contacts.csv` twice the second time reports duplicates skipped.
8. As viewer: choosing the admin menu shows "Admin access required for this action" and does not crash.
9. **Robustness tests, all of which must show only a friendly message and never a stack trace:** letters or blanks at every numeric prompt; leading-space numbers like ` 5`; menu numbers `0`, `-1`, `99`; `abc`/`7` at the search type filter; dates `2026-13-45`, `abc`, and a future date; a 200-character title; phone `abc`; a quoted CSV path; a CSV row with commas in the description; Ctrl+D/Ctrl+Z at any prompt; a missing `db.properties`; MySQL not running.
10. Every concept in Section 3 appears in the code.
11. Each class has a short header comment. No `nextInt()`/`next()` anywhere; exactly one `Scanner` is created.
12. `.gitignore` lists `db.properties` and `exports/`; `db.properties.example` exists.

## 12. Build order for the agent

Compile after each step.

1. **Setup:** package folders; `.gitignore`; `db.properties` and `db.properties.example`; `sql/schema.sql`; `sql/sample_data.sql`; `sample/contacts.csv`; `DBConnection` (lazy config load, `testConnection`).
2. **Enums, interface, models:** `RecordType`, `Role`, `Printable`, `EmergencyRecord` (typed constructors), the three subclasses, `ChecklistItem`, `Checklist`, `User`.
3. **Exceptions and util basics:** three exceptions, `DateUtil` (with `DateTimeParseException` handling), `PasswordUtil` (`%02x`).
4. **DAO:** `RecordDAO`, `RecordDAOImpl` (start with `findAll` and `add`; confirm with a temporary test method that sample rows map to the right subclasses and dates are correct), then the rest including `addAll`, search with escaping and loop binding, `contactExists`; `UserDAO`.
5. **Services:** `AuthService`, then `RecordService` (overloaded search, streams, `EnumMap` grouping, generic filter, `validate`, admin guards, CSV import flow).
6. **File handling:** `FileExporter` (slug, `listExports` with closed stream, `fit` helper used by `Checklist`), `CsvImporter`.
7. **Input and menu:** `InputHelper` first, then `Main` (login, main menu, admin menu, safety-net catches).
8. **Test pass:** walk through every item in Section 11, including all robustness tests in item 9. Fix failures.
9. **README.md:** description; prerequisites (JDK, NetBeans, MySQL, connector JAR); setup (copy `db.properties.example` to `db.properties` and set the password; run `schema.sql` then `sample_data.sql`; add the JAR to Libraries; run from the project root); the **schema re-run warning**; sample logins; package overview; limitations from Section 10; the demo script below.

## 13. Demo script (include in README)

1. Run the app and log in as **viewer**.
2. Search "fire"; open the fire checklist; export it; use "View an exported file" to display it.
3. Open the stale report and show a stale entry with its verification date and `[STALE]` flag.
4. Try the admin menu as viewer to show `UnauthorizedException` handled gracefully.
5. Log out and log in as **admin**.
6. Re-verify the stale record; rerun the stale report to show it is gone.
7. Add a new contact, update it, delete it (full CRUD). Then update a checklist using the item sub-menu.
8. Import `sample/contacts.csv`; import it again to show duplicate skipping.
9. Show a bad input (letters at a number prompt, an invalid date) to demonstrate that nothing crashes.

## 14. Out of scope (do NOT build unless everything above passes)

GUI/Swing/JavaFX, web interfaces, REST APIs, user registration screens, email or SMS, multi-language support, and audit logs. If time remains after all acceptance criteria pass, a simple audit-log table (who changed what, and when) may be added as an optional extra.

## 15. Decisions log (resolved contradictions from v1)

- Overloaded search signature is `search(String)` and `search(String, RecordType)` everywhere (service and DAO).
- Grouping type is `Map<RecordType, List<EmergencyRecord>>` backed by an `EnumMap`.
- The stale marker is the ASCII text `[STALE]` everywhere; no symbols.
- `STALE_DAYS` is a static configurable field (non-final), set from `db.properties` at startup.
- Updating a record does not change `last_verified`; the admin is asked separately whether to mark it re-verified.
- Checklist items on update: keep / re-enter all / add one / remove one.
