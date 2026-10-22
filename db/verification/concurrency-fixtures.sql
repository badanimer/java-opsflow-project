-- Executable specification of the order and inventory flows in
-- docs/database/database-design.md (database design v1.2).
--
-- These procedures exist only to verify the design against a real MySQL server with
-- concurrent sessions. They are loaded into a temporary verification database by
-- run-db-verification.ps1. They are NOT part of the schema and are never migrated.
--
-- Every procedure uses SQL SECURITY INVOKER, so each one runs with the privileges of the
-- account that calls it. That lets the tests prove that the restricted application account
-- is enough for the flows. This file contains no passwords.

DELIMITER //

-- ---------------------------------------------------------------------------------------
-- Test data
-- ---------------------------------------------------------------------------------------

CREATE PROCEDURE reset_data() SQL SECURITY INVOKER
BEGIN
    DELETE FROM operation_logs;
    DELETE FROM email_verification_codes;
    DELETE FROM ticket_comments WHERE corrects_comment_id IS NOT NULL;
    DELETE FROM ticket_comments;
    DELETE FROM ticket_assignment_history;
    DELETE FROM ticket_inventory;
    DELETE FROM tickets WHERE follows_ticket_id IS NOT NULL;
    DELETE FROM tickets;
    DELETE FROM inventory_transactions;
    DELETE FROM order_items;
    DELETE FROM orders;
    DELETE FROM inventory;
    DELETE FROM products;
    DELETE FROM user_roles;
    DELETE FROM roles;
    DELETE FROM users;
END//

-- Three users, three products, and one inventory row per product. The inventory ids are
-- deliberately the reverse of the product ids (product 1 has inventory id 3), so the tests
-- prove that rows are locked in ascending inventory id and not in product id order.
CREATE PROCEDURE seed(IN p_stock INT) SQL SECURITY INVOKER
BEGIN
    CALL reset_data();
    INSERT INTO users (id, username, password_hash, email)
    VALUES (1, 'u1', 'x', 'u1@example.com'), (2, 'u2', 'x', 'u2@example.com'), (3, 'u3', 'x', 'u3@example.com');
    INSERT INTO products (id, sku, name, price)
    VALUES (1, 'P1', 'Widget', 10.00), (2, 'P2', 'Gadget', 5.00), (3, 'P3', 'Gizmo', 2.50);
    INSERT INTO inventory (id, product_id, available_quantity)
    VALUES (1, 3, p_stock), (2, 2, p_stock), (3, 1, p_stock);
END//

-- ---------------------------------------------------------------------------------------
-- Stock helpers
-- ---------------------------------------------------------------------------------------

-- Locks the inventory row of every item of an order, one row at a time in ascending
-- inventory id, with SELECT ... FOR UPDATE. Sets p_ok to 0 when any item cannot be served:
-- in RESERVE mode that means not enough available stock, in any other mode not enough
-- locked stock. Changes nothing.
CREATE PROCEDURE lock_and_check(IN p_order_id BIGINT, IN p_mode VARCHAR(8), OUT p_ok TINYINT) SQL SECURITY INVOKER
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_inv BIGINT;
    DECLARE v_qty INT;
    DECLARE v_av INT;
    DECLARE v_lk INT;
    DECLARE cur CURSOR FOR
        SELECT i.id, oi.quantity
        FROM order_items oi JOIN inventory i ON i.product_id = oi.product_id
        WHERE oi.order_id = p_order_id
        ORDER BY i.id;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    SET p_ok = 1;
    OPEN cur;
    item_loop: LOOP
        FETCH cur INTO v_inv, v_qty;
        IF done = 1 THEN LEAVE item_loop; END IF;
        SELECT available_quantity, locked_quantity INTO v_av, v_lk
        FROM inventory WHERE id = v_inv FOR UPDATE;
        IF p_mode = 'RESERVE' AND v_av < v_qty THEN SET p_ok = 0; END IF;
        IF p_mode <> 'RESERVE' AND v_lk < v_qty THEN SET p_ok = 0; END IF;
    END LOOP;
    CLOSE cur;
END//

