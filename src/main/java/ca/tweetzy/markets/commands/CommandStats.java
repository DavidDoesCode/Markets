package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.AllowedExecutor;
import ca.tweetzy.flight.command.Command;
import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.gui.user.market.MarketStatsGUI;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class CommandStats extends Command {

	public CommandStats() {
		super(AllowedExecutor.PLAYER, Settings.CMD_ALIAS_SUB_STATS.getStringList().toArray(new String[0]));
	}

	@Override
	protected ReturnType execute(CommandSender sender, String... args) {
		if (sender instanceof final Player player) {
			Market market;

			// Admin usage: /markets stats <player>
			if (args.length >= 1) {
				// Check if player has admin permission
				if (!player.hasPermission("markets.admin.stats")) {
					Common.tell(player, TranslationManager.string(player, Translations.NO_PERMISSION));
					return ReturnType.FAIL;
				}

				// Get target player
				final OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

				if (target == null || !target.hasPlayedBefore()) {
					Common.tell(player, TranslationManager.string(player, Translations.PLAYER_OFFLINE, "value", args[0]));
					return ReturnType.FAIL;
				}

				// Get target player's market
				market = Markets.getMarketManager().getByOwner(target.getUniqueId());

				if (market == null) {
					Common.tell(player, TranslationManager.string(player, Translations.NO_MARKET_FOUND, "player_name", args[0]));
					return ReturnType.FAIL;
				}

				// Open the stats GUI for target player
				Markets.getGuiManager().showGUI(player, new MarketStatsGUI(player, market));
				return ReturnType.SUCCESS;
			}

			// Normal usage: show own stats
			market = Markets.getMarketManager().getByOwner(player.getUniqueId());

			if (market == null) {
				Common.tell(player, TranslationManager.string(player, Translations.NO_MARKET_FOUND_SELF));
				return ReturnType.FAIL;
			}

			// Open the stats GUI
			Markets.getGuiManager().showGUI(player, new MarketStatsGUI(player, market));
		}
		return ReturnType.SUCCESS;
	}

	@Override
	protected List<String> tab(CommandSender sender, String... args) {
		if (args.length == 1 && sender.hasPermission("markets.admin.stats")) {
			return null; // Return null to use default online player tab completion
		}
		return List.of();
	}

	@Override
	public String getPermissionNode() {
		return "markets.command.stats";
	}

	@Override
	public String getSyntax() {
		return "stats [player]";
	}

	@Override
	public String getDescription() {
		return "View market statistics (admin: view any player)";
	}
}
