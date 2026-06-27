package ca.tweetzy.markets.gui.shared.selector;

import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.gui.helper.InventoryBorder;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.flight.utils.input.TitleInput;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class PlayerPickerGUI extends MarketsPagedGUI<Player> {

	private final List<UUID> ignoredUsers;
	private final Consumer<UUID> selectedPlayer;

	public PlayerPickerGUI(final Gui parent, @NonNull final Player player, List<UUID> ignoredUsers, @NonNull final Consumer<UUID> selectedPlayer) {
		super(
				parent,
				player,
				TranslationManager.string(player, Translations.GUI_USER_PICKER_TITLE),
				6,
				ignoredUsers != null ? Bukkit.getOnlinePlayers().stream().filter(user -> !user.getUniqueId().equals(player.getUniqueId()) && !ignoredUsers.contains(user.getUniqueId())).collect(Collectors.toList()) : Bukkit.getOnlinePlayers().stream().filter(user -> !user.getUniqueId().equals(player.getUniqueId())).collect(Collectors.toList())
		);

		this.ignoredUsers = ignoredUsers != null ? ignoredUsers : Collections.emptyList();
		this.selectedPlayer = selectedPlayer;
		setAcceptsItems(true);
		setDefaultItem(QuickItem.bg(Settings.GUI_PLAYER_PICKER_BACKGROUND.getItemStack()));
		draw();
	}

	@Override
	protected void drawFixed() {
		setButton(getRows() - 1, 4, QuickItem
				.of(Settings.GUI_PLAYER_PICKER_ITEMS_ENTER_NAME.getItemStack())
				.name(TranslationManager.string(this.player, Translations.GUI_USER_PICKER_ITEMS_ENTER_NAME_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_USER_PICKER_ITEMS_ENTER_NAME_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
				.make(), click -> new TitleInput(Markets.getInstance(), click.player, TranslationManager.string(this.player, Translations.PROMPT_BAN_PLAYER_TITLE), TranslationManager.string(this.player, Translations.PROMPT_BAN_PLAYER_SUBTITLE)) {

			@Override
			public void onExit(Player player) {
				click.manager.showGUI(click.player, new PlayerPickerGUI(PlayerPickerGUI.this.parent, click.player, PlayerPickerGUI.this.ignoredUsers, PlayerPickerGUI.this.selectedPlayer));
			}

			@Override
			public boolean onResult(String string) {
				final String name = string.trim();
				if (name.isEmpty())
					return false;

				final UUID uuid = Markets.getPlayerManager().lookupUUIDByName(name).orElse(null);
				if (uuid == null) {
					Common.tell(click.player, TranslationManager.string(click.player, Translations.ERROR_PLAYER_NOT_FOUND, "player_name", name));
					return false;
				}

				if (uuid.equals(click.player.getUniqueId())) {
					Common.tell(click.player, TranslationManager.string(click.player, Translations.ERROR_CANNOT_BAN_SELF));
					return false;
				}

				if (PlayerPickerGUI.this.ignoredUsers.contains(uuid)) {
					Common.tell(click.player, TranslationManager.string(click.player, Translations.ERROR_PLAYER_ALREADY_BANNED));
					return false;
				}

				PlayerPickerGUI.this.selectedPlayer.accept(uuid);
				return true;
			}
		});
	}

	@Override
	protected ItemStack makeDisplayItem(Player player) {
		QuickItem quickItem = QuickItem.of(player);
		return quickItem.make();
	}

	@Override
	protected void onClick(Player player, GuiClickEvent event) {
		this.selectedPlayer.accept(player.getUniqueId());
	}

	@Override
	protected List<Integer> fillSlots() {
		return InventoryBorder.getInsideBorders(6);
	}
}
