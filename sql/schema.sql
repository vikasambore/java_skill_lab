-- schema.sql
-- WARNING: Re-running this script DROPS all tables and erases all data!
-- Run this once to set up the database structure.

CREATE DATABASE IF NOT EXISTS emergency_kit;
USE emergency_kit;

-- Drop tables in correct order (child tables first, then parent)
DROP TABLE IF EXISTS checklist_items;
DROP TABLE IF EXISTS records;
DROP TABLE IF EXISTS users;

-- Table to store app users (admin and viewer)
CREATE TABLE users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash CHAR(64)     NOT NULL,            -- SHA-256 hex, lowercase
    role          ENUM('ADMIN','VIEWER') NOT NULL
);

-- Main table that stores all emergency records (all types in one table)
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

-- Checklist items linked to checklist records
CREATE TABLE checklist_items (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    record_id   INT NOT NULL,
    item_text   VARCHAR(255) NOT NULL,
    item_order  INT NOT NULL,
    FOREIGN KEY (record_id) REFERENCES records(id) ON DELETE CASCADE
);
