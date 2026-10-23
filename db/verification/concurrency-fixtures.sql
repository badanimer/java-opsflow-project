-- Executable specification of the order and inventory flows in
-- docs/database/database-design.md (database design v1.7).
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
    DELETE FROM product_listing_events;
    DELETE FROM products;
    DELETE FROM inventory_item_events;
    DELETE FROM inventory;
    DELETE FROM user_roles;
    DELETE FROM roles;
    DELETE FROM users;
END//

-- Three users, three warehouse items, and one listed product per item. The inventory ids are
-- deliberately the reverse of the product ids (product 1 is on inventory id 3), so the tests
-- prove that rows are locked in ascending inventory id and not in product id order.
CREATE PROCEDURE seed(IN p_stock INT) SQL SECURITY INVOKER
BEGIN
    CALL reset_data();
    INSERT INTO users (id, username, password_hash, email)
    VALUES (1, 'u1', 'x', 'u1@example.com'), (2, 'u2', 'x', 'u2@example.com'), (3, 'u3', 'x', 'u3@example.com');
    INSERT INTO inventory (id, sku, name, available_quantity, created_by)
    VALUES (1, 'SKU00003', 'Gizmo item', p_stock, 1), (2, 'SKU00002', 'Gadget item', p_stock, 1), (3, 'SKU00001', 'Widget item', p_stock, 1);
    INSERT INTO products (id, inventory_id, name, price, status, listed_at, listed_by)
    VALUES (1, 3, 'Widget', 10.00, 'LISTED', NOW(3), 1), (2, 2, 'Gadget', 5.00, 'LISTED', NOW(3), 1), (3, 1, 'Gizmo', 2.50, 'LISTED', NOW(3), 1);
END//

-- ---------------------------------------------------------------------------------------
-- Stock helpers
-- ---------------------------------------------------------------------------------------

-- Locks the inventory row of every item of an order, one row at a time in ascending
-- inventory id, with SELECT ... FOR UPDATE. Sets p_ok to 0 when any item has not enough
-- available stock. Changes nothing.
CREATE PROCEDURE lock_and_check(IN p_order_id BIGINT, OUT p_ok TINYINT) SQL SECURITY INVOKER
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_inv BIGINT;
    DECLARE v_qty INT;
    DECLARE v_av INT;
    DECLARE cur CURSOR FOR
        SELECT i.id, oi.quantity
        FROM order_items oi JOIN products p ON p.id = oi.product_id JOIN inventory i ON i.id = p.inventory_id
        WHERE oi.order_id = p_order_id
        ORDER BY i.id;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    SET p_ok = 1;
    OPEN cur;
    item_loop: LOOP
        FETCH cur INTO v_inv, v_qty;
        IF done = 1 THEN LEAVE item_loop; END IF;
        SELECT available_quantity INTO v_av FROM inventory WHERE id = v_inv FOR UPDATE;
        IF v_av < v_qty THEN SET p_ok = 0; END IF;
    END LOOP;
    CLOSE cur;
END//

-- Moves the stock of an order and records one inventory transaction per item. The
-- snapshots come from the row read under lock in this same transaction. Must run after
-- lock_and_check in the same transaction. Records one SALE per item.
CREATE PROCEDURE apply_stock_move(IN p_order_id BIGINT, IN p_operator BIGINT) SQL SECURITY INVOKER
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_inv BIGINT;
    DECLARE v_qty INT;
    DECLARE v_av INT;
    DECLARE n_av INT;
    DECLARE cur CURSOR FOR
        SELECT i.id, oi.quantity
        FROM order_items oi JOIN products p ON p.id = oi.product_id JOIN inventory i ON i.id = p.inventory_id
        WHERE oi.order_id = p_order_id
        ORDER BY i.id;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    item_loop: LOOP
        FETCH cur INTO v_inv, v_qty;
        IF done = 1 THEN LEAVE item_loop; END IF;
        SELECT available_quantity INTO v_av FROM inventory WHERE id = v_inv FOR UPDATE;
        SET n_av = v_av - v_qty;
        UPDATE inventory SET available_quantity = n_av, sold_quantity = sold_quantity + v_qty WHERE id = v_inv;
        INSERT INTO inventory_transactions
            (inventory_id, order_id, operator_id, type, quantity, available_before, available_after)
        VALUES (v_inv, p_order_id, p_operator, 'SALE', v_qty, v_av, n_av);
    END LOOP;
    CLOSE cur;
END//

