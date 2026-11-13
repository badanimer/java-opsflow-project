-- OpsFlow seed data (T-05): the four roles and the built-in SYSTEM identity.
-- It adds rows only; no table changes. See Authority over roles and accounts and
-- The SYSTEM identity in docs/database/database-design.md.
--
-- The integration tests empty every table before each test and then run this file again
-- (MySqlIntegrationTest), so it must stay a list of plain INSERT statements.

-- ADMIN and MANAGER are protected roles: nobody can delete them or change their code.
-- The permissions of the four roles live in application code, keyed by code.
INSERT INTO roles (code, name, description) VALUES
    ('ADMIN',    'Administrator', 'Full authority, including role definitions and the appointment of managers and administrators'),
    ('MANAGER',  'Manager',       'Runs the daily work and manages the users and roles below manager'),
    ('OPERATOR', 'Operator',      'Places orders, changes stock, and works on tickets'),
    ('VIEWER',   'Viewer',        'Reads everything a manager reads and changes nothing');

-- The SYSTEM identity is the actor of what the program does by itself. It is not a person:
-- it has no role and cannot log in. password_hash holds a marker that is not a hash, so no
-- password can ever match it, and the address is in the reserved domain .invalid.
INSERT INTO users (username, password_hash, email, is_system)
VALUES ('system', '!SYSTEM-CANNOT-LOG-IN', 'system@opsflow.invalid', TRUE);
