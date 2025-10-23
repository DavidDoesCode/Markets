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
			// Get the player's market
			final Market market = Markets.getMarketManager().getByOwner(player.getUniqueId());

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
		return null;
	}

	@Override
	public String getPermissionNode() {
		return "markets.command.stats";
	}

	@Override
	public String getSyntax() {
		return "stats";
	}

	@Override
	public String getDescription() {
		return "View your market statistics";
	}
}
