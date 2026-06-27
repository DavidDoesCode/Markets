package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.AllowedExecutor;
import ca.tweetzy.flight.command.Command;
import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.core.MarketUser;
import ca.tweetzy.markets.gui.user.OfflinePaymentsGUI;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class CommandPayments extends Command {

	public CommandPayments() {
		super(AllowedExecutor.PLAYER, Settings.CMD_ALIAS_SUB_PAYMENTS.getStringList().toArray(new String[0]));
	}

	@Override
	protected ReturnType execute(CommandSender sender, String... args) {
		if (sender instanceof final Player player) {

			// Admin usage: /markets payments <player>
			if (args.length >= 1) {
				if (!player.hasPermission("markets.admin.payments")) {
					Common.tell(player, TranslationManager.string(player, Translations.NO_PERMISSION));
					return ReturnType.FAIL;
				}

				final String targetName = args[0];

				// Online target
				final Player onlineTarget = Bukkit.getPlayer(targetName);
				if (onlineTarget != null) {
					Markets.getGuiManager().showGUI(player, new OfflinePaymentsGUI(null, player, onlineTarget.getUniqueId(), onlineTarget.getName()));
					return ReturnType.SUCCESS;
				}

				// Cached MarketUser by last known name
				final MarketUser marketUser = Markets.getPlayerManager().getManagerContent()
						.values()
						.stream()
						.filter(user -> user.getLastKnownName().equalsIgnoreCase(targetName))
						.findFirst()
						.orElse(null);

				if (marketUser != null) {
					Markets.getGuiManager().showGUI(player, new OfflinePaymentsGUI(null, player, marketUser.getUUID(), marketUser.getLastKnownName()));
					return ReturnType.SUCCESS;
				}

				// Offline player fallback
				final OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
				if (offlineTarget != null && offlineTarget.hasPlayedBefore()) {
					final String displayName = offlineTarget.getName() != null ? offlineTarget.getName() : targetName;
					Markets.getGuiManager().showGUI(player, new OfflinePaymentsGUI(null, player, offlineTarget.getUniqueId(), displayName));
					return ReturnType.SUCCESS;
				}

				Common.tell(player, "&cPlayer not found: &e" + targetName);
				return ReturnType.FAIL;
			}

			// Normal usage: show own payments
			Markets.getGuiManager().showGUI(player, new OfflinePaymentsGUI(null, player));
		}
		return ReturnType.SUCCESS;
	}

	@Override
	protected List<String> tab(CommandSender sender, String... args) {
		if (args.length == 1 && sender.hasPermission("markets.admin.payments")) {
			return null; // default online player tab completion
		}
		return List.of();
	}

	@Override
	public String getPermissionNode() {
		return "markets.command.payments";
	}

	@Override
	public String getSyntax() {
		return "payments [player]";
	}

	@Override
	public String getDescription() {
		return "Opens your payment collection bin";
	}
}