-- Moves the stock of an order and records one inventory transaction per item. The
-- snapshots come from the row read under lock in this same transaction. Must run after
-- lock_and_check in the same transaction.
CREATE PROCEDURE apply_stock_move(IN p_order_id BIGINT, IN p_type VARCHAR(16), IN p_operator BIGINT) SQL SECURITY INVOKER
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_inv BIGINT;
    DECLARE v_qty INT;
    DECLARE v_av INT;
    DECLARE v_lk INT;
    DECLARE n_av INT;
    DECLARE n_lk INT;
    DECLARE cur CURSOR FOR
        SELECT i.id, oi.quantity
        FROM order_items oi JOIN inventory i ON i.product_id = oi.product_id
        WHERE oi.order_id = p_order_id
        ORDER BY i.id;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    item_loop: LOOP
        FETCH cur INTO v_inv, v_qty;
        IF done = 1 THEN LEAVE item_loop; END IF;
        SELECT available_quantity, locked_quantity INTO v_av, v_lk
        FROM inventory WHERE id = v_inv FOR UPDATE;
        IF p_type = 'RESERVE' THEN
            SET n_av = v_av - v_qty, n_lk = v_lk + v_qty;
            UPDATE inventory SET available_quantity = n_av, locked_quantity = n_lk WHERE id = v_inv;
        ELSEIF p_type = 'CONFIRM' THEN
            SET n_av = v_av, n_lk = v_lk - v_qty;
            UPDATE inventory SET locked_quantity = n_lk, sold_quantity = sold_quantity + v_qty WHERE id = v_inv;
        ELSE
            SET n_av = v_av + v_qty, n_lk = v_lk - v_qty;
            UPDATE inventory SET available_quantity = n_av, locked_quantity = n_lk WHERE id = v_inv;
        END IF;
        INSERT INTO inventory_transactions
            (inventory_id, order_id, operator_id, type, quantity, available_before, available_after, locked_before, locked_after)
        VALUES (v_inv, p_order_id, p_operator, p_type, v_qty, v_av, n_av, v_lk, n_lk);
    END LOOP;
    CLOSE cur;
END//

-- ---------------------------------------------------------------------------------------
-- Create order
-- ---------------------------------------------------------------------------------------

-- p_items is a JSON array such as [{"product_id":1,"qty":2}]. A negative p_ttl_seconds
-- creates an order whose reservation has already expired, for the timeout tests.
-- p_result: CREATED, DUPLICATE, OUT_OF_STOCK, RETRYABLE (deadlock or lock wait timeout,
-- the whole transaction rolled back), or ERROR.
CREATE PROCEDURE create_order(
    IN p_user BIGINT, IN p_order_no VARCHAR(64), IN p_key VARCHAR(128), IN p_items JSON,
    IN p_ttl_seconds INT, OUT p_result VARCHAR(32), OUT p_order_id BIGINT) SQL SECURITY INVOKER
proc: BEGIN
    DECLARE v_ok TINYINT DEFAULT 1;
    DECLARE EXIT HANDLER FOR 1062
    BEGIN
        ROLLBACK;
        SET p_result = 'DUPLICATE';
        SET p_order_id = NULL;
        SELECT id INTO p_order_id FROM orders WHERE user_id = p_user AND idempotency_key = p_key LIMIT 1;
    END;
    DECLARE EXIT HANDLER FOR 1213, 1205
    BEGIN
        ROLLBACK;
        SET p_result = 'RETRYABLE';
        SET p_order_id = NULL;
    END;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_result = 'ERROR';
        SET p_order_id = NULL;
    END;

    SET p_order_id = NULL;
    -- A repeated submission returns the original order.
    IF p_key IS NOT NULL THEN
        SELECT id INTO p_order_id FROM orders WHERE user_id = p_user AND idempotency_key = p_key LIMIT 1;
        IF p_order_id IS NOT NULL THEN
            SET p_result = 'DUPLICATE';
            LEAVE proc;
        END IF;
    END IF;

    START TRANSACTION;
    INSERT INTO orders (order_no, user_id, status, total_amount, item_count, idempotency_key, reserved_until)
    VALUES (p_order_no, p_user, 'CREATED', 0, 0, p_key, DATE_ADD(NOW(3), INTERVAL p_ttl_seconds SECOND));
    SET p_order_id = LAST_INSERT_ID();

    INSERT INTO order_items (order_id, product_id, product_sku, product_name, unit_price, quantity, subtotal)
    SELECT p_order_id, p.id, p.sku, p.name, p.price, jt.qty, p.price * jt.qty
    FROM JSON_TABLE(p_items, '$[*]' COLUMNS (product_id BIGINT PATH '$.product_id', qty INT PATH '$.qty')) jt
    JOIN products p ON p.id = jt.product_id;

    UPDATE orders
    SET total_amount = (SELECT COALESCE(SUM(subtotal), 0) FROM order_items WHERE order_id = p_order_id),
        item_count = (SELECT COUNT(*) FROM order_items WHERE order_id = p_order_id)
    WHERE id = p_order_id;

    CALL lock_and_check(p_order_id, 'RESERVE', v_ok);
    IF v_ok = 0 THEN
        ROLLBACK;
        SET p_result = 'OUT_OF_STOCK';
        SET p_order_id = NULL;
        LEAVE proc;
    END IF;

    CALL apply_stock_move(p_order_id, 'RESERVE', NULL);
    COMMIT;
    SET p_result = 'CREATED';
