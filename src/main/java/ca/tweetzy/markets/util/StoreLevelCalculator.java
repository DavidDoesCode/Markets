package ca.tweetzy.markets.util;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.markets.api.market.Transaction;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.entity.Player;

import java.util.List;

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
		if (level >= 9) return TranslationManager.string(player, Translations.STORE_TIER_LEGENDARY);
		if (level >= 7) return TranslationManager.string(player, Translations.STORE_TIER_MASTER);
		if (level >= 5) return TranslationManager.string(player, Translations.STORE_TIER_EXPERT);
		if (level >= 3) return TranslationManager.string(player, Translations.STORE_TIER_ESTABLISHED);
		if (level >= 1) return TranslationManager.string(player, Translations.STORE_TIER_NOVICE);
		return TranslationManager.string(player, Translations.STORE_TIER_BEGINNER);
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
