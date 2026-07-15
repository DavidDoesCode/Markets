package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.gui.admin.AdminMarketsListGUI;
import ca.tweetzy.markets.settings.Translations;
import lombok.experimental.UtilityClass;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@UtilityClass
public final class LockedShopsAdminCommand {

	public ReturnType execute(CommandSender sender, String... args) {
		if (!(sender instanceof final Player player))
			return ReturnType.FAIL;

		if (!player.hasPermission("markets.admin.shoplock") && !player.isOp()) {
			Common.tell(sender, TranslationManager.string(Translations.NO_PERMISSION));
			return ReturnType.FAIL;
		}

		final var markets = Markets.getMarketManager().getLockedMarkets();
		if (markets.isEmpty()) {
			Common.tell(player, TranslationManager.string(player, Translations.ADMIN_LOCKED_SHOPS_EMPTY));
			return ReturnType.FAIL;
		}

		Markets.getGuiManager().showGUI(player, new AdminMarketsListGUI(null, player, AdminMarketsListGUI.ListMode.LOCKED, markets));
		return ReturnType.SUCCESS;
	}
}
