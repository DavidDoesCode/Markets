package ca.tweetzy.markets.model;

import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.api.market.core.MarketItem;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@UtilityClass
public final class WorthPriceLimiter {

	public boolean isVaultCurrency(final String currency) {
		if (currency == null || currency.isEmpty())
			return false;

		final String[] parts = currency.split("/");
		return parts.length >= 1 && parts[0].equalsIgnoreCase("Vault");
	}

	public double resolveUnitPrice(final double listingPrice, final boolean priceIsForAll, final int quantity) {
		if (priceIsForAll)
			return listingPrice / Math.max(1, quantity);
		return listingPrice;
	}

	/**
	 * @return max allowed unit price, or null if uncapped for this player/currency
	 */
	public Double getMaxAllowedUnitPrice(@NonNull final Player player, @NonNull final ItemStack item, final String currency) {
		if (!Settings.WORTH_PRICE_LIMIT_ENABLED.getBoolean())
			return null;

		if (!isVaultCurrency(currency))
			return null;

		if (player.hasPermission(Settings.WORTH_PRICE_LIMIT_BYPASS_PERMISSION.getString()))
			return null;

		final Double worth = EssentialsWorthHook.getUnitWorth(item);
		if (worth != null && worth > 0)
			return worth * (Settings.WORTH_PRICE_LIMIT_MAX_PERCENT.getDouble() / 100.0);

		return Settings.WORTH_PRICE_LIMIT_ABSOLUTE_MAX.getDouble();
	}

	/**
	 * Validates a proposed listing price. Returns true if allowed.
	 * On failure, sends an error message to the player.
	 */
	public boolean validate(@NonNull final Player player, @NonNull final ItemStack item, final String currency,
							final double listingPrice, final boolean priceIsForAll, final int quantity) {
		final Double maxUnit = getMaxAllowedUnitPrice(player, item, currency);
		if (maxUnit == null)
			return true;

		final double unitPrice = resolveUnitPrice(listingPrice, priceIsForAll, quantity);
		if (unitPrice <= maxUnit)
			return true;

		Common.tell(player, TranslationManager.string(player, Translations.WORTH_PRICE_TOO_HIGH,
				"max_price", String.format("%,.2f", maxUnit)));
		return false;
	}

	public boolean validate(@NonNull final Player player, @NonNull final MarketItem marketItem, final double listingPrice) {
		final int quantity = marketItem.isPriceForAll()
				? Math.max(1, marketItem.getStock())
				: Math.max(1, marketItem.getItem().getAmount());

		return validate(player, marketItem.getItem(), marketItem.getCurrency(), listingPrice, marketItem.isPriceForAll(), quantity);
	}

	public boolean validateCurrentPrice(@NonNull final Player player, @NonNull final MarketItem marketItem) {
		return validate(player, marketItem, marketItem.getPrice());
	}
}
