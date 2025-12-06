package ca.tweetzy.markets.listeners;

import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.event.MarketTransactionEvent;
import ca.tweetzy.markets.api.market.Transaction;
import ca.tweetzy.markets.api.market.core.MarketUser;
import ca.tweetzy.markets.impl.MarketTransaction;
import ca.tweetzy.markets.settings.Settings;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public final class MarketTransactionListener implements Listener {

	@EventHandler
	public void onTransactionEvent(final MarketTransactionEvent event) {
		// Safely extract buyer name with fallback chain
		String buyerName = getSafePlayerName(event.getBuyer().getUniqueId(), event.getBuyer().getName(), "Buyer");

		// Safely extract seller name with fallback chain
		String sellerName;
		if (event.getSeller().getUniqueId().equals(UUID.fromString(Settings.SERVER_MARKET_UUID.getString()))) {
			// Server market - use configured name
			sellerName = Settings.NAME.getString();
		} else {
			// Player market - get name safely
			sellerName = getSafePlayerName(event.getSeller().getUniqueId(), event.getSeller().getName(), "Seller");
		}

		// Create transaction with guaranteed non-null names
		final Transaction transaction = new MarketTransaction(
				UUID.randomUUID(),
				event.getBuyer().getUniqueId(),
				buyerName,
				event.getSeller().getUniqueId(),
				sellerName,
				event.getType(),
				event.getItem(),
				event.getCurrency(),
				event.getQuantity(),
				event.getPrice(),
				System.currentTimeMillis()
		);

		// Store transaction
		transaction.store(storeTransaction -> {
			if (storeTransaction == null) {
				Common.log("&cFailed to store transaction: " + transaction.getId());
				Common.log("&c  Buyer: " + buyerName + " (" + event.getBuyer().getUniqueId() + ")");
				Common.log("&c  Seller: " + sellerName + " (" + event.getSeller().getUniqueId() + ")");
				Common.log("&c  Type: " + event.getType());
				Common.log("&c  Item: " + event.getItem().getType());
				Common.log("&c  Quantity: " + event.getQuantity());
			} else {
				Markets.getTransactionManager().add(storeTransaction);
			}
		});

		// create an offline notification for the player

	}

	/**
	 * Safely get player name with fallback chain
	 *
	 * @param uuid Player UUID
	 * @param offlinePlayerName Name from OfflinePlayer.getName() (can be null)
	 * @param role Role description for logging (e.g., "Buyer", "Seller")
	 * @return Non-null player name
	 */
	private String getSafePlayerName(UUID uuid, String offlinePlayerName, String role) {
		// Try OfflinePlayer name first
		if (offlinePlayerName != null && !offlinePlayerName.isEmpty()) {
			return offlinePlayerName;
		}

		// Fallback 1: Try MarketUser cache
		MarketUser user = Markets.getPlayerManager().get(uuid);
		if (user != null && user.getLastKnownName() != null && !user.getLastKnownName().isEmpty()) {
			Common.log("&eWarning: " + role + " name was null, using cached name: " + user.getLastKnownName());
			return user.getLastKnownName();
		}

		// Fallback 2: Use UUID prefix as last resort
		String fallbackName = "Unknown-" + uuid.toString().substring(0, 8);
		Common.log("&cWarning: " + role + " name was null and no cache available, using fallback: " + fallbackName);
		return fallbackName;
	}
}
