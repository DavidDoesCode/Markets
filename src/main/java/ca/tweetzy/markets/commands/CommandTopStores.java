package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.AllowedExecutor;
import ca.tweetzy.flight.command.Command;
import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.gui.shared.view.TopStoresGUI;
import ca.tweetzy.markets.settings.Settings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class CommandTopStores extends Command {

	public CommandTopStores() {
		super(AllowedExecutor.PLAYER, Settings.CMD_ALIAS_SUB_TOPSTORES.getStringList().toArray(new String[0]));
	}

	@Override
	protected ReturnType execute(CommandSender sender, String... args) {
		if (sender instanceof final Player player) {
			Markets.getGuiManager().showGUI(player, new TopStoresGUI(null, player));
		}
		return ReturnType.SUCCESS;
	}

	@Override
	protected List<String> tab(CommandSender sender, String... args) {
		return null;
	}

	@Override
	public String getPermissionNode() {
		return "markets.command.topstores";
	}

	@Override
	public String getSyntax() {
		return "topstores";
	}

	@Override
	public String getDescription() {
		return "Opens the top stores leaderboard";
	}
}
