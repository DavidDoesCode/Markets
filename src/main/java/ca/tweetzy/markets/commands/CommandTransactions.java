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

				// Get target player
				final OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

				if (target == null || !target.hasPlayedBefore()) {
					Common.tell(player, TranslationManager.string(player, Translations.PLAYER_OFFLINE, "value", args[0]));
					return ReturnType.FAIL;
				}

				// Show loading message
				Common.tell(player, TranslationManager.string(Translations.LOADING_TRANSACTIONS, "player_name", args[0]));

				// Load transactions async
				Markets.getTransactionManager().getTransactionsForAsync(target.getUniqueId(), transactions -> {
					// Open GUI with loaded data (already on main thread from callback)
					Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
					if (onlineTarget == null) {
						// For offline players, show their transactions to admin
						Markets.getGuiManager().showGUI(player, new TransactionsGUI(null, player, false) {
							@Override
							protected void prePopulate() {
								// Override with pre-loaded transactions
								this.items = new java.util.ArrayList<>(transactions);
								this.items.sort(java.util.Comparator.comparing(ca.tweetzy.markets.api.market.Transaction::getTimeCreated).reversed());
								this.isLoading = false; // Mark as loaded
							}
						});
					} else {
						// Show online player's transactions (will load async)
						Markets.getGuiManager().showGUI(player, new TransactionsGUI(null, onlineTarget, false));
					}
				});

				return ReturnType.SUCCESS;
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
