package ca.tweetzy.markets.model;

import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.ItemUtil;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketItem;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Detects and logs potential dupe exploit attempts.
 * Handles both prevention mode and honeypot mode.
 */
public final class DupeDetector {

	// HONEYPOT MODE: Set to true to ALLOW dupes but log them (catch exploiters)
	// Set to false to PREVENT dupes (protect economy)
	private static final boolean ALLOW_POTTED_DUPE = false;

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	private static final File LOG_FILE = new File(Markets.getInstance().getDataFolder(), "dupe-attempts.txt");

	/**
	 * Check if honeypot mode is enabled (allows dupes for detection)
	 */
	public static boolean isHoneypotMode() {
		return ALLOW_POTTED_DUPE;
	}

	/**
	 * Log a dupe attempt with full details
	 */
	public static void logDupeAttempt(
			@NonNull final String attemptType,
			@NonNull final UUID buyerUUID,
			@NonNull final String buyerName,
			@NonNull final UUID sellerUUID,
			@NonNull final String sellerName,
			@NonNull final MarketItem item,
			final int quantity,
			final double price,
			@NonNull final Market market
	) {
		final boolean prevented = !ALLOW_POTTED_DUPE;

		// Alert online admins
		alertAdmins(attemptType, buyerName, sellerName, item.getItem(), quantity, prevented);

		// Log to file
		logToFile(attemptType, buyerUUID, buyerName, sellerUUID, sellerName, item, quantity, price, market, prevented);

		// Log to console
		Markets.getInstance().getLogger().warning(
				String.format("[DUPE %s] Type: %s | Buyer: %s | Seller: %s | Item: %s x%d | Market: %s",
						prevented ? "PREVENTED" : "DETECTED",
						attemptType,
						buyerName,
						sellerName,
						ItemUtil.getItemName(item.getItem()),
						quantity,
						market.getDisplayName()
				)
		);
	}

	/**
	 * Alert all online players with the dupe alert permission
	 */
	private static void alertAdmins(
			@NonNull final String attemptType,
			@NonNull final String buyerName,
			@NonNull final String sellerName,
			@NonNull final ItemStack item,
			final int quantity,
			final boolean prevented
	) {
		if (!Settings.DUPE_ALERT_ENABLED.getBoolean()) {
			return;
		}

		final String permission = Settings.DUPE_ALERT_PERMISSION.getString();

		Bukkit.getOnlinePlayers().stream()
				.filter(player -> player.hasPermission(permission))
				.forEach(admin -> {
					// Send message
					if (prevented) {
						Common.tell(admin, TranslationManager.list(admin, Translations.DUPE_PREVENTED_MESSAGE,
								"buyer", buyerName,
								"seller", sellerName,
								"item_name", ItemUtil.getItemName(item),
								"quantity", quantity,
								"attempt_type", attemptType
						));
					} else {
						Common.tell(admin, TranslationManager.list(admin, Translations.DUPE_ALERT_MESSAGE,
								"buyer", buyerName,
								"seller", sellerName,
								"item_name", ItemUtil.getItemName(item),
								"quantity", quantity,
								"attempt_type", attemptType
						));
					}

					// Play sound
					if (Settings.DUPE_ALERT_SOUND_ENABLED.getBoolean()) {
						try {
							final Sound sound = Sound.valueOf(Settings.DUPE_ALERT_SOUND.getString().toUpperCase());
							admin.playSound(admin.getLocation(), sound, 1.0f, 1.0f);
						} catch (IllegalArgumentException e) {
							// Invalid sound, skip
						}
					}
				});
	}

	/**
	 * Log dupe attempt to file with full details
	 */
	private static void logToFile(
			@NonNull final String attemptType,
			@NonNull final UUID buyerUUID,
			@NonNull final String buyerName,
			@NonNull final UUID sellerUUID,
			@NonNull final String sellerName,
			@NonNull final MarketItem item,
			final int quantity,
			final double price,
			@NonNull final Market market,
			final boolean prevented
	) {
		if (!Settings.DUPE_LOG_TO_FILE.getBoolean()) {
			return;
		}

		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			try {
				// Create parent directory if it doesn't exist
				if (!LOG_FILE.getParentFile().exists()) {
					LOG_FILE.getParentFile().mkdirs();
				}

				// Create file if it doesn't exist
				if (!LOG_FILE.exists()) {
					LOG_FILE.createNewFile();
				}

				// Append to file
				try (FileWriter fw = new FileWriter(LOG_FILE, true);
				     PrintWriter pw = new PrintWriter(fw)) {

					final String timestamp = DATE_FORMAT.format(new Date());
					final String status = prevented ? "PREVENTED" : "DETECTED";

					pw.println("================================================================================");
					pw.println(String.format("[%s] DUPE ATTEMPT %s", timestamp, status));
					pw.println("--------------------------------------------------------------------------------");
					pw.println(String.format("Attempt Type:    %s", attemptType));
					pw.println(String.format("Buyer:           %s (%s)", buyerName, buyerUUID));
					pw.println(String.format("Seller:          %s (%s)", sellerName, sellerUUID));
					pw.println(String.format("Market:          %s (%s)", market.getDisplayName(), market.getId()));
					pw.println(String.format("Market Owner:    %s (%s)", market.getOwnerName(), market.getOwnerUUID()));
					pw.println(String.format("Item:            %s", ItemUtil.getItemName(item.getItem())));
					pw.println(String.format("Item ID:         %s", item.getId()));
					pw.println(String.format("Quantity:        %d", quantity));
					pw.println(String.format("Price:           %.2f %s", price, item.getCurrencyDisplayName()));
					pw.println(String.format("Total Value:     %.2f %s", price * quantity, item.getCurrencyDisplayName()));
					pw.println(String.format("Thread ID:       %s", Thread.currentThread().getId()));
					pw.println(String.format("Server Time:     %d", System.currentTimeMillis()));
					pw.println("================================================================================");
					pw.println();
				}
			} catch (IOException e) {
				Markets.getInstance().getLogger().severe("Failed to write to dupe-attempts.txt: " + e.getMessage());
				e.printStackTrace();
			}
		});
	}

	/**
	 * Log when an operation is blocked due to concurrent modification
	 */
	public static void logBlockedOperation(
			@NonNull final String operationType,
			@NonNull final Player player,
			@NonNull final MarketItem item
	) {
		Markets.getInstance().getLogger().info(
				String.format("[DUPE PROTECTION] Blocked %s by %s on item %s (item is being edited)",
						operationType,
						player.getName(),
						ItemUtil.getItemName(item.getItem())
				)
		);
	}
}
