package ca.tweetzy.markets.gui.user.market;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.gui.helper.InventoryBorder;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.SynchronizeResult;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketUser;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.gui.shared.selector.PlayerPickerGUI;
import ca.tweetzy.markets.model.MarketLockHelper;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public final class MarketBannedUsersGUI extends MarketsPagedGUI<UUID> {

	private final Player player;
	private final Market market;


	public MarketBannedUsersGUI(@NonNull final Gui parent, @NonNull final Player player, @NonNull final Market market) {
		super(parent, player, TranslationManager.string(player, Translations.GUI_MARKET_BANNED_USERS_TITLE), 6, market.getBannedUsers());
		this.player = player;
		this.market = market;
		setAsync(true);
		setDefaultItem(QuickItem.bg(Settings.GUI_MARKET_BANNED_USERS_BACKGROUND.getItemStack()));

		if (MarketLockHelper.shouldBlockManagementGui(player, market))
			return;

		draw();
	}

	@Override
	protected void drawFixed() {
		// new ban button
		setButton(getRows() - 1, 4, QuickItem
				.of(Settings.GUI_MARKET_BANNED_USERS_ITEMS_NEW_BAN.getItemStack())
				.name(TranslationManager.string(this.player, Translations.GUI_MARKET_BANNED_USERS_ITEMS_NEW_BAN_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_MARKET_BANNED_USERS_ITEMS_NEW_BAN_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
				.make(), click -> click.manager.showGUI(click.player, new PlayerPickerGUI(this, this.player, this.market.getBannedUsers(), uuid -> banUser(uuid, click.player))));
	}

	private void banUser(UUID uuid, Player opener) {
		if (this.market.getBannedUsers().contains(uuid)) return;

		this.market.getBannedUsers().add(uuid);

		this.market.sync(result -> {
			if (result == SynchronizeResult.FAILURE)
				this.market.getBannedUsers().remove(uuid);

			Markets.getGuiManager().showGUI(opener, new MarketBannedUsersGUI(this.parent, opener, this.market));
		});
	}

	@Override
	protected void onPopulateComplete() {
		// Load player heads asynchronously after the GUI is populated
		loadPlayerHeadsAsync();
	}

	@Override
	protected ItemStack makeDisplayItem(UUID uuid) {
		final String playerName = resolveDisplayName(uuid);

		// Return placeholder head immediately
		// The actual player head will be loaded asynchronously in onPopulateComplete()
		return QuickItem
				.of(CompMaterial.PLAYER_HEAD)
				.name(TranslationManager.string(this.player, Translations.GUI_MARKET_BANNED_USERS_ITEMS_PLAYER_NAME, "player_name", playerName))
				.lore(TranslationManager.list(this.player, Translations.GUI_MARKET_BANNED_USERS_ITEMS_PLAYER_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
				.make();
	}

	private String resolveDisplayName(UUID uuid) {
		final MarketUser user = Markets.getPlayerManager().get(uuid);
		if (user != null && user.getLastKnownName() != null)
			return user.getLastKnownName();
		return uuid.toString().substring(0, 8);
	}

	private void loadPlayerHeadsAsync() {
		// Get the current page items
		final List<UUID> itemsToDisplay = this.items.stream()
				.skip((page - 1) * (long) fillSlots().size())
				.limit(fillSlots().size())
				.toList();

		// Load player heads asynchronously for each banned user
		for (int i = 0; i < itemsToDisplay.size(); i++) {
			final UUID uuid = itemsToDisplay.get(i);
			final int slotIndex = fillSlots().get(i);
			final String playerName = resolveDisplayName(uuid);

			// Load the OfflinePlayer and then the player head asynchronously
			// This prevents blocking the main thread
			Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
				final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
				QuickItem.asyncPlayerHead(offlinePlayer).thenAccept(skull -> {
					// Build the final item with the loaded skull
					ItemStack finalItem = QuickItem.of(skull)
							.name(TranslationManager.string(this.player, Translations.GUI_MARKET_BANNED_USERS_ITEMS_PLAYER_NAME, "player_name", playerName))
							.lore(TranslationManager.list(this.player, Translations.GUI_MARKET_BANNED_USERS_ITEMS_PLAYER_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
							.make();

					// Update the slot on the main thread
					Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
						setItem(slotIndex, finalItem);
					});
				});
			});
		}
	}

	@Override
	protected void onClick(UUID uuid, GuiClickEvent click) {
		if (!this.market.getBannedUsers().contains(uuid)) return;

		this.market.getBannedUsers().remove(uuid);

		this.market.sync(result -> {
			if (result == SynchronizeResult.FAILURE)
				this.market.getBannedUsers().add(uuid);
			else
				click.manager.showGUI(click.player, new MarketBannedUsersGUI(this.parent, this.player, this.market));
		});
	}

	@Override
	protected List<Integer> fillSlots() {
		return InventoryBorder.getInsideBorders(6);
	}
}
