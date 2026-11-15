-- OpsFlow database design v1.14: one signed-in device per account.
--
-- A user is signed in on one device at a time. Each successful login, and the sign-up that logs the new
-- user in, writes a new random session id here, in the transaction that holds the user row, and the
-- token issued for it carries the same id. The token check accepts a token only when its id equals the
-- one in this row, so the newest login wins and every older token is refused with SESSION_REPLACED.
--
-- 32 hexadecimal characters: 16 random bytes. NULL for an account that has not logged in since this
-- column was added, and for the SYSTEM identity, which never logs in; a token issued before this
-- migration carries no id and is refused, so every user logs in once again.
--
-- The application account already has UPDATE on users, so no privilege changes.

ALTER TABLE users
    ADD COLUMN session_id CHAR(32) NULL AFTER last_login_at;
