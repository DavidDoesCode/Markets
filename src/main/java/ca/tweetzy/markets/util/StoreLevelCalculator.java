package ca.tweetzy.markets.util;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.markets.api.market.Transaction;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.settings.Translations;
import lombok.Getter;
import lombok.NonNull;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StoreLevelCalculator {

	private StoreLevelCalculator() {
	}

	public record StoreLevelSnapshot(
			@NonNull Market market,
			int level,
			int totalSales,
			int totalListings,
			double avgRating,
			int totalCustomers,
			int totalReviews
	) {
	}

	@Getter
	public enum StoreTier {
		LEGENDARY(9, "Legendary"),
		MASTER(7, "Master"),
		EXPERT(5, "Expert"),
		ESTABLISHED(3, "Established"),
		NOVICE(1, "Novice"),
		BEGINNER(0, "Beginner");

		private final int minLevel;
		private final String plainName;

		StoreTier(final int minLevel, final String plainName) {
			this.minLevel = minLevel;
			this.plainName = plainName;
		}

		public static @NonNull StoreTier fromLevel(final int level) {
			if (level >= LEGENDARY.minLevel) return LEGENDARY;
			if (level >= MASTER.minLevel) return MASTER;
			if (level >= EXPERT.minLevel) return EXPERT;
			if (level >= ESTABLISHED.minLevel) return ESTABLISHED;
			if (level >= NOVICE.minLevel) return NOVICE;
			return BEGINNER;
		}
	}

	public static int calculateStoreLevel(final int totalSales, final int totalListings, final double avgRating, final int totalCustomers) {
		final int salesScore = Math.min(totalSales / 50, 3);
		final int listingsScore = Math.min(totalListings / 10, 2);
		final int ratingScore = (int) Math.min(avgRating, 3);
		final int customerScore = Math.min(totalCustomers / 5, 2);

		return salesScore + listingsScore + ratingScore + customerScore;
	}

	public static @NonNull CompMaterial getStoreLevelIcon(final int level) {
		if (level >= 9) return CompMaterial.NETHER_STAR;
		if (level >= 7) return CompMaterial.DIAMOND;
		if (level >= 5) return CompMaterial.GOLD_BLOCK;
		if (level >= 3) return CompMaterial.IRON_BLOCK;
		if (level >= 1) return CompMaterial.COPPER_BLOCK;
		return CompMaterial.DIRT;
	}

	public static @NonNull String getStoreLevelTier(@NonNull final Player player, final int level) {
		return switch (StoreTier.fromLevel(level)) {
			case LEGENDARY -> TranslationManager.string(player, Translations.STORE_TIER_LEGENDARY);
			case MASTER -> TranslationManager.string(player, Translations.STORE_TIER_MASTER);
			case EXPERT -> TranslationManager.string(player, Translations.STORE_TIER_EXPERT);
			case ESTABLISHED -> TranslationManager.string(player, Translations.STORE_TIER_ESTABLISHED);
			case NOVICE -> TranslationManager.string(player, Translations.STORE_TIER_NOVICE);
			case BEGINNER -> TranslationManager.string(player, Translations.STORE_TIER_BEGINNER);
		};
	}

	public static @NonNull Map<StoreTier, Integer> countByTier(@NonNull final List<StoreLevelSnapshot> snapshots) {
		final Map<StoreTier, Integer> counts = new LinkedHashMap<>();
		for (final StoreTier tier : StoreTier.values()) {
			counts.put(tier, 0);
		}

		for (final StoreLevelSnapshot snapshot : snapshots) {
			final StoreTier tier = StoreTier.fromLevel(snapshot.level());
			counts.merge(tier, 1, Integer::sum);
		}

		return counts;
	}

	public static @NonNull StoreLevelSnapshot computeForMarket(
			@NonNull final Market market,
			final int totalSales,
			final int totalCustomers
	) {
		final int totalListings = getTotalListings(market);
		final double avgRating = market.getRatings().isEmpty() ? 0 : market.getReviewAvg();
		final int level = calculateStoreLevel(totalSales, totalListings, avgRating, totalCustomers);

		return new StoreLevelSnapshot(
				market,
				level,
				totalSales,
				totalListings,
				avgRating,
				totalCustomers,
				market.getRatings().size()
		);
	}

	public static @NonNull StoreLevelSnapshot computeForMarket(@NonNull final Market market, @NonNull final List<Transaction> sales) {
		final int totalSales = sales.stream().mapToInt(Transaction::getQuantity).sum();
		final int totalCustomers = (int) sales.stream().map(Transaction::getBuyer).distinct().count();
		return computeForMarket(market, totalSales, totalCustomers);
	}

	public static int getTotalListings(@NonNull final Market market) {
		return market.getCategories().stream()
				.mapToInt(category -> category.getItems().size())
				.sum();
	}

	public static int getTotalSalesQuantity(@NonNull final List<Transaction> sales) {
		return sales.stream().mapToInt(Transaction::getQuantity).sum();
	}

	public static int getUniqueCustomers(@NonNull final List<Transaction> sales) {
		return (int) sales.stream().map(Transaction::getBuyer).distinct().count();
	}

}