-- ---------------------------------------------------------------------------------------
-- Product check
-- ---------------------------------------------------------------------------------------

-- Reads the row of every product of an order with SELECT ... FOR SHARE, in ascending product id,
-- before anything is written. p_check is OK, INVALID_QUANTITY (not a whole number from 1 to
-- 9999), PRODUCT_DELISTED (missing or not LISTED), or PRODUCT_CHANGED (the price or the name
-- differs from the one the user saw, when the item carries one). Must run inside the order
-- transaction, because the shared locks live until it ends.
CREATE PROCEDURE check_products(IN p_items JSON, OUT p_check VARCHAR(32)) SQL SECURITY INVOKER
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_pid BIGINT;
    DECLARE v_qty INT;
    DECLARE v_seen_price DECIMAL(12,2);
    DECLARE v_seen_name VARCHAR(200);
    DECLARE v_status VARCHAR(16);
    DECLARE v_price DECIMAL(12,2);
    DECLARE v_name VARCHAR(200);
    DECLARE cur CURSOR FOR
        SELECT jt.pid, jt.qty, jt.seen_price, jt.seen_name
        FROM JSON_TABLE(p_items, '$[*]' COLUMNS (
            pid BIGINT PATH '$.product_id',
            qty INT PATH '$.qty',
            seen_price DECIMAL(12,2) PATH '$.price',
            seen_name VARCHAR(200) PATH '$.name')) jt
        ORDER BY jt.pid;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    SET p_check = 'OK';
    OPEN cur;
    item_loop: LOOP
        FETCH cur INTO v_pid, v_qty, v_seen_price, v_seen_name;
        IF done = 1 THEN LEAVE item_loop; END IF;
        IF v_qty IS NULL OR v_qty < 1 OR v_qty > 9999 THEN
            SET p_check = 'INVALID_QUANTITY';
            LEAVE item_loop;
        END IF;
        SET v_status = NULL;
        SELECT status, price, name INTO v_status, v_price, v_name FROM products WHERE id = v_pid FOR SHARE;
        IF v_status IS NULL OR v_status <> 'LISTED' THEN
            SET p_check = 'PRODUCT_DELISTED';
            LEAVE item_loop;
        END IF;
        IF (v_seen_price IS NOT NULL AND v_seen_price <> v_price) OR (v_seen_name IS NOT NULL AND v_seen_name <> v_name) THEN
            SET p_check = 'PRODUCT_CHANGED';
            LEAVE item_loop;
        END IF;
    END LOOP;
    CLOSE cur;
END//

-- ---------------------------------------------------------------------------------------
-- Create order
-- ---------------------------------------------------------------------------------------

-- An order is completed the moment it is placed: one transaction checks the products, inserts the
-- order with its items, checks and deducts the stock, and records one SALE per item.
-- p_items is a JSON array such as [{"product_id":1,"qty":2}]. An item may also carry the price and
-- the name the user saw, as "price" and "name".
-- p_result: COMPLETED, DUPLICATE, PRODUCT_DELISTED, PRODUCT_CHANGED, INVALID_QUANTITY, OUT_OF_STOCK,
-- RETRYABLE (deadlock or lock wait timeout, the whole transaction rolled back), or ERROR.
CREATE PROCEDURE create_order(
    IN p_user BIGINT, IN p_order_no VARCHAR(64), IN p_key VARCHAR(128), IN p_items JSON,
    OUT p_result VARCHAR(32), OUT p_order_id BIGINT) SQL SECURITY INVOKER
