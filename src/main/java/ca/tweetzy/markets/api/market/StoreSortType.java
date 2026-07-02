package ca.tweetzy.markets.api.market;

import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.markets.api.Navigable;
import ca.tweetzy.markets.settings.Translations;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum StoreSortType implements Navigable<StoreSortType> {

	HIGHEST_SCORE(true),
	LOWEST_SCORE(true);

	@Getter
	private final boolean enabled;

	@Override
	public StoreSortType next() {
		final StoreSortType[] values = enumClass().getEnumConstants();
		final int ordinal = ordinal();

		for (int i = 1; i < values.length; i++) {
			final int nextOrdinal = (ordinal + i) % values.length;
			final StoreSortType next = values[nextOrdinal];

			if (next.enabled) {
				return next;
			}
		}

		return this;
	}

	@Override
	public StoreSortType previous() {
		final StoreSortType[] values = enumClass().getEnumConstants();
		final int ordinal = ordinal();

		for (int i = 1; i <= values.length; i++) {
			final int previousOrdinal = (ordinal - i + values.length) % values.length;
			final StoreSortType previous = values[previousOrdinal];

			if (previous.enabled) {
				return previous;
			}
		}

		return this;
	}

	@Override
	public Class<StoreSortType> enumClass() {
		return StoreSortType.class;
	}

	public String getTranslatedName() {
		return switch (this) {
			case HIGHEST_SCORE -> TranslationManager.string(Translations.STORE_SORT_HIGHEST_SCORE);
			case LOWEST_SCORE -> TranslationManager.string(Translations.STORE_SORT_LOWEST_SCORE);
		};
	}
}
