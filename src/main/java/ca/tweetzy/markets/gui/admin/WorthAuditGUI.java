package ca.tweetzy.markets.gui.admin;

import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.gui.helper.InventoryBorder;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.AuditSortType;
import ca.tweetzy.markets.api.market.AuditStockFilter;
import ca.tweetzy.markets.api.market.core.Category;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketItem;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.gui.shared.selector.ConfirmGUI;
import ca.tweetzy.markets.gui.shared.view.content.MarketCategoryViewGUI;
import ca.tweetzy.markets.model.AuditEntry;
import ca.tweetzy.markets.model.AuditListingRemover;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WorthAuditGUI extends MarketsPagedGUI<AuditEntry> {

	private final String modeLabel;
	private AuditSortType sortType;
	private AuditStockFilter stockFilter;
	private final List<AuditEntry> results;

	public WorthAuditGUI(Gui parent, @NonNull final Player player, @NonNull final List<AuditEntry> results,
						 @NonNull final String modeLabel, @NonNull final AuditSortType sortType,
						 @NonNull final AuditStockFilter stockFilter) {
		super(parent, player, TranslationManager.string(player, Translations.GUI_WORTH_AUDIT_TITLE, "mode", modeLabel), 6, new ArrayList<>(results));
		this.modeLabel = modeLabel;
		this.sortType = sortType;
		this.stockFilter = stockFilter;
		this.results = new ArrayList<>(results);
		setAsync(true);
		setDefaultItem(QuickItem.bg(Settings.GUI_WORTH_AUDIT_BACKGROUND.getItemStack()));
		draw();
	}

	@Override
	protected void prePopulate() {
		this.items = new ArrayList<>(this.results);
		applyStockFilter();
		applySort();
	}

	private void applyStockFilter() {
		if (this.stockFilter == AuditStockFilter.ALL)
			return;

		this.items.removeIf(entry -> !matchesStockFilter(entry));
	}

	private boolean matchesStockFilter(AuditEntry entry) {
		final MarketItem source = resolveMarketItem(entry);
		return switch (this.stockFilter) {
			case ALL -> true;
			case IN_STOCK -> source.isInfinite() || source.getStock() > 0;
			case NO_STOCK -> !source.isInfinite() && source.getStock() <= 0;
		};
	}

	private void applySort() {
		switch (this.sortType) {
			case HIGHEST_PRICE -> this.items.sort(Comparator.comparingDouble(AuditEntry::getUnitPrice).reversed());
			case HIGHEST_RATIO -> this.items.sort(Comparator.comparingDouble(AuditEntry::getRatioPercent).reversed());
			case LOWEST_PRICE -> this.items.sort(Comparator.comparingDouble(AuditEntry::getUnitPrice));
			case LOWEST_RATIO -> this.items.sort(Comparator.comparingDouble(AuditEntry::getRatioPercent));
			case HIGHEST_QTY -> this.items.sort(Comparator.comparingInt(WorthAuditGUI::resolveStockQty).reversed());
			case LOWEST_QTY -> this.items.sort(Comparator.comparingInt(WorthAuditGUI::resolveStockQty));
		}
	}

	private static MarketItem resolveMarketItem(AuditEntry entry) {
		final MarketItem live = Markets.getCategoryItemManager().getByUUID(entry.getMarketItem().getId());
		return live != null ? live : entry.getMarketItem();
	}

	private static int resolveStockQty(AuditEntry entry) {
		final MarketItem marketItem = resolveMarketItem(entry);
		return marketItem.isInfinite() ? Integer.MAX_VALUE : Math.max(0, marketItem.getStock());
	}

	@Override
	protected ItemStack makeDisplayItem(AuditEntry entry) {
		return QuickItem
				.of(resolveMarketItem(entry).getItem())
				.lore(buildLore(entry))
				.make();
	}

	private List<String> buildLore(AuditEntry entry) {
		final MarketItem source = resolveMarketItem(entry);
		final String stockDisplay = source.isInfinite() ? "∞" : String.format("%,d", Math.max(0, source.getStock()));

		return TranslationManager.list(this.player, Translations.GUI_WORTH_AUDIT_ITEMS_ENTRY_LORE,
				"owner", entry.getOwnerName(),
				"item", entry.getItemName(),
				"unit_price", String.format("%,.2f", entry.getUnitPrice()),
				"worth", String.format("%,.2f", entry.getWorth()),
				"ratio", String.format("%,.0f", entry.getRatioPercent()),
				"stock", stockDisplay,
				"item_id", entry.getItemId(),
				"drop_key", TranslationManager.string(this.player, Translations.DROP_KEY));
	}

	@Override
	protected void drawFixed() {
		setButton(5, 7, QuickItem
				.of(Settings.GUI_WORTH_AUDIT_ITEMS_STOCK_FILTER_ITEM.getItemStack())
				.name(TranslationManager.string(this.player, Translations.GUI_WORTH_AUDIT_ITEMS_STOCK_FILTER_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_WORTH_AUDIT_ITEMS_STOCK_FILTER_LORE,
						"audit_stock_filter", this.stockFilter.getTranslatedName()))
				.make(), click -> {
			this.stockFilter = this.stockFilter.next();
			this.page = 1;
			draw();
		});

		setButton(5, 8, QuickItem
				.of(Settings.GUI_WORTH_AUDIT_ITEMS_FILTER_ITEM.getItemStack())
				.name(TranslationManager.string(this.player, Translations.GUI_WORTH_AUDIT_ITEMS_FILTER_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_WORTH_AUDIT_ITEMS_FILTER_LORE,
						"audit_sort_type", this.sortType.getTranslatedName()))
				.make(), click -> {
			this.sortType = this.sortType.next();
			this.page = 1;
			draw();
		});
	}

	@Override
	protected void onClick(AuditEntry entry, GuiClickEvent click) {
		if (click.clickType == ClickType.DROP) {
			handleDelete(entry, click);
			return;
		}

		final MarketItem marketItem = Markets.getCategoryItemManager().getByUUID(entry.getMarketItem().getId());
		if (marketItem == null) {
			refreshAfterStale(click, entry);
			return;
		}

		final Category category = Markets.getCategoryManager().getByUUID(marketItem.getOwningCategory());
		if (category == null) {
			refreshAfterStale(click, entry);
			return;
		}

		final Market market = Markets.getMarketManager().getByUUID(category.getOwningMarket());
		if (market == null) {
			refreshAfterStale(click, entry);
			return;
		}

		click.manager.showGUI(click.player, new MarketCategoryViewGUI(this, click.player, market, category, false, false));
	}

	private void handleDelete(AuditEntry entry, GuiClickEvent click) {
		final MarketItem marketItem = Markets.getCategoryItemManager().getByUUID(entry.getMarketItem().getId());
		if (marketItem == null) {
			refreshAfterStale(click, entry);
			return;
		}

		click.manager.showGUI(click.player, new ConfirmGUI(this, click.player, confirmed -> {
			if (!confirmed) {
				click.manager.showGUI(click.player, reopen());
				return;
			}

			AuditListingRemover.removeListing(
					marketItem,
					click.player.getName(),
					"Admin removed listing from worth audit",
					success -> Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
						if (!success) {
							Common.tell(click.player, TranslationManager.string(click.player, Translations.WORTH_AUDIT_DELETE_FAILED));
							click.manager.showGUI(click.player, reopen());
							return;
						}

						Common.tell(click.player, TranslationManager.string(click.player, Translations.WORTH_AUDIT_DELETE_SUCCESS,
								"item", entry.getItemName(),
								"owner", entry.getOwnerName()));
						this.results.removeIf(existing -> existing.getMarketItem().getId().equals(entry.getMarketItem().getId()));
						click.manager.showGUI(click.player, reopen());
					})
			);
		}));
	}

	private void refreshAfterStale(GuiClickEvent click, AuditEntry entry) {
		Common.tell(click.player, TranslationManager.string(click.player, Translations.WORTH_AUDIT_STALE));
		this.results.removeIf(existing -> existing.getMarketItem().getId().equals(entry.getMarketItem().getId()));
		click.manager.showGUI(click.player, reopen());
	}

	private WorthAuditGUI reopen() {
		return new WorthAuditGUI(this.parent, this.player, this.results, this.modeLabel, this.sortType, this.stockFilter);
	}

	@Override
	protected List<Integer> fillSlots() {
		return InventoryBorder.getInsideBorders(6);
	}
}
