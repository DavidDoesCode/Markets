package ca.tweetzy.markets.model;

import ca.tweetzy.flight.utils.ItemUtil;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.core.Category;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketItem;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@UtilityClass
public final class AuditScanner {

	public List<AuditEntry> findMatches(final double percent) {
		return findMatches(percent, null);
	}

	public List<AuditEntry> findMatches(final double percent, final Material materialFilter) {
		final boolean overpricedMode = percent >= 100;
		final double multiplier = percent / 100.0;
		final List<MarketItem> snapshot = new ArrayList<>(Markets.getCategoryItemManager().getManagerContent());
		final List<AuditEntry> matches = new ArrayList<>();

		for (final MarketItem marketItem : snapshot) {
			if (materialFilter != null && marketItem.getItem().getType() != materialFilter)
				continue;

			if (!WorthPriceLimiter.isVaultCurrency(marketItem.getCurrency()))
				continue;

			if (WorthPriceLimiter.isExcludedFromWorthPercent(marketItem.getItem()))
				continue;

			final Double worth = EssentialsWorthHook.getUnitWorth(marketItem.getItem());
			if (worth == null || worth <= 0)
				continue;

			final int quantity = marketItem.isPriceForAll()
					? Math.max(1, marketItem.getStock())
					: Math.max(1, marketItem.getItem().getAmount());
			final double unitPrice = WorthPriceLimiter.resolveUnitPrice(marketItem.getPrice(), marketItem.isPriceForAll(), quantity);
			final double threshold = worth * multiplier;
			final boolean matchesRule = overpricedMode ? unitPrice >= threshold : unitPrice <= threshold;

			if (!matchesRule)
				continue;

			final OwnerInfo owner = resolveOwner(marketItem);
			final double ratioPercent = (unitPrice / worth) * 100.0;
			matches.add(new AuditEntry(
					marketItem,
					owner.uuid(),
					owner.name(),
					ItemUtil.getItemName(marketItem.getItem()),
					unitPrice,
					worth,
					ratioPercent
			));
		}

		return matches;
	}

	public boolean isOverpricedMode(final double percent) {
		return percent >= 100;
	}

	private OwnerInfo resolveOwner(@NonNull final MarketItem marketItem) {
		final Category category = Markets.getCategoryManager().getByUUID(marketItem.getOwningCategory());
		if (category == null)
			return new OwnerInfo(new UUID(0, 0), "Unknown");

		final Market market = Markets.getMarketManager().getByUUID(category.getOwningMarket());
		if (market == null)
			return new OwnerInfo(new UUID(0, 0), "Unknown");

		return new OwnerInfo(market.getOwnerUUID(), market.getOwnerName());
	}

	private record OwnerInfo(UUID uuid, String name) {
	}
}
