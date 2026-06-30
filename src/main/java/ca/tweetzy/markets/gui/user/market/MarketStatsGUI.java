package ca.tweetzy.markets.gui.user.market;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.settings.TranslationEntry;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.Transaction;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.gui.MarketsBaseGUI;
import ca.tweetzy.markets.gui.shared.MarketsMainGUI;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import ca.tweetzy.markets.util.MarketSalesPeriodStatsCalculator;
import ca.tweetzy.markets.util.MarketSalesPeriodStatsCalculator.PeriodStats;
import ca.tweetzy.markets.util.MarketSalesPeriodStatsCalculator.SalesPeriodSnapshot;
import ca.tweetzy.markets.util.MessageLinks;
import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class MarketStatsGUI extends MarketsBaseGUI {

	private final Player player;
	private final Market market;

	private List<Transaction> sales = List.of();
	private List<Transaction> purchases = List.of();
	private SalesPeriodSnapshot shopSnapshot;
	private SalesPeriodSnapshot serverSnapshot;

	private boolean isLoading = true;
	private int loadRequestId = 0;

	public MarketStatsGUI(@NonNull final Player player, @NonNull final Market market) {
		super(new MarketsMainGUI(player), player, TranslationManager.string(player, Translations.GUI_MARKET_STATS_TITLE), 6);
		this.player = player;
		this.market = market;
		setDefaultItem(QuickItem.bg(Settings.GUI_MARKET_STATS_BACKGROUND.getItemStack()));
		setOnOpen(open -> draw());
		loadDataAsync();
	}

	@Override
	protected void draw() {
		if (!this.player.hasPermission("markets.viewstats")) {
			this.player.closeInventory();
			return;
		}

		drawInventoryStats();
		drawWebsiteButton();

		if (this.isLoading || this.shopSnapshot == null || this.serverSnapshot == null) {
			drawLoadingWing();
			drawLoadingCenterStats();
		} else {
			drawStoreLevelRating();
			drawSalesStats();
			drawPurchaseStats();
			drawShopPeriodStats();
			drawServerPeriodStats();
			drawShopInsightStats();
			drawServerInsightStats();
		}

		applyBackExit();
	}

	private void loadDataAsync() {
		final int requestId = ++this.loadRequestId;
		this.isLoading = true;
		this.shopSnapshot = null;
		this.serverSnapshot = null;

		final AtomicInteger pending = new AtomicInteger(3);
		final Runnable tryFinish = () -> {
			if (pending.decrementAndGet() != 0) return;
			if (requestId != this.loadRequestId) return;

			this.isLoading = false;
			if (isStillOpen()) {
				draw();
			}
		};

		Markets.getTransactionManager().getSalesTransactionsForAsync(this.market.getOwnerUUID(), transactions -> {
			if (requestId != this.loadRequestId) return;
			this.sales = transactions;
			this.shopSnapshot = MarketSalesPeriodStatsCalculator.calculate(transactions);
			tryFinish.run();
		});

		Markets.getTransactionManager().getAllTransactionsForAsync(transactions -> {
			if (requestId != this.loadRequestId) return;
			this.serverSnapshot = MarketSalesPeriodStatsCalculator.calculate(transactions);
			tryFinish.run();
		});

		Markets.getTransactionManager().getPurchaseTransactionsForAsync(this.market.getOwnerUUID(), transactions -> {
			if (requestId != this.loadRequestId) return;
			this.purchases = transactions;
			tryFinish.run();
		});
	}

	private boolean isStillOpen() {
		return this.player.isOnline() && this.player.getOpenInventory().getTopInventory().equals(this.inventory);
	}

	private void drawLoadingWing() {
		final List<String> loadingLore = TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_LOADING_LORE);

		setDisplayButton(1, 4, Material.SUNFLOWER, Translations.GUI_MARKET_STATS_ITEMS_SHOP_YESTERDAY_NAME, loadingLore);
		setDisplayButton(2, 4, Material.CLOCK, Translations.GUI_MARKET_STATS_ITEMS_SHOP_SEVEN_DAY_NAME, loadingLore);
		setDisplayButton(3, 4, Material.PAPER, Translations.GUI_MARKET_STATS_ITEMS_SHOP_THIRTY_DAY_NAME, loadingLore);
		setDisplayButton(1, 5, Material.GOLD_INGOT, Translations.GUI_MARKET_STATS_ITEMS_SERVER_YESTERDAY_NAME, loadingLore);
		setDisplayButton(2, 5, Material.HOPPER, Translations.GUI_MARKET_STATS_ITEMS_SERVER_SEVEN_DAY_NAME, loadingLore);
		setDisplayButton(3, 5, Material.ENDER_CHEST, Translations.GUI_MARKET_STATS_ITEMS_SERVER_THIRTY_DAY_NAME, loadingLore);
		setDisplayButton(1, 7, Material.PLAYER_HEAD, Translations.GUI_MARKET_STATS_ITEMS_SHOP_BUYERS_NAME, loadingLore);
		setDisplayButton(2, 7, Material.EMERALD, Translations.GUI_MARKET_STATS_ITEMS_SHOP_AVG_ORDER_NAME, loadingLore);
		setDisplayButton(3, 7, Material.COMPARATOR, Translations.GUI_MARKET_STATS_ITEMS_SHOP_TREND_NAME, loadingLore);
		setDisplayButton(1, 8, Material.SKELETON_SKULL, Translations.GUI_MARKET_STATS_ITEMS_SERVER_BUYERS_NAME, loadingLore);
		setDisplayButton(2, 8, Material.DIAMOND, Translations.GUI_MARKET_STATS_ITEMS_SERVER_AVG_ORDER_NAME, loadingLore);
		setDisplayButton(3, 8, Material.REDSTONE_TORCH, Translations.GUI_MARKET_STATS_ITEMS_SERVER_TREND_NAME, loadingLore);
	}

	private void drawLoadingCenterStats() {
		final List<String> loadingLore = TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_LOADING_LORE);

		setDisplayButton(1, 2, CompMaterial.DIRT, Translations.GUI_MARKET_STATS_ITEMS_LEVEL_NAME,
				TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_LEVEL_LORE,
						"store_level", "...",
						"store_tier", "...",
						"total_sales", "...",
						"total_reviews", "...",
						"avg_rating", "...",
						"total_customers", "...",
						"active_bans", "..."
				));
		setDisplayButton(3, 2, Material.GOLD_INGOT, Translations.GUI_MARKET_STATS_ITEMS_SALES_NAME, loadingLore);
		setDisplayButton(4, 2, Material.BLACK_WOOL, Translations.GUI_MARKET_STATS_ITEMS_PURCHASES_NAME, loadingLore);
	}

	private void drawShopPeriodStats() {
		drawPeriodStatsButton(1, 4, Material.SUNFLOWER,
				Translations.GUI_MARKET_STATS_ITEMS_SHOP_YESTERDAY_NAME,
				Translations.GUI_MARKET_STATS_ITEMS_SHOP_YESTERDAY_LORE,
				this.shopSnapshot.getYesterday(), false);
		drawPeriodStatsButton(2, 4, Material.CLOCK,
				Translations.GUI_MARKET_STATS_ITEMS_SHOP_SEVEN_DAY_NAME,
				Translations.GUI_MARKET_STATS_ITEMS_SHOP_SEVEN_DAY_LORE,
				this.shopSnapshot.getSevenDay(), true);
		drawPeriodStatsButton(3, 4, Material.PAPER,
				Translations.GUI_MARKET_STATS_ITEMS_SHOP_THIRTY_DAY_NAME,
				Translations.GUI_MARKET_STATS_ITEMS_SHOP_THIRTY_DAY_LORE,
				this.shopSnapshot.getThirtyDay(), true);
	}

	private void drawServerPeriodStats() {
		drawPeriodStatsButton(1, 5, Material.GOLD_INGOT,
				Translations.GUI_MARKET_STATS_ITEMS_SERVER_YESTERDAY_NAME,
				Translations.GUI_MARKET_STATS_ITEMS_SERVER_YESTERDAY_LORE,
				this.serverSnapshot.getYesterday(), false);
		drawPeriodStatsButton(2, 5, Material.HOPPER,
				Translations.GUI_MARKET_STATS_ITEMS_SERVER_SEVEN_DAY_NAME,
				Translations.GUI_MARKET_STATS_ITEMS_SERVER_SEVEN_DAY_LORE,
				this.serverSnapshot.getSevenDay(), true);
		drawPeriodStatsButton(3, 5, Material.ENDER_CHEST,
				Translations.GUI_MARKET_STATS_ITEMS_SERVER_THIRTY_DAY_NAME,
				Translations.GUI_MARKET_STATS_ITEMS_SERVER_THIRTY_DAY_LORE,
				this.serverSnapshot.getThirtyDay(), true);
	}

	private void drawShopInsightStats() {
		setDisplayButton(1, 7, Material.PLAYER_HEAD,
				Translations.GUI_MARKET_STATS_ITEMS_SHOP_BUYERS_NAME,
				TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_SHOP_BUYERS_LORE,
						"unique_buyers", this.shopSnapshot.getSevenDayUniqueBuyers(),
						"repeat_buyers", this.shopSnapshot.getSevenDayRepeatBuyers(),
						"new_buyers", this.shopSnapshot.getSevenDayNewBuyers()
				));

		setDisplayButton(2, 7, Material.EMERALD,
				Translations.GUI_MARKET_STATS_ITEMS_SHOP_AVG_ORDER_NAME,
				TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_SHOP_AVG_ORDER_LORE,
						"avg_order_value", formatCurrency(this.shopSnapshot.getSevenDayAvgOrderValue()),
						"median_order_value", formatCurrency(this.shopSnapshot.getSevenDayMedianOrderValue()),
						"largest_order", formatCurrency(this.shopSnapshot.getSevenDayLargestOrder())
				));

		setDisplayButton(3, 7, Material.COMPARATOR,
				Translations.GUI_MARKET_STATS_ITEMS_SHOP_TREND_NAME,
				TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_SHOP_TREND_LORE,
						"current_revenue", formatCurrency(this.shopSnapshot.getCurrentSevenDayRevenue()),
						"previous_revenue", formatCurrency(this.shopSnapshot.getPreviousSevenDayRevenue()),
						"change_percent", formatPercent(this.shopSnapshot.getChangePercent()),
						"change_direction", this.shopSnapshot.getChangeDirection()
				));
	}

	private void drawServerInsightStats() {
		setDisplayButton(1, 8, Material.SKELETON_SKULL,
				Translations.GUI_MARKET_STATS_ITEMS_SERVER_BUYERS_NAME,
				TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_SERVER_BUYERS_LORE,
						"unique_buyers", this.serverSnapshot.getSevenDayUniqueBuyers(),
						"repeat_buyers", this.serverSnapshot.getSevenDayRepeatBuyers(),
						"new_buyers", this.serverSnapshot.getSevenDayNewBuyers()
				));

		setDisplayButton(2, 8, Material.DIAMOND,
				Translations.GUI_MARKET_STATS_ITEMS_SERVER_AVG_ORDER_NAME,
				TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_SERVER_AVG_ORDER_LORE,
						"avg_order_value", formatCurrency(this.serverSnapshot.getSevenDayAvgOrderValue()),
						"median_order_value", formatCurrency(this.serverSnapshot.getSevenDayMedianOrderValue()),
						"largest_order", formatCurrency(this.serverSnapshot.getSevenDayLargestOrder())
				));

		setDisplayButton(3, 8, Material.REDSTONE_TORCH,
				Translations.GUI_MARKET_STATS_ITEMS_SERVER_TREND_NAME,
				TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_SERVER_TREND_LORE,
						"current_revenue", formatCurrency(this.serverSnapshot.getCurrentSevenDayRevenue()),
						"previous_revenue", formatCurrency(this.serverSnapshot.getPreviousSevenDayRevenue()),
						"change_percent", formatPercent(this.serverSnapshot.getChangePercent()),
						"change_direction", this.serverSnapshot.getChangeDirection()
				));
	}

	private void drawPeriodStatsButton(
			final int row,
			final int col,
			@NonNull final Material material,
			@NonNull final TranslationEntry nameEntry,
			@NonNull final TranslationEntry loreEntry,
			@NonNull final PeriodStats stats,
			final boolean includeAvgOrder
	) {
		if (includeAvgOrder) {
			setDisplayButton(row, col, material, nameEntry,
					TranslationManager.list(this.player, loreEntry,
							"orders", stats.getOrders(),
							"items_sold", stats.getItemsSold(),
							"revenue", formatCurrency(stats.getRevenue()),
							"unique_buyers", stats.getUniqueBuyers(),
							"avg_order_value", formatCurrency(stats.getAvgOrderValue())
					));
			return;
		}

		setDisplayButton(row, col, material, nameEntry,
				TranslationManager.list(this.player, loreEntry,
						"orders", stats.getOrders(),
						"items_sold", stats.getItemsSold(),
						"revenue", formatCurrency(stats.getRevenue()),
						"unique_buyers", stats.getUniqueBuyers()
				));
	}

	private void drawWebsiteButton() {
		if (!Settings.GUI_MARKET_STATS_ITEMS_WEBSITE_ENABLED.getBoolean()) return;

		final String url = Settings.GUI_MARKET_STATS_ITEMS_WEBSITE_URL.getString();
		setButton(4, 0, QuickItem
				.of(Settings.GUI_MARKET_STATS_ITEMS_WEBSITE_ITEM.getItemStack())
				.name(TranslationManager.string(this.player, Translations.GUI_MARKET_STATS_ITEMS_WEBSITE_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_WEBSITE_LORE,
						"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
				.make(), click -> {
			final String linkText = TranslationManager.string(this.player, Translations.GUI_MARKET_STATS_ITEMS_WEBSITE_LINK);
			MessageLinks.openUrl(click.player, url, linkText);
		});
	}

	private void setDisplayButton(
			final int row,
			final int col,
			@NonNull final Material material,
			@NonNull final TranslationEntry nameEntry,
			@NonNull final List<String> lore
	) {
		setButton(row, col, QuickItem
				.of(new ItemStack(material))
				.name(TranslationManager.string(this.player, nameEntry))
				.lore(lore)
				.make(), click -> {});
	}

	private void setDisplayButton(
			final int row,
			final int col,
			@NonNull final CompMaterial material,
			@NonNull final TranslationEntry nameEntry,
			@NonNull final List<String> lore
	) {
		setButton(row, col, QuickItem
				.of(material)
				.name(TranslationManager.string(this.player, nameEntry, "store_level", "..."))
				.lore(lore)
				.make(), click -> {});
	}

	private void drawStoreLevelRating() {
		int totalSales = getSalesTotalQuantity();
		int totalListings = getTotalListings();
		double avgRating = this.market.getRatings().isEmpty() ? 0 : this.market.getReviewAvg();
		int totalCustomers = getUniqueCustomers();
		int totalReviews = this.market.getRatings().size();
		int activeBans = this.market.getBannedUsers().size();

		int level = calculateStoreLevel(totalSales, totalListings, avgRating, totalCustomers);
		String levelTier = getStoreLevelTier(level);
		CompMaterial levelIcon = getStoreLevelIcon(level);

		setButton(1, 2, QuickItem
				.of(levelIcon)
				.name(TranslationManager.string(this.player, Translations.GUI_MARKET_STATS_ITEMS_LEVEL_NAME,
						"store_level", level))
				.lore(TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_LEVEL_LORE,
						"store_tier", levelTier,
						"total_sales", totalSales,
						"total_reviews", totalReviews,
						"avg_rating", String.format("%.1f", avgRating),
						"total_customers", totalCustomers,
						"active_bans", activeBans
				))
				.make(), click -> {});
	}

	private void drawSalesStats() {
		int totalSales = this.sales.size();
		int totalQuantity = this.sales.stream().mapToInt(Transaction::getQuantity).sum();
		double totalRevenue = this.sales.stream().mapToDouble(Transaction::getPrice).sum();

		List<String> topSoldItems = getTopSoldItems(3);
		List<String> topSalesByPrice = getTopSalesByPrice(3);

		setButton(3, 2, QuickItem
				.of(new ItemStack(Material.GOLD_INGOT))
				.name(TranslationManager.string(this.player, Translations.GUI_MARKET_STATS_ITEMS_SALES_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_SALES_LORE,
						"total_sales", totalSales,
						"total_quantity", totalQuantity,
						"total_revenue", formatCurrency(totalRevenue),
						"top_item_1", !topSoldItems.isEmpty() ? topSoldItems.get(0) : "None",
						"top_item_2", topSoldItems.size() > 1 ? topSoldItems.get(1) : "None",
						"top_item_3", topSoldItems.size() > 2 ? topSoldItems.get(2) : "None",
						"top_sale_1", !topSalesByPrice.isEmpty() ? topSalesByPrice.get(0) : "None",
						"top_sale_2", topSalesByPrice.size() > 1 ? topSalesByPrice.get(1) : "None",
						"top_sale_3", topSalesByPrice.size() > 2 ? topSalesByPrice.get(2) : "None"
				))
				.make(), click -> {});
	}

	private void drawPurchaseStats() {
		int totalPurchases = this.purchases.size();
		int totalQuantity = this.purchases.stream().mapToInt(Transaction::getQuantity).sum();
		double totalSpent = this.purchases.stream().mapToDouble(Transaction::getPrice).sum();

		List<String> topBoughtItems = getTopBoughtItems(this.purchases, 3);
		List<String> topPurchasesByPrice = getTopPurchasesByPrice(this.purchases, 3);

		setButton(4, 2, QuickItem
				.of(new ItemStack(Material.BLACK_WOOL))
				.name(TranslationManager.string(this.player, Translations.GUI_MARKET_STATS_ITEMS_PURCHASES_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_PURCHASES_LORE,
						"total_purchases", totalPurchases,
						"total_quantity", totalQuantity,
						"total_spent", formatCurrency(totalSpent),
						"top_item_1", !topBoughtItems.isEmpty() ? topBoughtItems.get(0) : "None",
						"top_item_2", topBoughtItems.size() > 1 ? topBoughtItems.get(1) : "None",
						"top_item_3", topBoughtItems.size() > 2 ? topBoughtItems.get(2) : "None",
						"top_purchase_1", !topPurchasesByPrice.isEmpty() ? topPurchasesByPrice.get(0) : "None",
						"top_purchase_2", topPurchasesByPrice.size() > 1 ? topPurchasesByPrice.get(1) : "None",
						"top_purchase_3", topPurchasesByPrice.size() > 2 ? topPurchasesByPrice.get(2) : "None"
				))
				.make(), click -> {});
	}

	private void drawInventoryStats() {
		int totalListings = getTotalListings();
		int inStock = getInStockCount();
		int outOfStock = getOutOfStockCount();
		int totalCategories = this.market.getCategories().size();

		setButton(2, 2, QuickItem
				.of(new ItemStack(Material.CHEST))
				.name(TranslationManager.string(this.player, Translations.GUI_MARKET_STATS_ITEMS_INVENTORY_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_INVENTORY_LORE,
						"total_categories", totalCategories,
						"total_listings", totalListings,
						"in_stock", inStock,
						"out_of_stock", outOfStock
				))
				.make(), click -> {});
	}

	private int getTotalListings() {
		return this.market.getCategories().stream()
				.mapToInt(category -> category.getItems().size())
				.sum();
	}

	private int getInStockCount() {
		return (int) this.market.getCategories().stream()
				.flatMap(category -> category.getItems().stream())
				.filter(item -> item.getStock() > 0)
				.count();
	}

	private int getOutOfStockCount() {
		return (int) this.market.getCategories().stream()
				.flatMap(category -> category.getItems().stream())
				.filter(item -> item.getStock() == 0)
				.count();
	}

	private int getSalesTotalQuantity() {
		return this.sales.stream()
				.mapToInt(Transaction::getQuantity)
				.sum();
	}

	private int getUniqueCustomers() {
		return (int) this.sales.stream()
				.map(Transaction::getBuyer)
				.distinct()
				.count();
	}

	private int calculateStoreLevel(int totalSales, int totalListings, double avgRating, int totalCustomers) {
		int salesScore = Math.min(totalSales / 50, 3);
		int listingsScore = Math.min(totalListings / 10, 2);
		int ratingScore = (int) Math.min(avgRating, 3);
		int customerScore = Math.min(totalCustomers / 5, 2);

		return salesScore + listingsScore + ratingScore + customerScore;
	}

	private String getStoreLevelTier(int level) {
		if (level >= 9) return TranslationManager.string(this.player, Translations.STORE_TIER_LEGENDARY);
		if (level >= 7) return TranslationManager.string(this.player, Translations.STORE_TIER_MASTER);
		if (level >= 5) return TranslationManager.string(this.player, Translations.STORE_TIER_EXPERT);
		if (level >= 3) return TranslationManager.string(this.player, Translations.STORE_TIER_ESTABLISHED);
		if (level >= 1) return TranslationManager.string(this.player, Translations.STORE_TIER_NOVICE);
		return TranslationManager.string(this.player, Translations.STORE_TIER_BEGINNER);
	}

	private CompMaterial getStoreLevelIcon(int level) {
		if (level >= 9) return CompMaterial.NETHER_STAR;
		if (level >= 7) return CompMaterial.DIAMOND;
		if (level >= 5) return CompMaterial.GOLD_BLOCK;
		if (level >= 3) return CompMaterial.IRON_BLOCK;
		if (level >= 1) return CompMaterial.COPPER_BLOCK;
		return CompMaterial.DIRT;
	}

	private List<String> getTopSoldItems(int limit) {
		Map<String, Integer> itemSales = new HashMap<>();

		for (Transaction transaction : this.sales) {
			String itemName = getItemName(transaction);
			itemSales.merge(itemName, transaction.getQuantity(), Integer::sum);
		}

		return itemSales.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.limit(limit)
				.map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
				.collect(Collectors.toList());
	}

	private List<String> getTopBoughtItems(List<Transaction> purchaseList, int limit) {
		Map<String, Integer> itemPurchases = new HashMap<>();

		for (Transaction transaction : purchaseList) {
			String itemName = getItemName(transaction);
			itemPurchases.merge(itemName, transaction.getQuantity(), Integer::sum);
		}

		return itemPurchases.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.limit(limit)
				.map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
				.collect(Collectors.toList());
	}

	private List<String> getTopSalesByPrice(int limit) {
		Map<String, Double> itemRevenue = new HashMap<>();

		for (Transaction transaction : this.sales) {
			String itemName = getItemName(transaction);
			itemRevenue.merge(itemName, transaction.getPrice(), Double::sum);
		}

		return itemRevenue.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(limit)
				.map(entry -> entry.getKey() + " ($" + formatCurrency(entry.getValue()) + ")")
				.collect(Collectors.toList());
	}

	private List<String> getTopPurchasesByPrice(List<Transaction> purchaseList, int limit) {
		Map<String, Double> itemSpent = new HashMap<>();

		for (Transaction transaction : purchaseList) {
			String itemName = getItemName(transaction);
			itemSpent.merge(itemName, transaction.getPrice(), Double::sum);
		}

		return itemSpent.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(limit)
				.map(entry -> entry.getKey() + " ($" + formatCurrency(entry.getValue()) + ")")
				.collect(Collectors.toList());
	}

	private String getItemName(@NonNull final Transaction transaction) {
		String itemName = transaction.getItem().getType().name();
		if (transaction.getItem().hasItemMeta() && transaction.getItem().getItemMeta().hasDisplayName()) {
			itemName = transaction.getItem().getItemMeta().getDisplayName();
		}
		return itemName;
	}

	private String formatCurrency(double amount) {
		return new DecimalFormat("#,##0.00").format(amount);
	}

	private String formatPercent(double percent) {
		return new DecimalFormat("#,##0.0").format(percent);
	}
}
