-- OpsFlow database design v1.11: limits on the requests for an email code.
--
-- A request for an email code can make the application send a mail, so one client must not be able
-- to request codes for many addresses and use the mail service for spam. The 60-second rule in
-- email_verification_codes limits one email address only. This table counts the accepted requests
--   * of one client address in one hour: scope 'IP:<address>', window_start the start of the hour;
--   * of the whole system in one day:    scope 'ALL',          window_start the start of the day
--     (midnight in the configured zone, stored in UTC like every time).
-- A request adds 1 to both rows in the transaction that stores the code, with
-- INSERT ... ON DUPLICATE KEY UPDATE, and is refused when a count passes its limit. The row lock of
-- that statement makes two requests at the same moment wait for each other, so a limit cannot be
-- passed by a race. The rows hold no email address and no code.
--
-- A row is of no use once its window is over. The cleanup job of the application deletes old rows,
-- so the application account needs DELETE on this table (see db/admin/restrict-app-privileges.sql).

CREATE TABLE email_code_request_counts (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    scope         VARCHAR(64) NOT NULL,
    window_start  DATETIME(3) NOT NULL,
    request_count INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_email_code_request_counts_window UNIQUE (scope, window_start),
    KEY idx_email_code_request_counts_window_start (window_start),
    CONSTRAINT chk_email_code_request_counts_count CHECK (request_count > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
