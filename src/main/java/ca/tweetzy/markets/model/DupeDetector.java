package ca.tweetzy.markets.model;

import ca.tweetzy.flight.comp.enums.CompMaterial;
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
import org.jetbrains.annotations.Nullable;

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
	 * Unified entry point for logging a prevented dupe attempt.
	 */
	public static void logPreventedAttempt(
			@NonNull final String attemptType,
			@Nullable final Player actor,
			@Nullable final Player otherParty,
			@Nullable final ItemStack item,
			final int quantity,
			@Nullable final Market market,
			@Nullable final MarketItem marketItem,
			@Nullable final String detail
	) {
		final boolean prevented = !ALLOW_POTTED_DUPE;
		final String actorName = resolveName(actor, market);
		final String otherName = otherParty != null ? otherParty.getName() : actorName;
		final ItemStack alertItem = item != null ? item : CompMaterial.BARRIER.parseItem();

		alertAdmins(attemptType, actorName, otherName, alertItem, quantity, prevented);
		logPreventedToFile(attemptType, actor, otherParty, item, quantity, market, marketItem, detail, prevented);

		Markets.getInstance().getLogger().warning(
				String.format("[DUPE %s] Type: %s | Actor: %s | Item: %s x%d | Market: %s%s",
						prevented ? "PREVENTED" : "DETECTED",
						attemptType,
						actorName,
						ItemUtil.getItemName(alertItem),
						quantity,
						market != null ? market.getDisplayName() : "N/A",
						detail != null ? " | " + detail : "")
		);
	}

	/**
	 * Log a dupe attempt with full details (purchase/delete race — legacy API).
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
		logPreventedAttempt(
				attemptType,
				Bukkit.getPlayer(buyerUUID),
				Bukkit.getPlayer(sellerUUID),
				item.getItem(),
				quantity,
				market,
				item,
				String.format("buyer=%s (%s) | seller=%s (%s) | price=%.2f",
						buyerName, buyerUUID, sellerName, sellerUUID, price)
		);
	}

	/**
	 * Log a GUI draft-item dupe attempt (e.g. grab item from slot then refresh GUI).
	 */
	public static void logGuiDraftDupeAttempt(
			@NonNull final Player player,
			@NonNull final String guiName,
			@NonNull final String action,
			@NonNull final ItemStack draftItem,
			@NonNull final Market market
	) {
		logPreventedAttempt(
				"GUI_DRAFT_ITEM / " + action,
				player,
				null,
				draftItem,
				draftItem.getAmount(),
				market,
				null,
				"gui=" + guiName
		);
	}

	/**
	 * Log when an operation is blocked due to concurrent modification.
	 */
	public static void logBlockedOperation(
			@NonNull final String operationType,
			@NonNull final Player player,
			@NonNull final MarketItem item
	) {
		final String attemptType = "PURCHASE".equals(operationType)
				? "RACE_PURCHASE_DURING_EDIT"
				: operationType;

		logPreventedAttempt(
				attemptType,
				player,
				null,
				item.getItem(),
				item.getStock(),
				item.getOwningMarket(),
				item,
				null
		);
	}

	private static String resolveName(@Nullable final Player actor, @Nullable final Market market) {
		if (actor != null)
			return actor.getName();
		if (market != null)
			return market.getOwnerName();
		return "Unknown";
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

					if (Settings.DUPE_ALERT_SOUND_ENABLED.getBoolean()) {
						try {
							final Sound sound = Sound.valueOf(Settings.DUPE_ALERT_SOUND.getString().toUpperCase());
							admin.playSound(admin.getLocation(), sound, 1.0f, 1.0f);
						} catch (IllegalArgumentException ignored) {
						}
					}
				});
	}

	private static void logPreventedToFile(
			@NonNull final String attemptType,
			@Nullable final Player actor,
			@Nullable final Player otherParty,
			@Nullable final ItemStack item,
			final int quantity,
			@Nullable final Market market,
			@Nullable final MarketItem marketItem,
			@Nullable final String detail,
			final boolean prevented
	) {
		if (!Settings.DUPE_LOG_TO_FILE.getBoolean()) {
			return;
		}

		final String actorName = resolveName(actor, market);
		final UUID actorUuid = actor != null ? actor.getUniqueId()
				: (market != null ? market.getOwnerUUID() : new UUID(0, 0));
		final String otherName = otherParty != null ? otherParty.getName() : "N/A";
		final UUID otherUuid = otherParty != null ? otherParty.getUniqueId() : new UUID(0, 0);

		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			try {
				if (!LOG_FILE.getParentFile().exists()) {
					LOG_FILE.getParentFile().mkdirs();
				}

				if (!LOG_FILE.exists()) {
					LOG_FILE.createNewFile();
				}

				try (FileWriter fw = new FileWriter(LOG_FILE, true);
				     PrintWriter pw = new PrintWriter(fw)) {

					final String timestamp = DATE_FORMAT.format(new Date());
					final String status = prevented ? "PREVENTED" : "DETECTED";

					pw.println("================================================================================");
					pw.println(String.format("[%s] DUPE ATTEMPT %s", timestamp, status));
					pw.println("--------------------------------------------------------------------------------");
					pw.println(String.format("Attempt Type:    %s", attemptType));
					pw.println(String.format("Actor:           %s (%s)", actorName, actorUuid));
					if (otherParty != null) {
						pw.println(String.format("Other Party:     %s (%s)", otherName, otherUuid));
					}
					if (market != null) {
						pw.println(String.format("Market:          %s (%s)", market.getDisplayName(), market.getId()));
						pw.println(String.format("Market Owner:    %s (%s)", market.getOwnerName(), market.getOwnerUUID()));
					}
					if (marketItem != null) {
						pw.println(String.format("Item ID:         %s", marketItem.getId()));
					}
					if (item != null) {
						pw.println(String.format("Item:            %s", ItemUtil.getItemName(item)));
					}
					pw.println(String.format("Quantity:        %d", quantity));
					if (marketItem != null) {
						pw.println(String.format("Price:           %.2f %s", marketItem.getPrice(), marketItem.getCurrencyDisplayName()));
					}
					if (detail != null) {
						pw.println(String.format("Detail:          %s", detail));
					}
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
}