proc: BEGIN
    DECLARE v_ok TINYINT DEFAULT 1;
    DECLARE v_check VARCHAR(32) DEFAULT 'OK';
    DECLARE done INT DEFAULT 0;
    DECLARE v_pid BIGINT;
    DECLARE v_sku VARCHAR(64);
    DECLARE v_pname VARCHAR(200);
    DECLARE v_price DECIMAL(12,2);
    DECLARE v_qty INT;
    DECLARE cur_items CURSOR FOR
        SELECT p.id, i.sku, p.name, p.price, jt.qty
        FROM JSON_TABLE(p_items, '$[*]' COLUMNS (product_id BIGINT PATH '$.product_id', qty INT PATH '$.qty')) jt
        JOIN products p ON p.id = jt.product_id
        JOIN inventory i ON i.id = p.inventory_id
        ORDER BY p.id;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
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
    -- Read every product row under a shared lock, in ascending product id, before writing anything.
    CALL check_products(p_items, v_check);
    IF v_check <> 'OK' THEN
        ROLLBACK;
        SET p_result = v_check;
        SET p_order_id = NULL;
        LEAVE proc;
    END IF;

    INSERT INTO orders (order_no, user_id, total_amount, item_count, idempotency_key)
    VALUES (p_order_no, p_user, 0, 0, p_key);
    SET p_order_id = LAST_INSERT_ID();

    -- The SKU, name, and price are copied by a plain read (a cursor), never by INSERT ... SELECT from
    -- inventory: that would take shared locks on the inventory rows before the exclusive locks below,
    -- and two orders listing the same items in opposite order would deadlock on the lock upgrade.
    SET done = 0;
    OPEN cur_items;
    copy_loop: LOOP
        FETCH cur_items INTO v_pid, v_sku, v_pname, v_price, v_qty;
        IF done = 1 THEN LEAVE copy_loop; END IF;
        INSERT INTO order_items (order_id, product_id, product_sku, product_name, unit_price, quantity, subtotal)
        VALUES (p_order_id, v_pid, v_sku, v_pname, v_price, v_qty, v_price * v_qty);
    END LOOP;
    CLOSE cur_items;

    UPDATE orders
    SET total_amount = (SELECT COALESCE(SUM(subtotal), 0) FROM order_items WHERE order_id = p_order_id),
        item_count = (SELECT COUNT(*) FROM order_items WHERE order_id = p_order_id)
    WHERE id = p_order_id;

    CALL lock_and_check(p_order_id, v_ok);
    IF v_ok = 0 THEN
        ROLLBACK;
        SET p_result = 'OUT_OF_STOCK';
        SET p_order_id = NULL;
        LEAVE proc;
    END IF;

    CALL apply_stock_move(p_order_id, p_user);
    COMMIT;
    SET p_result = 'COMPLETED';
END//

-- ---------------------------------------------------------------------------------------
-- Listing and delisting
-- ---------------------------------------------------------------------------------------

-- Lists a warehouse item. The first listing creates the product, and a later one sets it back to
-- LISTED. p_name is optional and defaults to the name of the item. The status of an existing
-- product is read without a lock and changed by a conditional update, so two listings cannot
-- both win. Two first listings meet on the unique key of products(inventory_id): the second
-- waits for the first, then fails with a duplicate key and reports ALREADY_LISTED.
-- p_result: LISTED, ALREADY_LISTED, NOT_FOUND, RETRYABLE, or ERROR.
CREATE PROCEDURE list_product(
    IN p_inventory_id BIGINT, IN p_price DECIMAL(12,2), IN p_name VARCHAR(200), IN p_operator BIGINT,
    OUT p_result VARCHAR(32)) SQL SECURITY INVOKER
proc: BEGIN
    DECLARE v_pid BIGINT DEFAULT NULL;
    DECLARE v_status VARCHAR(16) DEFAULT NULL;
    DECLARE v_live INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR 1062
    BEGIN
        ROLLBACK;
        SET p_result = 'ALREADY_LISTED';
    END;
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
    SELECT id, status INTO v_pid, v_status FROM products WHERE inventory_id = p_inventory_id;

    IF v_pid IS NULL THEN
        INSERT INTO products (inventory_id, name, price, status, listed_at, listed_by)
        SELECT i.id, COALESCE(p_name, i.name), p_price, 'LISTED', NOW(3), p_operator
        FROM inventory i WHERE i.id = p_inventory_id AND i.deleted_at IS NULL;
        IF ROW_COUNT() = 0 THEN
            ROLLBACK;
            SET p_result = 'NOT_FOUND';
            LEAVE proc;
        END IF;
        SET v_pid = LAST_INSERT_ID();
    ELSE
        UPDATE products
        SET status = 'LISTED', price = p_price, name = COALESCE(p_name, name),
            listed_at = NOW(3), listed_by = p_operator, delisted_at = NULL, delisted_by = NULL
        WHERE id = v_pid AND status = 'UNLISTED';
        IF ROW_COUNT() = 0 THEN
            ROLLBACK;
            SET p_result = 'ALREADY_LISTED';
            LEAVE proc;
        END IF;
        -- The product row is held, so the warehouse row comes next in the lock order. A deleted
        -- item cannot be listed again.
        SELECT COUNT(*) INTO v_live FROM inventory WHERE id = p_inventory_id AND deleted_at IS NULL FOR SHARE;
        IF v_live = 0 THEN
            ROLLBACK;
            SET p_result = 'NOT_FOUND';
            LEAVE proc;
        END IF;
    END IF;

    INSERT INTO product_listing_events (product_id, inventory_id, action, name, price, operator_id)
    SELECT id, inventory_id, 'LISTED', name, price, p_operator FROM products WHERE id = v_pid;
    COMMIT;
    SET p_result = 'LISTED';