END//

-- ---------------------------------------------------------------------------------------
-- Confirm order
-- ---------------------------------------------------------------------------------------

-- p_result: COMPLETED, EXPIRED_CANCELLED, ALREADY_<final status>, NOT_FOUND, RETRY,
-- INCONSISTENT, RETRYABLE, or ERROR.
CREATE PROCEDURE confirm_order(IN p_order_id BIGINT, IN p_operator BIGINT, OUT p_result VARCHAR(32)) SQL SECURITY INVOKER
proc: BEGIN
    DECLARE v_status VARCHAR(32) DEFAULT NULL;
    DECLARE v_until DATETIME(3);
    DECLARE v_ok TINYINT DEFAULT 1;
    DECLARE EXIT HANDLER FOR 1213, 1205
    BEGIN
        ROLLBACK;
        SET p_result = 'RETRYABLE';
    END;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_result = 'ERROR';
    END;

    START TRANSACTION;
    -- Status gate.
    UPDATE orders SET status = 'COMPLETED', finished_at = NOW(3)
    WHERE id = p_order_id AND status = 'CREATED' AND reserved_until > NOW(3);

    IF ROW_COUNT() = 1 THEN
        CALL lock_and_check(p_order_id, 'CONFIRM', v_ok);
        IF v_ok = 0 THEN
            ROLLBACK;
            SET p_result = 'INCONSISTENT';
            LEAVE proc;
        END IF;
        CALL apply_stock_move(p_order_id, 'CONFIRM', p_operator);
        COMMIT;
        SET p_result = 'COMPLETED';
        LEAVE proc;
    END IF;

    -- Zero rows updated: decide by what the order shows.
    SELECT status, reserved_until INTO v_status, v_until FROM orders WHERE id = p_order_id;
    IF v_status IS NULL THEN
        ROLLBACK;
        SET p_result = 'NOT_FOUND';
    ELSEIF v_status <> 'CREATED' THEN
        ROLLBACK;
        SET p_result = CONCAT('ALREADY_', v_status);
    ELSEIF v_until <= NOW(3) THEN
        -- Expired: run the cancel path in this transaction and commit it.
        UPDATE orders SET status = 'CANCELLED', finished_at = NOW(3), status_reason = 'RESERVATION_EXPIRED'
        WHERE id = p_order_id AND status = 'CREATED';
        IF ROW_COUNT() = 1 THEN
            CALL lock_and_check(p_order_id, 'RELEASE', v_ok);
            IF v_ok = 0 THEN
                ROLLBACK;
                SET p_result = 'INCONSISTENT';
                LEAVE proc;
            END IF;
            CALL apply_stock_move(p_order_id, 'RELEASE', p_operator);
            COMMIT;
            SET p_result = 'EXPIRED_CANCELLED';
        ELSE
            ROLLBACK;
            SET p_result = 'ALREADY_FINAL';
        END IF;
    ELSE
        ROLLBACK;
        SET p_result = 'RETRY';
    END IF;
