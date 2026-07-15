package ca.tweetzy.markets.api.market;

import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.markets.api.Navigable;
import ca.tweetzy.markets.settings.Translations;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum AuditSortType implements Navigable<AuditSortType> {

	HIGHEST_PRICE(true),
	HIGHEST_RATIO(true),
	LOWEST_PRICE(true),
	LOWEST_RATIO(true);

	@Getter
	private final boolean enabled;

	@Override
	public AuditSortType next() {
		AuditSortType[] values = enumClass().getEnumConstants();
		AuditSortType current = this;
		int ordinal = current.ordinal();

		for (int i = 1; i < values.length; i++) {
			int nextOrdinal = (ordinal + i) % values.length;
			AuditSortType next = values[nextOrdinal];
			if (next.enabled)
				return next;
		}

		return null;
	}

	@Override
	public AuditSortType previous() {
		AuditSortType[] values = enumClass().getEnumConstants();
		AuditSortType current = this;
		int ordinal = current.ordinal();

		for (int i = 1; i <= values.length; i++) {
			int previousOrdinal = (ordinal - i + values.length) % values.length;
			AuditSortType previous = values[previousOrdinal];
			if (previous.enabled)
				return previous;
		}

		return null;
	}

	@Override
	public Class<AuditSortType> enumClass() {
		return AuditSortType.class;
	}

	public String getTranslatedName() {
		return switch (this) {
			case HIGHEST_PRICE -> TranslationManager.string(Translations.AUDIT_SORT_HIGHEST_PRICE);
			case HIGHEST_RATIO -> TranslationManager.string(Translations.AUDIT_SORT_HIGHEST_RATIO);
			case LOWEST_PRICE -> TranslationManager.string(Translations.AUDIT_SORT_LOWEST_PRICE);
			case LOWEST_RATIO -> TranslationManager.string(Translations.AUDIT_SORT_LOWEST_RATIO);
		};
	}
}
