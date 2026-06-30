package ca.tweetzy.markets.util;

import ca.tweetzy.flight.utils.Common;
import lombok.NonNull;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public final class MessageLinks {

	private MessageLinks() {
	}

	public static void openUrl(@NonNull final Player player, @NonNull final String url, @NonNull final String linkText) {
		final TextComponent message = new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', linkText)));
		message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
		message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(url).create()));
		player.spigot().sendMessage(message);
		Common.tell(player, "&7" + url);
	}
}
