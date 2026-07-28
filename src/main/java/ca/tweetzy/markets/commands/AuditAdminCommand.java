package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.AuditSortType;
import ca.tweetzy.markets.api.market.core.MarketItem;
import ca.tweetzy.markets.gui.admin.WorthAuditGUI;
import ca.tweetzy.markets.model.AuditEntry;
import ca.tweetzy.markets.model.AuditScanner;
import ca.tweetzy.markets.model.EssentialsWorthHook;
import ca.tweetzy.markets.settings.Translations;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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

		final Material materialFilter;
		if (args.length >= 3) {
			materialFilter = Material.matchMaterial(args[2]);
			if (materialFilter == null || !materialFilter.isItem()) {
				Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_INVALID));
				return ReturnType.FAIL;
			}
		} else {
			materialFilter = null;
		}

		Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_STARTED,
				"percent", String.format("%,.0f", percent)));

		final boolean overpricedMode = AuditScanner.isOverpricedMode(percent);

		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			final List<AuditEntry> matches = AuditScanner.findMatches(percent, materialFilter);

			Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
				if (matches.isEmpty()) {
					Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_NONE));
					return;
				}

				final AuditSortType defaultSort = overpricedMode ? AuditSortType.HIGHEST_RATIO : AuditSortType.LOWEST_RATIO;
				sortMatches(matches, defaultSort);

				final String modeLabel = overpricedMode ? "overpriced" : "underpriced";

				if (sender instanceof final Player player) {
					Common.tell(player, TranslationManager.string(Translations.WORTH_AUDIT_OPENED,
							"count", String.valueOf(matches.size())));
					Markets.getGuiManager().showGUI(player, new WorthAuditGUI(null, player, matches, modeLabel, defaultSort));
					return;
				}

				Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_HEADER,
						"count", String.valueOf(matches.size()),
						"mode", modeLabel));

				final int shown = Math.min(MAX_CHAT_RESULTS, matches.size());
				for (int i = 0; i < shown; i++) {
					final AuditEntry entry = matches.get(i);
					Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_ENTRY,
							"owner", entry.getOwnerName(),
							"item", entry.getItemName(),
							"unit_price", String.format("%,.2f", entry.getUnitPrice()),
							"worth", String.format("%,.2f", entry.getWorth()),
							"ratio", String.format("%,.0f", entry.getRatioPercent()),
							"item_id", entry.getItemId()));
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

	private void sortMatches(List<AuditEntry> matches, AuditSortType sortType) {
		switch (sortType) {
			case HIGHEST_PRICE -> matches.sort(Comparator.comparingDouble(AuditEntry::getUnitPrice).reversed());
			case HIGHEST_RATIO -> matches.sort(Comparator.comparingDouble(AuditEntry::getRatioPercent).reversed());
			case LOWEST_PRICE -> matches.sort(Comparator.comparingDouble(AuditEntry::getUnitPrice));
			case LOWEST_RATIO -> matches.sort(Comparator.comparingDouble(AuditEntry::getRatioPercent));
			case HIGHEST_QTY -> matches.sort(Comparator.comparingInt(AuditAdminCommand::resolveStockQty).reversed());
			case LOWEST_QTY -> matches.sort(Comparator.comparingInt(AuditAdminCommand::resolveStockQty));
		}
	}

	private int resolveStockQty(AuditEntry entry) {
		final MarketItem marketItem = entry.getMarketItem();
		return marketItem.isInfinite() ? Integer.MAX_VALUE : Math.max(0, marketItem.getStock());
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

		if (args.length == 3) {
			final String prefix = args[2].toLowerCase(Locale.ROOT);
			return Arrays.stream(Material.values())
					.filter(Material::isItem)
					.map(material -> material.name().toLowerCase(Locale.ROOT))
					.filter(name -> name.startsWith(prefix))
					.collect(Collectors.toList());
		}

		return null;
	}
}
