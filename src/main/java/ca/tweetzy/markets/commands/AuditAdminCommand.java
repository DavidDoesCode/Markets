package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.ItemUtil;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.core.Category;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketItem;
import ca.tweetzy.markets.model.EssentialsWorthHook;
import ca.tweetzy.markets.model.WorthPriceLimiter;
import ca.tweetzy.markets.settings.Translations;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public final class AuditAdminCommand {

	private static final int MAX_CHAT_RESULTS = 50;

	public ReturnType execute(CommandSender sender, String... args) {
		if (!sender.hasPermission("markets.admin.audit")) {
			Common.tell(sender, TranslationManager.string(Translations.NO_PERMISSION));
			return ReturnType.FAIL;
		}

		if (!EssentialsWorthHook.isAvailable()) {
			Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_UNAVAILABLE));
			return ReturnType.FAIL;
		}

		if (args.length < 2) {
			Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_INVALID));
			return ReturnType.FAIL;
		}

		final Double percent = parseDouble(args[1]);
		if (percent == null || percent <= 0) {
			Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_INVALID));
			return ReturnType.FAIL;
		}

		Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_STARTED,
				"percent", String.format("%,.0f", percent)));

		final boolean overpricedMode = percent >= 100;
		final double multiplier = percent / 100.0;
		final List<MarketItem> snapshot = new ArrayList<>(Markets.getCategoryItemManager().getManagerContent());

		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			final List<AuditEntry> matches = new ArrayList<>();

			for (final MarketItem marketItem : snapshot) {
				if (!WorthPriceLimiter.isVaultCurrency(marketItem.getCurrency()))
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

				final String owner = resolveOwnerName(marketItem);
				final double ratioPercent = (unitPrice / worth) * 100.0;
				matches.add(new AuditEntry(
						owner,
						ItemUtil.getItemName(marketItem.getItem()),
						unitPrice,
						worth,
						ratioPercent,
						marketItem.getId().toString()
				));
			}

			Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
				if (matches.isEmpty()) {
					Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_NONE));
					return;
				}

				Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_HEADER,
						"count", String.valueOf(matches.size()),
						"mode", overpricedMode ? "overpriced" : "underpriced"));

				final int shown = Math.min(MAX_CHAT_RESULTS, matches.size());
				for (int i = 0; i < shown; i++) {
					final AuditEntry entry = matches.get(i);
					Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_ENTRY,
							"owner", entry.owner,
							"item", entry.itemName,
							"unit_price", String.format("%,.2f", entry.unitPrice),
							"worth", String.format("%,.2f", entry.worth),
							"ratio", String.format("%,.0f", entry.ratioPercent),
							"item_id", entry.itemId));
				}

				if (matches.size() > MAX_CHAT_RESULTS) {
					Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_MORE,
							"remaining", String.valueOf(matches.size() - MAX_CHAT_RESULTS)));
				}

				Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_COMPLETE));
			});
		});

		return ReturnType.SUCCESS;
	}

	private String resolveOwnerName(MarketItem marketItem) {
		final Category category = Markets.getCategoryManager().getByUUID(marketItem.getOwningCategory());
		if (category == null)
			return "Unknown";

		final Market market = Markets.getMarketManager().getByUUID(category.getOwningMarket());
		if (market == null)
			return "Unknown";

		return market.getOwnerName();
	}

	private Double parseDouble(String value) {
		try {
			return Double.parseDouble(value);
		} catch (final NumberFormatException exception) {
			return null;
		}
	}

	public List<String> tab(String... args) {
		if (args.length == 1 || args.length == 2)
			return List.of("50", "100", "500", "1000", "2500");
		return null;
	}

	private static final class AuditEntry {
		private final String owner;
		private final String itemName;
		private final double unitPrice;
		private final double worth;
		private final double ratioPercent;
		private final String itemId;

		private AuditEntry(String owner, String itemName, double unitPrice, double worth, double ratioPercent, String itemId) {
			this.owner = owner;
			this.itemName = itemName;
			this.unitPrice = unitPrice;
			this.worth = worth;
			this.ratioPercent = ratioPercent;
			this.itemId = itemId;
		}
	}
}
