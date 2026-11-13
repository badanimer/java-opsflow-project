package com.opsflow.common.audit;

/**
 * The areas of the audit page. Every row of {@code operation_logs} is filed under one of them, and the
 * database accepts no other value ({@code chk_operation_logs_category}). See Categories in
 * docs/database/database-design.md.
 */
public enum AuditCategory {

	ACCOUNT, ROLE, TICKET, STOCK, ITEM, PRODUCT

}
