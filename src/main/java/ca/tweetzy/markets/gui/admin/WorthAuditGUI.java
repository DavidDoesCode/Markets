package ca.tweetzy.markets.gui.admin;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.gui.helper.InventoryBorder;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.AuditSortType;
import ca.tweetzy.markets.api.market.core.Category;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketItem;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.gui.shared.view.content.MarketCategoryViewGUI;
import ca.tweetzy.markets.model.AuditEntry;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WorthAuditGUI extends MarketsPagedGUI<AuditEntry> {

	private final String modeLabel;
	private AuditSortType sortType;
	private final List<AuditEntry> results;

	public WorthAuditGUI(Gui parent, @NonNull final Player player, @NonNull final List<AuditEntry> results,
						 @NonNull final String modeLabel, @NonNull final AuditSortType sortType) {
		super(parent, player, TranslationManager.string(player, Translations.GUI_WORTH_AUDIT_TITLE, "mode", modeLabel), 6, new ArrayList<>(results));
		this.modeLabel = modeLabel;
		this.sortType = sortType;
		this.results = new ArrayList<>(results);
		setAsync(true);
		setDefaultItem(QuickItem.bg(Settings.GUI_WORTH_AUDIT_BACKGROUND.getItemStack()));
		draw();
	}

	@Override
	protected void onPopulateComplete() {
		loadPlayerHeadsAsync();
	}

	@Override
	protected void prePopulate() {
		this.items = new ArrayList<>(this.results);
		applySort();
	}

	private void applySort() {
		switch (this.sortType) {
			case HIGHEST_PRICE -> this.items.sort(Comparator.comparingDouble(AuditEntry::getUnitPrice).reversed());
			case HIGHEST_RATIO -> this.items.sort(Comparator.comparingDouble(AuditEntry::getRatioPercent).reversed());
			case LOWEST_PRICE -> this.items.sort(Comparator.comparingDouble(AuditEntry::getUnitPrice));
			case LOWEST_RATIO -> this.items.sort(Comparator.comparingDouble(AuditEntry::getRatioPercent));
		}
	}

	@Override
	protected ItemStack makeDisplayItem(AuditEntry entry) {
		return QuickItem
				.of(CompMaterial.PLAYER_HEAD)
				.name(TranslationManager.string(this.player, Translations.GUI_WORTH_AUDIT_ITEMS_ENTRY_NAME,
						"owner", entry.getOwnerName()))
				.lore(buildLore(entry))
				.make();
	}

	private List<String> buildLore(AuditEntry entry) {
		return TranslationManager.list(this.player, Translations.GUI_WORTH_AUDIT_ITEMS_ENTRY_LORE,
				"item", entry.getItemName(),
				"unit_price", String.format("%,.2f", entry.getUnitPrice()),
				"worth", String.format("%,.2f", entry.getWorth()),
				"ratio", String.format("%,.0f", entry.getRatioPercent()),
				"item_id", entry.getItemId());
	}

	@Override
	protected void drawFixed() {
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

	private void loadPlayerHeadsAsync() {
		final List<AuditEntry> itemsToDisplay = this.items.stream()
				.skip((page - 1) * (long) fillSlots().size())
				.limit(fillSlots().size())
				.toList();

		for (int i = 0; i < itemsToDisplay.size(); i++) {
			final AuditEntry entry = itemsToDisplay.get(i);
			final int slotIndex = fillSlots().get(i);

			Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
				final OfflinePlayer owner = Bukkit.getOfflinePlayer(entry.getOwnerUUID());
				QuickItem.asyncPlayerHead(owner).thenAccept(skull -> {
					final ItemStack finalItem = QuickItem.of(skull)
							.name(TranslationManager.string(this.player, Translations.GUI_WORTH_AUDIT_ITEMS_ENTRY_NAME,
									"owner", entry.getOwnerName()))
							.lore(buildLore(entry))
							.make();

					Bukkit.getScheduler().runTask(Markets.getInstance(), () -> setItem(slotIndex, finalItem));
				});
			});
		}
	}

	@Override
	protected void onClick(AuditEntry entry, GuiClickEvent click) {
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

	private void refreshAfterStale(GuiClickEvent click, AuditEntry entry) {
		Common.tell(click.player, TranslationManager.string(click.player, Translations.WORTH_AUDIT_STALE));
		this.results.removeIf(existing -> existing.getMarketItem().getId().equals(entry.getMarketItem().getId()));
		click.manager.showGUI(click.player, new WorthAuditGUI(this.parent, this.player, this.results, this.modeLabel, this.sortType));
	}

	@Override
	protected List<Integer> fillSlots() {
		return InventoryBorder.getInsideBorders(6);
	}
}
