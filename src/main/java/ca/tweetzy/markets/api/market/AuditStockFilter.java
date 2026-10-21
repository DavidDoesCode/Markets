package ca.tweetzy.markets.api.market;

import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.markets.api.Navigable;
import ca.tweetzy.markets.settings.Translations;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum AuditStockFilter implements Navigable<AuditStockFilter> {

	ALL(true),
	IN_STOCK(true),
	NO_STOCK(true);

	@Getter
	private final boolean enabled;

	@Override
	public AuditStockFilter next() {
		AuditStockFilter[] values = enumClass().getEnumConstants();
		int ordinal = this.ordinal();

		for (int i = 1; i < values.length; i++) {
			int nextOrdinal = (ordinal + i) % values.length;
			AuditStockFilter next = values[nextOrdinal];
			if (next.enabled)
				return next;
		}

		return null;
	}

	@Override
	public AuditStockFilter previous() {
		AuditStockFilter[] values = enumClass().getEnumConstants();
		int ordinal = this.ordinal();

		for (int i = 1; i <= values.length; i++) {
			int previousOrdinal = (ordinal - i + values.length) % values.length;
			AuditStockFilter previous = values[previousOrdinal];
			if (previous.enabled)
				return previous;
		}

		return null;
	}

	@Override
	public Class<AuditStockFilter> enumClass() {
		return AuditStockFilter.class;
	}

	public String getTranslatedName() {
		return switch (this) {
			case ALL -> TranslationManager.string(Translations.AUDIT_STOCK_ALL);
			case IN_STOCK -> TranslationManager.string(Translations.AUDIT_STOCK_IN_STOCK);
			case NO_STOCK -> TranslationManager.string(Translations.AUDIT_STOCK_NO_STOCK);
		};
	}
}
