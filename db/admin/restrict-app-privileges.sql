-- Per-table privileges for the application account (database design v1.2).
--
-- Run as root, once, after Flyway has applied the first migration, because privileges can
-- only be granted on tables that exist. Not a Flyway migration: the migration account has
-- no GRANT privilege, and privileges belong to the server, not to the schema.
--
-- Result: the application can read everything, but can change only the tables it is meant
-- to change. Append-only and frozen tables accept INSERT and nothing else.
--
-- Every later migration that adds a table must be followed by a grant for that table.
-- A missing grant shows up as a permission error, which is the safe way to fail.
-- This file contains no passwords.

-- Replace the database-wide write privileges. SELECT stays database-wide.
REVOKE INSERT, UPDATE ON opsflow.* FROM 'opsflow_app'@'localhost';

-- Mutable business tables
GRANT INSERT, UPDATE ON opsflow.users     TO 'opsflow_app'@'localhost';
GRANT INSERT, UPDATE ON opsflow.roles     TO 'opsflow_app'@'localhost';
GRANT INSERT, UPDATE ON opsflow.products  TO 'opsflow_app'@'localhost';
GRANT INSERT, UPDATE ON opsflow.inventory TO 'opsflow_app'@'localhost';
GRANT INSERT, UPDATE ON opsflow.orders    TO 'opsflow_app'@'localhost';
GRANT INSERT, UPDATE ON opsflow.tickets   TO 'opsflow_app'@'localhost';
-- Expired email codes are deleted by a job (once expires_at has passed), so this is the one table with DELETE.
GRANT INSERT, UPDATE, DELETE ON opsflow.email_verification_codes TO 'opsflow_app'@'localhost';

-- Append-only tables: read and insert only
GRANT INSERT ON opsflow.inventory_transactions    TO 'opsflow_app'@'localhost';
GRANT INSERT ON opsflow.ticket_assignment_history TO 'opsflow_app'@'localhost';
GRANT INSERT ON opsflow.ticket_inventory          TO 'opsflow_app'@'localhost';
GRANT INSERT ON opsflow.product_listing_events    TO 'opsflow_app'@'localhost';
GRANT INSERT ON opsflow.inventory_item_events     TO 'opsflow_app'@'localhost';
GRANT INSERT ON opsflow.operation_logs            TO 'opsflow_app'@'localhost';
GRANT INSERT ON opsflow.order_attempts            TO 'opsflow_app'@'localhost';

-- Frozen after the order is created: read and insert only
GRANT INSERT ON opsflow.order_items TO 'opsflow_app'@'localhost';

-- Link rows are created and, later, removed, but never edited.
-- When the permission management feature is built, also run:
--   GRANT DELETE ON opsflow.user_roles TO 'opsflow_app'@'localhost';
--   GRANT DELETE ON opsflow.roles      TO 'opsflow_app'@'localhost';
GRANT INSERT ON opsflow.user_roles TO 'opsflow_app'@'localhost';