END//

-- Delists a product and, in the same transaction, cancels every ticket that is OPEN or
-- IN_PROGRESS and linked to its warehouse item, as the SYSTEM user, with the reason
-- PRODUCT_DELISTED:<product id>. Tickets that are already RESOLVED, CLOSED, or CANCELLED stay
-- as they are. The log rows of the cancelled tickets are not modelled here.
-- p_result: DELISTED, ALREADY_DELISTED, NOT_FOUND, RETRYABLE, or ERROR. p_cancelled is the
-- number of tickets cancelled.
CREATE PROCEDURE delist_product(
    IN p_product_id BIGINT, IN p_operator BIGINT, IN p_system_user BIGINT,
    OUT p_result VARCHAR(32), OUT p_cancelled INT) SQL SECURITY INVOKER
proc: BEGIN
    DECLARE v_status VARCHAR(16) DEFAULT NULL;
    DECLARE EXIT HANDLER FOR 1213, 1205
    BEGIN
        ROLLBACK;
        SET p_result = 'RETRYABLE';
        SET p_cancelled = 0;
    END;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_result = 'ERROR';
        SET p_cancelled = 0;
    END;

    SET p_cancelled = 0;
    START TRANSACTION;
    -- Status gate on the product.
    UPDATE products
    SET status = 'UNLISTED', delisted_at = NOW(3), delisted_by = p_operator
    WHERE id = p_product_id AND status = 'LISTED';

    IF ROW_COUNT() = 0 THEN
        SELECT status INTO v_status FROM products WHERE id = p_product_id;
        ROLLBACK;
        IF v_status IS NULL THEN SET p_result = 'NOT_FOUND'; ELSE SET p_result = 'ALREADY_DELISTED'; END IF;
        LEAVE proc;
    END IF;

    INSERT INTO product_listing_events (product_id, inventory_id, action, name, price, operator_id)
    SELECT id, inventory_id, 'DELISTED', name, price, p_operator FROM products WHERE id = p_product_id;

    -- Each ticket moves through its own status gate.
    UPDATE tickets t
    JOIN ticket_inventory ti ON ti.ticket_id = t.id
    JOIN products p ON p.inventory_id = ti.inventory_id
    SET t.status = 'CANCELLED', t.cancel_reason = CONCAT('PRODUCT_DELISTED:', p_product_id),
        t.cancelled_by = p_system_user, t.cancelled_at = NOW(3)
    WHERE p.id = p_product_id AND t.status IN ('OPEN', 'IN_PROGRESS');
    SET p_cancelled = ROW_COUNT();

    COMMIT;
    SET p_result = 'DELISTED';
END//

-- ---------------------------------------------------------------------------------------
-- Adding and deleting warehouse items, and opening a ticket on items
-- ---------------------------------------------------------------------------------------

-- Adds a warehouse item. The program generates the SKU (8 characters, A-Z and 0-9) and passes
-- it in. A starting quantity above zero is recorded as a RESTOCK so that the history explains
-- the stock. p_result: CREATED, NAME_EXISTS, SKU_EXISTS, INVALID_QUANTITY, RETRYABLE, or ERROR.
CREATE PROCEDURE create_item(
    IN p_sku CHAR(8), IN p_name VARCHAR(200), IN p_qty INT, IN p_operator BIGINT,
    OUT p_result VARCHAR(32)) SQL SECURITY INVOKER
proc: BEGIN
    DECLARE v_msg TEXT DEFAULT '';
    DECLARE v_id BIGINT;
    DECLARE EXIT HANDLER FOR 1062
    BEGIN
        GET DIAGNOSTICS CONDITION 1 v_msg = MESSAGE_TEXT;
        ROLLBACK;
        IF v_msg LIKE '%uk_inventory_name_active%' THEN SET p_result = 'NAME_EXISTS'; ELSE SET p_result = 'SKU_EXISTS'; END IF;
    END;
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

    IF p_qty IS NULL OR p_qty < 0 OR p_qty > 999999 THEN
        SET p_result = 'INVALID_QUANTITY';
        LEAVE proc;
    END IF;

    START TRANSACTION;
    INSERT INTO inventory (sku, name, available_quantity, created_by) VALUES (p_sku, p_name, p_qty, p_operator);
    SET v_id = LAST_INSERT_ID();
    IF p_qty > 0 THEN
        INSERT INTO inventory_transactions
            (inventory_id, operator_id, type, quantity, available_before, available_after, reason)
        VALUES (v_id, p_operator, 'RESTOCK', p_qty, 0, p_qty, 'initial stock');
    END IF;
    INSERT INTO inventory_item_events (inventory_id, action, sku, name, quantity, operator_id)
    VALUES (v_id, 'CREATED', p_sku, p_name, p_qty, p_operator);
    COMMIT;
    SET p_result = 'CREATED';
