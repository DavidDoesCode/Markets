package ca.tweetzy.markets.listeners;

import ca.tweetzy.markets.gui.user.category.CategoryNewItemGUI;
import ca.tweetzy.markets.settings.Settings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Locale;

public final class NewItemPriceInputListener implements Listener {

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onCommand(final PlayerCommandPreprocessEvent event) {
		final Player player = event.getPlayer();
		if (!CategoryNewItemGUI.isAwaitingPrice(player.getUniqueId()))
			return;

		if (!isBareMainMarketCommand(event.getMessage()))
			return;

		event.setCancelled(true);
		CategoryNewItemGUI.cancelPriceInputToGui(player);
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onQuit(final PlayerQuitEvent event) {
		// Run before TitleInput's quit handler (NORMAL) so we recover instead of reopening a GUI
		CategoryNewItemGUI.recoverDraftToPayments(event.getPlayer().getUniqueId());
	}

	private static boolean isBareMainMarketCommand(final String rawMessage) {
		if (rawMessage == null || rawMessage.isBlank())
			return false;

		String message = rawMessage.trim();
		if (message.startsWith("/"))
			message = message.substring(1);

		final String[] parts = message.split("\\s+");
		if (parts.length != 1)
			return false;

		final String label = parts[0].toLowerCase(Locale.ROOT);
		final List<String> aliases = Settings.CMD_ALIAS_MAIN.getStringList();
		if (aliases == null || aliases.isEmpty())
			return label.equals("market") || label.equals("markets");

		for (final String alias : aliases) {
			if (alias != null && alias.equalsIgnoreCase(label))
				return true;
		}
		return false;
	}
}
