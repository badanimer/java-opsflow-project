package com.opsflow.warehouse;

import org.springframework.stereotype.Service;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.retry.TransactionRetry;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.validation.Limits;
import com.opsflow.common.validation.Quantities;
import com.opsflow.warehouse.StockWriter.Kind;
import com.opsflow.warehouse.WarehouseRepository.ItemRow;

/**
 * Manual stock changes (T-30, T-41). The values are checked first, with no transaction: the quantity
 * through {@link Quantities}, and the reason, which every manual change needs. A correction to the number
 * the person saw changes nothing and is refused here. Then {@link StockWriter} runs through
 * {@link TransactionRetry}.
 */
@Service
public class StockService {

	/** An item after a change, and whether its product is on sale. */
	public record Changed(ItemRow item, boolean listed) {
	}

	private final StockWriter writer;
	private final WarehouseRepository repository;
	private final Quantities quantities;
	private final TransactionRetry retry;

	StockService(StockWriter writer, WarehouseRepository repository, Quantities quantities, TransactionRetry retry) {
		this.writer = writer;
		this.repository = repository;
		this.quantities = quantities;
		this.retry = retry;
	}

	public Changed stockIn(CurrentUser actor, long itemId, Object quantity, String reason, String ipAddress) {
		int checked = quantities.stockChange(quantity);
		return run(actor, itemId, Kind.STOCK_IN, checked, 0, reason(reason), ipAddress);
	}

	public Changed writeOff(CurrentUser actor, long itemId, Object quantity, String reason, String ipAddress) {
		int checked = quantities.stockChange(quantity);
		return run(actor, itemId, Kind.WRITE_OFF, checked, 0, reason(reason), ipAddress);
	}

	/**
	 * @param quantity the number that was counted
	 * @param expectedQuantity the stock the person saw on the screen
	 */
	public Changed correct(CurrentUser actor, long itemId, Object quantity, Object expectedQuantity, String reason, String ipAddress) {
		int counted = quantities.countedQuantity(quantity);
		int seen = quantities.seenQuantity(expectedQuantity);
		String checkedReason = reason(reason);
		if (counted == seen) {
			throw ApiException.invalid("quantity", "must differ from the stock shown");
		}
		return run(actor, itemId, Kind.CORRECTION, counted, seen, checkedReason, ipAddress);
	}

	private Changed run(CurrentUser actor, long itemId, Kind kind, int quantity, int seen, String reason, String ipAddress) {
		ItemRow item = retry.run(() -> writer.change(actor.id(), itemId, kind, quantity, seen, reason, ipAddress));
		return new Changed(item, repository.isListed(itemId));
	}

	/** A reason, trimmed: 1 to 255 characters. */
	private static String reason(String reason) {
		String trimmed = reason == null ? "" : reason.strip();
		if (trimmed.isEmpty() || trimmed.length() > Limits.REASON) {
			throw ApiException.invalid("reason", "must be 1 to " + Limits.REASON + " characters");
		}
		return trimmed;
	}

}