END//

-- Deletes a warehouse item softly. In one transaction it locks the product row (if there is one),
-- then the warehouse row, and refuses when the item is deleted already. Otherwise it delists the
-- product if it is LISTED, cancels every OPEN or IN_PROGRESS ticket linked to the item as the
-- SYSTEM user with the reason ITEM_DELETED:<inventory id>, marks the item deleted, and records the
-- event. The stock left on the item is not touched and no longer matters. The tickets are cancelled
-- after the warehouse row is locked, so a ticket that committed before the lock is seen. A listing
-- that slips in between the first read and the lock makes it roll back and report RETRYABLE.
-- p_result: DELETED, ALREADY_DELETED, NOT_FOUND, RETRYABLE, or ERROR.
CREATE PROCEDURE delete_item(
    IN p_inventory_id BIGINT, IN p_operator BIGINT, IN p_system_user BIGINT,
    OUT p_result VARCHAR(32), OUT p_cancelled INT) SQL SECURITY INVOKER
proc: BEGIN
    DECLARE v_pid BIGINT DEFAULT NULL;
    DECLARE v_pid2 BIGINT DEFAULT NULL;
    DECLARE v_pstatus VARCHAR(16) DEFAULT NULL;
    DECLARE v_sku CHAR(8) DEFAULT NULL;
    DECLARE v_name VARCHAR(200);
    DECLARE v_av INT;
    DECLARE v_del DATETIME(3);
    DECLARE EXIT HANDLER FOR 1213, 1205
    BEGIN
        ROLLBACK;
        SET p_result = 'RETRYABLE';
        SET p_cancelled = 0;
    END;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_result = 'ERROR';
        SET p_cancelled = 0;
    END;

    SET p_cancelled = 0;
    START TRANSACTION;
    SELECT id INTO v_pid FROM products WHERE inventory_id = p_inventory_id;
    IF v_pid IS NOT NULL THEN
        SELECT status INTO v_pstatus FROM products WHERE id = v_pid FOR UPDATE;
    END IF;

    SELECT sku, name, available_quantity, deleted_at
    INTO v_sku, v_name, v_av, v_del
    FROM inventory WHERE id = p_inventory_id FOR UPDATE;
    IF v_sku IS NULL THEN
        ROLLBACK;
        SET p_result = 'NOT_FOUND';
        LEAVE proc;
    END IF;
    IF v_del IS NOT NULL THEN
        ROLLBACK;
        SET p_result = 'ALREADY_DELETED';
        LEAVE proc;
    END IF;
    IF v_pid IS NULL THEN
        SELECT id INTO v_pid2 FROM products WHERE inventory_id = p_inventory_id FOR UPDATE;
        IF v_pid2 IS NOT NULL THEN
            ROLLBACK;
            SET p_result = 'RETRYABLE';
            LEAVE proc;
        END IF;
    END IF;

    IF v_pstatus = 'LISTED' THEN
        UPDATE products SET status = 'UNLISTED', delisted_at = NOW(3), delisted_by = p_operator
        WHERE id = v_pid AND status = 'LISTED';
        INSERT INTO product_listing_events (product_id, inventory_id, action, name, price, operator_id)
        SELECT id, inventory_id, 'DELISTED', name, price, p_operator FROM products WHERE id = v_pid;
    END IF;

    UPDATE tickets t
    JOIN ticket_inventory ti ON ti.ticket_id = t.id
    SET t.status = 'CANCELLED', t.cancel_reason = CONCAT('ITEM_DELETED:', p_inventory_id),
        t.cancelled_by = p_system_user, t.cancelled_at = NOW(3)
    WHERE ti.inventory_id = p_inventory_id AND t.status IN ('OPEN', 'IN_PROGRESS');
    SET p_cancelled = ROW_COUNT();

    UPDATE inventory SET deleted_at = NOW(3), deleted_by = p_operator WHERE id = p_inventory_id;
    INSERT INTO inventory_item_events (inventory_id, action, sku, name, quantity, operator_id)
    VALUES (p_inventory_id, 'DELETED', v_sku, v_name, v_av, p_operator);

    COMMIT;
    SET p_result = 'DELETED';
