package ca.tweetzy.markets.gui.admin;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.gui.helper.InventoryBorder;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.flight.utils.TimeUtil;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.MarketSortType;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketUser;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.gui.shared.view.content.MarketViewGUI;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AdminMarketsListGUI extends MarketsPagedGUI<Market> {

	private static final DecimalFormat VALUE_FORMAT = new DecimalFormat("#,##0.00");

	public enum ListMode {
		LOCKED,
		BANNED,
		CLOSED
	}

	private final List<Market> sourceMarkets;
	private MarketSortType sortType = MarketSortType.NAME;

	public AdminMarketsListGUI(Gui parent, @NonNull Player player, @NonNull ListMode listMode, @NonNull List<Market> markets) {
		super(parent, player, TranslationManager.string(player, switch (listMode) {
			case LOCKED -> Translations.GUI_ADMIN_LOCKED_SHOPS_TITLE;
			case BANNED -> Translations.GUI_ADMIN_BANNED_SHOPS_TITLE;
			case CLOSED -> Translations.GUI_ADMIN_CLOSED_SHOPS_TITLE;
		}), 6, new ArrayList<>(markets));
		this.sourceMarkets = new ArrayList<>(markets);
		setAsync(true);
		setDefaultItem(QuickItem.bg(Settings.GUI_ALL_MARKETS_BACKGROUND.getItemStack()));
		draw();
	}

	@Override
	protected void prePopulate() {
		this.items = new ArrayList<>(this.sourceMarkets);

		if (this.sortType == MarketSortType.NAME) {
			this.items.sort(Comparator.comparing(Market::getDisplayName, String.CASE_INSENSITIVE_ORDER));
		}

		if (this.sortType == MarketSortType.ITEMS) {
			this.items.sort(Comparator.comparing(Market::getItemCount).reversed());
		}

		if (this.sortType == MarketSortType.REVIEWS) {
			this.items.sort(Comparator
					.comparingDouble(Market::getReviewAvg)
					.thenComparingInt(m -> m.getRatings().size())
					.reversed());
		}

		if (this.sortType == MarketSortType.LAST_UPDATED) {
			this.items.sort(Comparator.comparing(Market::getLastUpdated).reversed());
		}
	}

	@Override
	protected void drawFixed() {
		setButton(getRows() - 1, 8, QuickItem
				.of(Settings.GUI_ALL_MARKETS_ITEMS_FILTER_ITEM.getItemStack())
				.name(TranslationManager.string(this.player, Translations.GUI_ADMIN_MARKETS_LIST_ITEMS_FILTER_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_ADMIN_MARKETS_LIST_ITEMS_FILTER_LORE,
						"market_sort_type", this.sortType.getTranslatedName()))
				.make(), click -> {
			final MarketSortType next = this.sortType.next();
			if (next != null)
				this.sortType = next;
			draw();
		});
	}

	@Override
	protected void onPopulateComplete() {
		loadPlayerHeadsAsync();
	}

	@Override
	protected ItemStack makeDisplayItem(Market market) {
		return buildMarketItem(market, null);
	}

	@Override
	protected void onClick(Market market, GuiClickEvent click) {
		click.manager.showGUI(click.player, new MarketViewGUI(this, click.player, market, false));
	}

	@Override
	protected List<Integer> fillSlots() {
		return InventoryBorder.getInsideBorders(6);
	}

	private ItemStack buildMarketItem(Market market, ItemStack skull) {
		final MarketUser owner = Markets.getPlayerManager().get(market.getOwnerUUID());
		final QuickItem item = skull == null
				? (market.isServerMarket()
					? QuickItem.of(Settings.SERVER_MARKET_TEXTURE.getString()).fallbackTexture(Settings.SERVER_MARKET_TEXTURE.getString())
					: QuickItem.of(CompMaterial.PLAYER_HEAD))
				: QuickItem.of(skull);

		return item
				.name(market.getDisplayName())
				.lore(TranslationManager.list(this.player, Translations.GUI_ADMIN_MARKETS_LIST_ITEMS_MARKET_OWNER,
						"market_owner", market.getOwnerName()))
				.lore(market.getDescription())
				.lore(buildMarketLore(market, owner))
				.make();
	}

	private List<String> buildMarketLore(Market market, MarketUser owner) {
		return TranslationManager.list(this.player, Translations.GUI_ADMIN_MARKETS_LIST_ITEMS_MARKET_LORE,
				"shop_open_status", TranslationManager.string(this.player, market.isOpen() ? Translations.SHOP_STATUS_OPEN : Translations.SHOP_STATUS_CLOSED),
				"shop_lock_status", TranslationManager.string(this.player, market.isLocked() ? Translations.SHOP_STATUS_LOCKED : Translations.SHOP_STATUS_UNLOCKED),
				"user_last_seen", TimeUtil.convertToReadableDate(owner.getLastSeenAt(), Settings.DATETIME_FORMAT.getString()),
				"item_count", market.getItemCount(),
				"in_stock_count", market.getTotalStock(),
				"total_value", VALUE_FORMAT.format(market.getTotalListingValue()),
				"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)
		);
	}

	private void loadPlayerHeadsAsync() {
		final List<Market> itemsToDisplay = this.items.stream()
				.skip((page - 1) * (long) fillSlots().size())
				.limit(fillSlots().size())
				.toList();

		for (int i = 0; i < itemsToDisplay.size(); i++) {
			final Market market = itemsToDisplay.get(i);
			final int slotIndex = fillSlots().get(i);

			if (market.isServerMarket())
				continue;

			Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
				final OfflinePlayer owner = Bukkit.getOfflinePlayer(market.getOwnerUUID());
				QuickItem.asyncPlayerHead(owner).thenAccept(skull -> {
					final ItemStack finalItem = buildMarketItem(market, skull);
					Bukkit.getScheduler().runTask(Markets.getInstance(), () -> setItem(slotIndex, finalItem));
				});
			});
		}
	}
}