END//

-- ---------------------------------------------------------------------------------------
-- Cancel, time out, or fail an order
-- ---------------------------------------------------------------------------------------

-- p_status is CANCELLED or FAILED. p_result: the p_status value on success,
-- ALREADY_<final status>, NOT_FOUND, INCONSISTENT, RETRYABLE, or ERROR.
CREATE PROCEDURE cancel_order(
    IN p_order_id BIGINT, IN p_status VARCHAR(16), IN p_reason VARCHAR(255), IN p_operator BIGINT,
    OUT p_result VARCHAR(32)) SQL SECURITY INVOKER
proc: BEGIN
    DECLARE v_status VARCHAR(32) DEFAULT NULL;
    DECLARE v_ok TINYINT DEFAULT 1;
    DECLARE EXIT HANDLER FOR 1213, 1205
    BEGIN
        ROLLBACK;
        SET p_result = 'RETRYABLE';
    END;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_result = 'ERROR';
    END;

    START TRANSACTION;
    -- Status gate.
    UPDATE orders SET status = p_status, finished_at = NOW(3), status_reason = p_reason
    WHERE id = p_order_id AND status = 'CREATED';

    IF ROW_COUNT() = 0 THEN
        SELECT status INTO v_status FROM orders WHERE id = p_order_id;
        ROLLBACK;
        IF v_status IS NULL THEN
            SET p_result = 'NOT_FOUND';
        ELSE
            SET p_result = CONCAT('ALREADY_', v_status);
        END IF;
        LEAVE proc;
    END IF;

    CALL lock_and_check(p_order_id, 'RELEASE', v_ok);
    IF v_ok = 0 THEN
        ROLLBACK;
        SET p_result = 'INCONSISTENT';
        LEAVE proc;
    END IF;
    CALL apply_stock_move(p_order_id, 'RELEASE', p_operator);
    COMMIT;
    SET p_result = p_status;
END//

-- Cancels up to p_batch expired orders, each in its own transaction.
CREATE PROCEDURE timeout_job(IN p_batch INT, OUT p_cancelled INT) SQL SECURITY INVOKER
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_id BIGINT;
    DECLARE v_result VARCHAR(32);
    DECLARE cur CURSOR FOR
        SELECT id FROM orders WHERE status = 'CREATED' AND reserved_until < NOW(3)
        ORDER BY reserved_until LIMIT p_batch;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    SET p_cancelled = 0;
    OPEN cur;
    order_loop: LOOP
        FETCH cur INTO v_id;
        IF done = 1 THEN LEAVE order_loop; END IF;
        CALL cancel_order(v_id, 'CANCELLED', 'RESERVATION_TIMEOUT', NULL, v_result);
        IF v_result = 'CANCELLED' THEN SET p_cancelled = p_cancelled + 1; END IF;
    END LOOP;
    CLOSE cur;
END//

-- ---------------------------------------------------------------------------------------
-- Negative control
-- ---------------------------------------------------------------------------------------

-- Deliberately WRONG: reads the stock with a plain SELECT and no lock, then writes. Exists
-- only to show that the tests detect stale snapshots. Never copy this pattern.
CREATE PROCEDURE reserve_unsafe(IN p_order_id BIGINT, IN p_inv BIGINT, IN p_qty INT) SQL SECURITY INVOKER
BEGIN
    DECLARE v_av INT;
    DECLARE v_lk INT;
    START TRANSACTION;
    SELECT available_quantity, locked_quantity INTO v_av, v_lk FROM inventory WHERE id = p_inv;
    DO SLEEP(0.4);
    UPDATE inventory
    SET available_quantity = available_quantity - p_qty, locked_quantity = locked_quantity + p_qty
    WHERE id = p_inv;
    INSERT INTO inventory_transactions
        (inventory_id, order_id, operator_id, type, quantity, available_before, available_after, locked_before, locked_after)
    VALUES (p_inv, p_order_id, NULL, 'RESERVE', p_qty, v_av, v_av - p_qty, v_lk, v_lk + p_qty);
    COMMIT;
END//

DELIMITER ;
