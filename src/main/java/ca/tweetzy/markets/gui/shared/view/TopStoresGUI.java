package ca.tweetzy.markets.gui.shared.view;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.gui.helper.InventoryBorder;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.StoreSortType;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.gui.shared.view.content.MarketViewGUI;
import ca.tweetzy.markets.model.manager.TransactionManager.SellerSalesStats;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import ca.tweetzy.markets.util.PlayerHeadCache;
import ca.tweetzy.markets.util.StoreLevelCalculator;
import ca.tweetzy.markets.util.StoreLevelCalculator.StoreLevelSnapshot;
import ca.tweetzy.markets.util.StoreLevelCalculator.StoreTier;
import ca.tweetzy.markets.util.TopStoresExporter;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TopStoresGUI extends MarketsPagedGUI<StoreLevelSnapshot> {

	private static final String EXPORT_PERMISSION = "markets.command.topstores.export";

	private final Player player;
	private StoreSortType sortType = StoreSortType.HIGHEST_SCORE;
	private boolean isLoading = false;
	private boolean guiShown = false;
	private int loadRequestId = 0;
	private int headLoadRequestId = 0;

	public TopStoresGUI(Gui parent, @NonNull final Player player) {
		super(parent, player, TranslationManager.string(player, Translations.GUI_TOP_STORES_TITLE), 6, new ArrayList<>());
		this.player = player;
		setDefaultItem(QuickItem.bg(Settings.GUI_TOP_STORES_BACKGROUND.getItemStack()));
		setOnOpen(open -> draw());

		loadRankingsAsync();
		this.guiShown = true;
	}

	@Override
	protected void prePopulate() {
		// Loaded in constructor and when filters change to avoid async draw loops
	}

	private void loadRankingsAsync() {
		final int requestId = ++this.loadRequestId;
		this.isLoading = true;

		if (this.guiShown) {
			draw();
		}

		final List<Market> playerMarkets = Markets.getMarketManager().getManagerContent().stream()
				.filter(market -> !market.isServerMarket())
				.collect(Collectors.toList());

		final List<UUID> ownerUUIDs = playerMarkets.stream()
				.map(Market::getOwnerUUID)
				.collect(Collectors.toList());

		Markets.getTransactionManager().computeSellerSalesStatsAsync(ownerUUIDs, stats ->
				applyRankingsResult(requestId, playerMarkets, stats));
	}

	private void applyRankingsResult(
			final int requestId,
			@NonNull final List<Market> playerMarkets,
			@NonNull final Map<UUID, SellerSalesStats> stats
	) {
		if (requestId != this.loadRequestId) return;

		final List<StoreLevelSnapshot> rankings = new ArrayList<>();
		for (final Market market : playerMarkets) {
			final SellerSalesStats salesStats = stats.getOrDefault(market.getOwnerUUID(), SellerSalesStats.EMPTY);
			rankings.add(StoreLevelCalculator.computeForMarket(market, salesStats.totalQuantity(), salesStats.uniqueBuyers()));
		}

		this.items = rankings;
		sortItems();
		this.isLoading = false;

		if (isStillOpen()) {
			draw();
		}
	}

	private boolean isStillOpen() {
		return this.player.isOnline() && this.player.getOpenInventory().getTopInventory().equals(this.inventory);
	}

	@Override
	protected void drawFixed() {
		setSortToggle();
		setSummaryButton();

		if (this.isLoading) {
			showLoadingIndicator();
		}
	}

	private void showLoadingIndicator() {
		setButton(2, 4, QuickItem
				.of(new ItemStack(Material.HOPPER))
				.name(TranslationManager.string(Translations.GUI_LOADING_INDICATOR_NAME))
				.lore(TranslationManager.list(Translations.GUI_LOADING_INDICATOR_LORE))
				.make(), click -> {});
	}

	private void setSortToggle() {
		setButton(5, 4, QuickItem
				.of(Settings.GUI_TOP_STORES_ITEMS_FILTER_ITEM.getItemStack())
				.name(TranslationManager.string(this.player, Translations.GUI_TOP_STORES_ITEMS_FILTER_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_TOP_STORES_ITEMS_FILTER_LORE,
						"store_sort_type", this.sortType.getTranslatedName(),
						"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
				.make(), click -> {
			if (this.isLoading) return;

			this.sortType = this.sortType.next();
			this.page = 1;
			sortItems();
			draw();
		});
	}

	private void setSummaryButton() {
		if (this.isLoading) {
			setButton(getRows() - 1, 8, QuickItem
					.of(Settings.GUI_TOP_STORES_ITEMS_SUMMARY_ITEM.getItemStack())
					.name(TranslationManager.string(this.player, Translations.GUI_TOP_STORES_ITEMS_SUMMARY_NAME))
					.lore(TranslationManager.list(this.player, Translations.GUI_TOP_STORES_ITEMS_SUMMARY_LOADING_LORE))
					.make(), click -> {});
			return;
		}

		final Map<StoreTier, Integer> tierCounts = StoreLevelCalculator.countByTier(this.items);

		setButton(getRows() - 1, 8, QuickItem
				.of(Settings.GUI_TOP_STORES_ITEMS_SUMMARY_ITEM.getItemStack())
				.name(TranslationManager.string(this.player, Translations.GUI_TOP_STORES_ITEMS_SUMMARY_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_TOP_STORES_ITEMS_SUMMARY_LORE,
						"tier_legendary_count", tierCounts.get(StoreTier.LEGENDARY),
						"tier_master_count", tierCounts.get(StoreTier.MASTER),
						"tier_expert_count", tierCounts.get(StoreTier.EXPERT),
						"tier_established_count", tierCounts.get(StoreTier.ESTABLISHED),
						"tier_novice_count", tierCounts.get(StoreTier.NOVICE),
						"tier_beginner_count", tierCounts.get(StoreTier.BEGINNER),
						"total_stores", this.items.size(),
						"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
				.make(), this::handleSummaryClick);
	}

	private void handleSummaryClick(final GuiClickEvent click) {
		if (this.isLoading) return;

		if (!click.player.hasPermission(EXPORT_PERMISSION)) {
			Common.tell(click.player, TranslationManager.string(click.player, Translations.GUI_TOP_STORES_EXPORT_NO_PERMISSION));
			return;
		}

		TopStoresExporter.exportAsync(
				click.player,
				this.items,
				this.sortType,
				fileName -> Common.tell(click.player, TranslationManager.string(click.player, Translations.GUI_TOP_STORES_EXPORT_SUCCESS, "file_name", fileName)),
				error -> Common.tell(click.player, TranslationManager.string(click.player, Translations.GUI_TOP_STORES_EXPORT_FAILURE, "error", error))
		);
	}

	private void sortItems() {
		if (this.items == null || this.items.isEmpty()) return;

		final List<StoreLevelSnapshot> rankings = new ArrayList<>(this.items);
		final Comparator<StoreLevelSnapshot> comparator = Comparator
				.comparingInt(StoreLevelSnapshot::level)
				.thenComparing(Comparator.comparingInt(StoreLevelSnapshot::totalSales).reversed())
				.thenComparing(snapshot -> snapshot.market().getDisplayName(), String.CASE_INSENSITIVE_ORDER);

		if (this.sortType == StoreSortType.HIGHEST_SCORE) {
			rankings.sort(comparator.reversed());
		} else {
			rankings.sort(comparator);
		}

		this.items = rankings;
	}

	@Override
	protected void onPopulateComplete() {
		if (this.isLoading || this.items == null || this.items.isEmpty()) {
			return;
		}

		loadPlayerHeadsAsync();
	}

	@Override
	protected ItemStack makeDisplayItem(StoreLevelSnapshot snapshot) {
		final Market market = snapshot.market();
		final int rank = getRankMap().getOrDefault(snapshot, 0);
		final String tier = StoreLevelCalculator.getStoreLevelTier(this.player, snapshot.level());

		return buildStoreItem(QuickItem.of(CompMaterial.PLAYER_HEAD).make(), snapshot, market, rank, tier);
	}

	private Map<StoreLevelSnapshot, Integer> getRankMap() {
		final Map<StoreLevelSnapshot, Integer> ranks = new HashMap<>();
		for (int i = 0; i < this.items.size(); i++) {
			ranks.put(this.items.get(i), i + 1);
		}
		return ranks;
	}

	private ItemStack buildStoreItem(
			@NonNull final ItemStack skull,
			@NonNull final StoreLevelSnapshot snapshot,
			@NonNull final Market market,
			final int rank,
			@NonNull final String tier
	) {
		return QuickItem.of(skull)
				.name(TranslationManager.string(this.player, Translations.GUI_TOP_STORES_ITEMS_ENTRY_NAME,
						"store_rank", rank,
						"market_name", market.getDisplayName()))
				.lore(TranslationManager.list(this.player, Translations.GUI_TOP_STORES_ITEMS_ENTRY_LORE,
						"market_owner", market.getOwnerName(),
						"store_level", snapshot.level(),
						"store_tier", tier,
						"total_sales", snapshot.totalSales(),
						"total_listings", snapshot.totalListings(),
						"avg_rating", String.format("%.1f", snapshot.avgRating()),
						"total_customers", snapshot.totalCustomers(),
						"total_reviews", snapshot.totalReviews(),
						"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
				.make();
	}

	private void loadPlayerHeadsAsync() {
		final int requestId = ++this.headLoadRequestId;
		final Map<StoreLevelSnapshot, Integer> rankMap = getRankMap();

		final List<StoreLevelSnapshot> itemsToDisplay = this.items.stream()
				.skip((page - 1) * (long) fillSlots().size())
				.limit(fillSlots().size())
				.toList();

		for (int i = 0; i < itemsToDisplay.size(); i++) {
			final StoreLevelSnapshot snapshot = itemsToDisplay.get(i);
			final Market market = snapshot.market();
			final int slotIndex = fillSlots().get(i);
			final int rank = rankMap.getOrDefault(snapshot, 0);
			final String tier = StoreLevelCalculator.getStoreLevelTier(this.player, snapshot.level());
			final OfflinePlayer owner = Bukkit.getOfflinePlayer(market.getOwnerUUID());

			PlayerHeadCache.getOrFetch(owner).thenAccept(skull -> Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
				if (requestId != this.headLoadRequestId || !isStillOpen()) {
					return;
				}

				setItem(slotIndex, buildStoreItem(skull, snapshot, market, rank, tier));
			}));
		}
	}

	@Override
	protected void onClick(StoreLevelSnapshot snapshot, GuiClickEvent click) {
		final Market market = snapshot.market();

		if (Markets.getMarketManager().isBannedFrom(market, click.player)) {
			Common.tell(click.player, TranslationManager.string(click.player, Translations.BANNED_FROM_MARKET, "market_owner", market.getOwnerName()));
			return;
		}

		if (!market.isOpen()) {
			Common.tell(click.player, TranslationManager.string(click.player, Translations.MARKET_IS_CLOSED, "market_owner", market.getOwnerName()));
			return;
		}

		click.manager.showGUI(click.player, new MarketViewGUI(this, click.player, market, false));
	}

	@Override
	protected List<Integer> fillSlots() {
		return InventoryBorder.getInsideBorders(5);
	}
}