END//

-- Opens a manual ticket on zero or more warehouse items (p_items is a JSON array of inventory
-- ids), and optionally as a follow-up to the ticket with the number p_follows_no. In one
-- transaction it locks the creator's user row, finds the earlier ticket by its number (it must
-- exist and be RESOLVED or CLOSED, which never changes back, so no lock is needed), inserts the
-- ticket, then locks each item with FOR SHARE in ascending id, refuses the whole ticket when an item
-- is missing or deleted, and writes the links. p_result: CREATED, FOLLOW_NOT_FOUND,
-- FOLLOW_NOT_ALLOWED, ITEM_GONE, RETRYABLE, or ERROR.
CREATE PROCEDURE create_ticket(
    IN p_ticket_no CHAR(8), IN p_creator BIGINT, IN p_items JSON, IN p_follows_no CHAR(8),
    OUT p_result VARCHAR(32)) SQL SECURITY INVOKER
proc: BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_id BIGINT;
    DECLARE v_tid BIGINT;
    DECLARE v_u BIGINT;
    DECLARE v_live INT;
    DECLARE v_fid BIGINT DEFAULT NULL;
    DECLARE v_fstatus VARCHAR(16) DEFAULT NULL;
    DECLARE cur CURSOR FOR
        SELECT jt.item_id FROM JSON_TABLE(p_items, '$[*]' COLUMNS (item_id BIGINT PATH '$')) jt ORDER BY jt.item_id;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
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
    SELECT id INTO v_u FROM users WHERE id = p_creator FOR UPDATE;
    IF p_follows_no IS NOT NULL THEN
        SELECT id, status INTO v_fid, v_fstatus FROM tickets WHERE ticket_no = p_follows_no;
        IF v_fid IS NULL THEN
            ROLLBACK;
            SET p_result = 'FOLLOW_NOT_FOUND';
            LEAVE proc;
        END IF;
        IF v_fstatus NOT IN ('RESOLVED', 'CLOSED') THEN
            ROLLBACK;
            SET p_result = 'FOLLOW_NOT_ALLOWED';
            LEAVE proc;
        END IF;
    END IF;
    INSERT INTO tickets (ticket_no, created_by, type, source, title, description, follows_ticket_id)
    VALUES (p_ticket_no, p_creator, 'INVENTORY_EXCEPTION', 'MANUAL', 'Manual ticket', 'Opened by a person on warehouse items.', v_fid);
    SET v_tid = LAST_INSERT_ID();

    SET done = 0;
    OPEN cur;
    item_loop: LOOP
        FETCH cur INTO v_id;
        IF done = 1 THEN LEAVE item_loop; END IF;
        SELECT COUNT(*) INTO v_live FROM inventory WHERE id = v_id AND deleted_at IS NULL FOR SHARE;
        IF v_live = 0 THEN
            CLOSE cur;
            ROLLBACK;
            SET p_result = 'ITEM_GONE';
            LEAVE proc;
        END IF;
        INSERT INTO ticket_inventory (ticket_id, inventory_id) VALUES (v_tid, v_id);
    END LOOP;
    CLOSE cur;
    COMMIT;
    SET p_result = 'CREATED';
END//
-- ---------------------------------------------------------------------------------------
-- Negative control
-- ---------------------------------------------------------------------------------------

-- Deliberately WRONG: reads the stock with a plain SELECT and no lock, then writes. Exists
-- only to show that the tests detect stale snapshots. Never copy this pattern.
CREATE PROCEDURE sale_unsafe(IN p_order_id BIGINT, IN p_inv BIGINT, IN p_qty INT) SQL SECURITY INVOKER
BEGIN
    DECLARE v_av INT;
    START TRANSACTION;
    SELECT available_quantity INTO v_av FROM inventory WHERE id = p_inv;
    DO SLEEP(0.4);
    UPDATE inventory
    SET available_quantity = available_quantity - p_qty, sold_quantity = sold_quantity + p_qty
    WHERE id = p_inv;
    INSERT INTO inventory_transactions
        (inventory_id, order_id, operator_id, type, quantity, available_before, available_after)
    VALUES (p_inv, p_order_id, 1, 'SALE', p_qty, v_av, v_av - p_qty);
    COMMIT;
END//

DELIMITER ;
