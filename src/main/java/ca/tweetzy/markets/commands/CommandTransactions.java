package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.AllowedExecutor;
import ca.tweetzy.flight.command.Command;
import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.gui.user.TransactionsGUI;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class CommandTransactions extends Command {

	public CommandTransactions() {
		super(AllowedExecutor.PLAYER, Settings.CMD_ALIAS_SUB_TRANSACTIONS.getStringList().toArray(new String[0]));
	}

	@Override
	protected ReturnType execute(CommandSender sender, String... args) {
		if (sender instanceof final Player player) {
			// Admin usage: /markets transactions <player>
			if (args.length >= 1) {
				// Check if player has admin permission
				if (!player.hasPermission("markets.admin.transactions")) {
					Common.tell(player, TranslationManager.string(player, Translations.NO_PERMISSION));
					return ReturnType.FAIL;
				}

				final String targetName = args[0];

				// Try to find the player - first check if they're online
				Player onlineTarget = Bukkit.getPlayer(targetName);
				if (onlineTarget != null) {
					// Player is online - show their transactions
					Markets.getGuiManager().showGUI(player, new TransactionsGUI(null, player, false, onlineTarget.getUniqueId(), onlineTarget.getName()));
					return ReturnType.SUCCESS;
				}

				// Player is offline - look them up in the player manager
				final ca.tweetzy.markets.api.market.core.MarketUser marketUser = Markets.getPlayerManager().getManagerContent()
						.values()
						.stream()
						.filter(user -> user.getLastKnownName().equalsIgnoreCase(targetName))
						.findFirst()
						.orElse(null);

				if (marketUser != null) {
					// Found in player manager - use their cached data
					Markets.getGuiManager().showGUI(player, new TransactionsGUI(null, player, false, marketUser.getUUID(), marketUser.getLastKnownName()));
					return ReturnType.SUCCESS;
				}

				// Last resort - try OfflinePlayer (less reliable)
				final OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
				if (offlineTarget != null && offlineTarget.hasPlayedBefore()) {
					// Use the offline player's UUID, but their name might be null
					final String displayName = offlineTarget.getName() != null ? offlineTarget.getName() : targetName;
					Markets.getGuiManager().showGUI(player, new TransactionsGUI(null, player, false, offlineTarget.getUniqueId(), displayName));
					return ReturnType.SUCCESS;
				}

				// Player not found
				Common.tell(player, "&cPlayer not found: &e" + targetName);
				return ReturnType.FAIL;
			}

			// Normal usage: show own transactions
			Markets.getGuiManager().showGUI(player, new TransactionsGUI(null, player, false));
		}
		return ReturnType.SUCCESS;
	}

	@Override
	protected List<String> tab(CommandSender sender, String... args) {
		if (args.length == 1 && sender.hasPermission("markets.admin.transactions")) {
			return null; // Return null to use default online player tab completion
		}
		return List.of();
	}

	@Override
	public String getPermissionNode() {
		return "markets.command.transactions";
	}

	@Override
	public String getSyntax() {
		return "transactions [player]";
	}

	@Override
	public String getDescription() {
		return "Opens transactions (admin: view any player)";
	}
}
