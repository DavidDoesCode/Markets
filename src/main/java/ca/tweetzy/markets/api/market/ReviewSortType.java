package ca.tweetzy.markets.api.market;

import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.markets.api.Navigable;
import ca.tweetzy.markets.settings.Translations;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum ReviewSortType implements Navigable<ReviewSortType> {

	RECENT(true),
	TOP_RATED(true),
	LOWEST_RATED(true);

	@Getter
	private final boolean enabled;

	@Override
	public ReviewSortType next() {
		ReviewSortType[] values = enumClass().getEnumConstants();
		ReviewSortType current = this;
		int ordinal = current.ordinal();

		// Loop through the values starting from the next one
		for (int i = 1; i < values.length; i++) {
			int nextOrdinal = (ordinal + i) % values.length;
			ReviewSortType next = values[nextOrdinal];

			// Return the first enabled value found
			if (next.enabled) {
				return next;
			}
		}

		return null;
	}

	@Override
	public ReviewSortType previous() {
		ReviewSortType[] values = enumClass().getEnumConstants();
		ReviewSortType current = this;
		int ordinal = current.ordinal();

		// Loop through the values starting from the previous one
		for (int i = 1; i <= values.length; i++) {
			int previousOrdinal = (ordinal - i + values.length) % values.length;
			ReviewSortType previous = values[previousOrdinal];

			// Return the first enabled value found
			if (previous.enabled) {
				return previous;
			}
		}

		return null;
	}

	@Override
	public Class<ReviewSortType> enumClass() {
		return ReviewSortType.class;
	}

	public String getTranslatedName() {
		return switch (this) {
			case RECENT -> TranslationManager.string(Translations.REVIEW_SORT_RECENT);
			case TOP_RATED -> TranslationManager.string(Translations.REVIEW_SORT_TOP_RATED);
			case LOWEST_RATED -> TranslationManager.string(Translations.REVIEW_SORT_LOWEST_RATED);
		};
	}
}
