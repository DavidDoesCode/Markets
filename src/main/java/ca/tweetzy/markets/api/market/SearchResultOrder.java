package ca.tweetzy.markets.api.market;

import ca.tweetzy.markets.settings.Settings;
import lombok.Getter;
import lombok.NonNull;

import java.util.Locale;

@Getter
public enum SearchResultOrder {

	DEFAULT,
	SHUFFLE,
	SHUFFLE_ROUND_ROBIN;

	public boolean shufflesShops() {
		return this == SHUFFLE || this == SHUFFLE_ROUND_ROBIN;
	}

	public boolean isRoundRobin() {
		return this == SHUFFLE_ROUND_ROBIN;
	}

	@NonNull
	public static SearchResultOrder fromConfig() {
		return fromString(Settings.SEARCH_RESULT_ORDER.getString());
	}

	@NonNull
	public static SearchResultOrder fromString(final String value) {
		if (value == null || value.isBlank())
			return DEFAULT;

		final String normalized = value.trim().toUpperCase(Locale.ROOT)
				.replace('-', '_')
				.replace(' ', '_');

		return switch (normalized) {
			case "SHUFFLE" -> SHUFFLE;
			case "SHUFFLE_ROUND_ROBIN", "SHUFFLEROUNDROBIN" -> SHUFFLE_ROUND_ROBIN;
			default -> DEFAULT;
		};
	}
}
