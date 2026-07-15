package ca.tweetzy.markets.model;

import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.gui.shared.MarketsMainGUI;
import ca.tweetzy.markets.gui.shared.view.content.MarketViewGUI;
import ca.tweetzy.markets.settings.Translations;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@UtilityClass
public final class MarketLockHelper {

	public boolean isOwnerManagementLocked(Player player, Market market) {
		if (player == null || market == null || !market.isLocked())
			return false;

		if (!market.getOwnerUUID().equals(player.getUniqueId()))
			return false;

		return !player.hasPermission("markets.admin.shoplock") && !player.isOp();
	}

	public boolean denyOwnerIfLocked(Player player, Market market) {
		if (!isOwnerManagementLocked(player, market))
			return false;

		Common.tell(player, TranslationManager.string(player, Translations.MARKET_IS_LOCKED));
		return true;
	}

	public void tellLockedAndRedirect(Player player, Market market) {
		Common.tell(player, TranslationManager.string(player, Translations.MARKET_IS_LOCKED));
		Bukkit.getScheduler().runTask(Markets.getInstance(), () ->
				Markets.getGuiManager().showGUI(player, new MarketViewGUI(new MarketsMainGUI(player), player, market, true)));
	}

	public boolean shouldBlockManagementGui(Player player, Market market) {
		if (!isOwnerManagementLocked(player, market))
			return false;

		tellLockedAndRedirect(player, market);
		return true;
	}
}
