# Offline Emergency Information Kit

A console (text-menu) Java application that stores institution-approved emergency information in a local MySQL database for fully offline access.

---

## Prerequisites

- **JDK 21** (or any JDK 17+)
- **Apache NetBeans IDE** (tested with NetBeans 19+)
- **MySQL Server 8.x** running locally
- **MySQL Connector/J** JAR (e.g. `mysql-connector-j-8.x.x.jar`)

---

## Setup Instructions

### 1. Set up the database

Open MySQL Workbench or your MySQL client and run the SQL scripts **in order**:

```
1. sql/schema.sql      -- Creates the database and tables
2. sql/sample_data.sql -- Inserts sample records and users
```

> **WARNING: Running `schema.sql` again will DROP all tables and erase all data.** Only run it once for initial setup.

`sample_data.sql` is safe to run multiple times (it deletes and re-inserts data).

### 2. Configure the database connection

Copy `db.properties.example` to `db.properties` in the project root:

```
db.url=jdbc:mysql://localhost:3306/emergency_kit?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata
db.user=root
db.password=YOUR_MYSQL_PASSWORD_HERE
stale.days=180
```

Replace `YOUR_MYSQL_PASSWORD_HERE` with your actual MySQL root password.

> **Note:** `db.properties` is listed in `.gitignore` and should NOT be committed to git.

### 3. Add the MySQL Connector JAR to NetBeans

1. Right-click the project in NetBeans → **Properties**
2. Go to **Libraries** → **Compile** tab → click **Add JAR/Folder**
3. Browse to your `mysql-connector-j-X.X.X.jar` file and add it

### 4. Running from NetBeans

The app uses **relative paths** for `db.properties`, `exports/`, and `sample/contacts.csv`. NetBeans runs from the **project root directory**, so these paths work automatically when you click **Run**.

---

## Sample Login Credentials

| Username | Password   | Role   |
|----------|------------|--------|
| admin    | admin123   | ADMIN  |
| viewer   | viewer123  | VIEWER |

---

## Features

- **Search records** across title, description, source, phone, and checklist items
- **Browse by category** (Contacts, Assembly Points, Checklists, Instructions)
- **Stale records report** (shows records not verified in the last N days)
- **Export checklists** to formatted `.txt` files
- **Admin operations**: Add, Update, Delete, Re-verify, CSV Import (with duplicate detection)

---

## Package Overview

```
src/com/emergencykit/
  app/        - Main.java (menu application)
  dao/        - Database access (RecordDAO, RecordDAOImpl, UserDAO)
  exception/  - Custom exceptions
  model/      - Data model classes (EmergencyRecord, subclasses, enums)
  service/    - Business logic (RecordService, AuthService)
  util/       - Helpers (DBConnection, DateUtil, InputHelper, etc.)
```

---

## Known Limitations

1. **Password visible in NetBeans**: The NetBeans output window is not a real terminal (`System.console()` returns null), so the password is visible as you type. In a real terminal the password is hidden. This is a known constraint.

2. **Unsalted SHA-256**: Passwords use SHA-256 without salting. Real applications should use bcrypt or Argon2 with a salt.

3. **STALE_DAYS is not final**: The `EmergencyRecord.STALE_DAYS` field is intentionally `static` and non-`final` so it can be configured from `db.properties` at startup.

4. **Role checks are in the service layer**: `requireAdmin()` is called at the start of every admin operation, so it cannot be bypassed by the menu.

---

## Demo Script

1. Run the app and log in as **viewer** (password: `viewer123`)
2. Search "fire" — should return the fire contact, fire checklist, and fire extinguisher instruction
3. View the fire checklist in full (option 5 with its ID)
4. Export it (option 6) then use "View an exported file" (option 7)
5. Open the Stale Records Report (option 4) — note the `[STALE]` entries
6. Try the Admin Menu (option 8) as viewer — you'll see "Admin access required" (no crash)
7. Logout and log in as **admin** (password: `admin123`)
8. Re-verify a stale record (Admin Menu → Re-verify); rerun stale report to confirm it's gone
9. Add a new contact, update it, then delete it (full CRUD)
10. Import `sample/contacts.csv`; import it again to see duplicate skipping
11. Try invalid input: letters at a number prompt, a bad date like `2026-13-45` — observe no crash

---

## SQL Reference

Re-run sample data only (safe):
```sql
SOURCE sql/sample_data.sql;
```

Full reset (erases everything):
```sql
SOURCE sql/schema.sql;
SOURCE sql/sample_data.sql;
```
