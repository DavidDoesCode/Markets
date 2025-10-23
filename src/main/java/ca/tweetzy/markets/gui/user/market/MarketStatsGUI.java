package ca.tweetzy.markets.gui.user.market;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.Transaction;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.gui.MarketsBaseGUI;
import ca.tweetzy.markets.gui.shared.MarketsMainGUI;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public final class MarketStatsGUI extends MarketsBaseGUI {

	private final Player player;
	private final Market market;

	private final List<Transaction> sales;

	public MarketStatsGUI(@NonNull final Player player, @NonNull final Market market) {
		super(new MarketsMainGUI(player), player, TranslationManager.string(player, Translations.GUI_MARKET_STATS_TITLE), 6);
		this.player = player;
		this.market = market;
		setDefaultItem(QuickItem.bg(Settings.GUI_MARKET_STATS_BACKGROUND.getItemStack()));

		this.sales = Markets.getTransactionManager().getManagerContent().stream()
				.filter(t -> t.getSeller().equals(this.market.getOwnerUUID())).toList();

		draw();
	}

	@Override
	protected void draw() {
		// Check permission
		if (!this.player.hasPermission("markets.viewstats")) {
			this.player.closeInventory();
			return;
		}

		drawStoreLevelRating();
		drawSalesStats();
		drawPurchaseStats();
		drawInventoryStats();
//		drawCustomerStats();

		applyBackExit();
	}

	private void drawStoreLevelRating() {
		// Calculate store level based on multiple factors
		int totalSales = getSalesTotalQuantity();
		int totalListings = getTotalListings();
		double avgRating = this.market.getRatings().isEmpty() ? 0 : this.market.getReviewAvg();
		int totalCustomers = getUniqueCustomers();
		int totalReviews = this.market.getRatings().size();
		int activeBans = this.market.getBannedUsers().size();

		// Level calculation (0-10 scale)
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
						"total_listings", totalListings,
						"total_reviews", totalReviews,
						"avg_rating", String.format("%.1f", avgRating),
						"total_customers", totalCustomers,
						"active_bans", activeBans
				))
				.make(), click -> {});
	}

	private void drawSalesStats() {
		int totalSales = sales.size();
		int totalQuantity = sales.stream().mapToInt(Transaction::getQuantity).sum();
		double totalRevenue = sales.stream().mapToDouble(Transaction::getPrice).sum();

		// Get top 3 sold items by quantity
		List<String> topSoldItems = getTopSoldItems(3);

		// Get top 3 sales by price
		List<String> topSalesByPrice = getTopSalesByPrice(3);

		setButton(3, 2, QuickItem
				.of(new ItemStack(Material.GOLD_INGOT))
				.name(TranslationManager.string(this.player, Translations.GUI_MARKET_STATS_ITEMS_SALES_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_SALES_LORE,
						"total_sales", totalSales,
						"total_quantity", totalQuantity,
						"total_revenue", formatCurrency(totalRevenue),
						"top_item_1", topSoldItems.size() > 0 ? topSoldItems.get(0) : "None",
						"top_item_2", topSoldItems.size() > 1 ? topSoldItems.get(1) : "None",
						"top_item_3", topSoldItems.size() > 2 ? topSoldItems.get(2) : "None",
						"top_sale_1", topSalesByPrice.size() > 0 ? topSalesByPrice.get(0) : "None",
						"top_sale_2", topSalesByPrice.size() > 1 ? topSalesByPrice.get(1) : "None",
						"top_sale_3", topSalesByPrice.size() > 2 ? topSalesByPrice.get(2) : "None"
				))
				.make(), click -> {});
	}

	private void drawPurchaseStats() {
		List<Transaction> purchases = Markets.getTransactionManager().getManagerContent().stream()
				.filter(t -> t.getBuyer().equals(this.market.getOwnerUUID()))
				.toList();

		int totalPurchases = purchases.size();
		int totalQuantity = purchases.stream().mapToInt(Transaction::getQuantity).sum();
		double totalSpent = purchases.stream().mapToDouble(Transaction::getPrice).sum();

		// Get top 3 bought items by quantity
		List<String> topBoughtItems = getTopBoughtItems(purchases, 3);

		// Get top 3 purchases by price
		List<String> topPurchasesByPrice = getTopPurchasesByPrice(purchases, 3);

		setButton(4, 2, QuickItem
				.of(new ItemStack(Material.BLACK_BUNDLE))
				.name(TranslationManager.string(this.player, Translations.GUI_MARKET_STATS_ITEMS_PURCHASES_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_PURCHASES_LORE,
						"total_purchases", totalPurchases,
						"total_quantity", totalQuantity,
						"total_spent", formatCurrency(totalSpent),
						"top_item_1", topBoughtItems.size() > 0 ? topBoughtItems.get(0) : "None",
						"top_item_2", topBoughtItems.size() > 1 ? topBoughtItems.get(1) : "None",
						"top_item_3", topBoughtItems.size() > 2 ? topBoughtItems.get(2) : "None",
						"top_purchase_1", topPurchasesByPrice.size() > 0 ? topPurchasesByPrice.get(0) : "None",
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

	private void drawCustomerStats() {
		int uniqueCustomers = getUniqueCustomers();
		int totalReviews = this.market.getRatings().size();
		double avgRating = this.market.getRatings().isEmpty() ? 0 : this.market.getReviewAvg();
		int activeBans = this.market.getBannedUsers().size();

		setButton(3, 3, QuickItem
				.of(new ItemStack(Material.PLAYER_HEAD))
				.name(TranslationManager.string(this.player, Translations.GUI_MARKET_STATS_ITEMS_CUSTOMERS_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_MARKET_STATS_ITEMS_CUSTOMERS_LORE,
						"unique_customers", uniqueCustomers,
						"total_reviews", totalReviews,
						"avg_rating", String.format("%.1f", avgRating),
						"active_bans", activeBans
				))
				.make(), click -> {});
	}

	// Helper methods for stats calculation
	private int getTotalListings() {
		return this.market.getCategories().stream()
				.mapToInt(category -> category.getItems().size())
				.sum();
	}

	private int getInStockCount() {
		return this.market.getCategories().stream()
				.flatMap(category -> category.getItems().stream())
				.filter(item -> item.getStock() > 0)
				.toList()
				.size();
	}

	private int getOutOfStockCount() {
		return this.market.getCategories().stream()
				.flatMap(category -> category.getItems().stream())
				.filter(item -> item.getStock() == 0)
				.toList()
				.size();
	}

	private int getSalesTotalQuantity() {
		return sales.stream()
				.mapToInt(Transaction::getQuantity)
				.sum();
	}

	private int getUniqueCustomers() {
		return (int) sales.stream()
				.map(Transaction::getBuyer)
				.distinct()
				.count();
	}

	private int calculateStoreLevel(int totalSales, int totalListings, double avgRating, int totalCustomers) {
		// Weighted scoring system
		int salesScore = Math.min(totalSales / 50, 3);  // 0-3 points (max at 150 sales)
		int listingsScore = Math.min(totalListings / 10, 2);  // 0-2 points (max at 20 listings)
		int ratingScore = (int) Math.min(avgRating, 3);  // 0-3 points (based on 5-star rating)
		int customerScore = Math.min(totalCustomers / 5, 2);  // 0-2 points (max at 10 customers)

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
		// Group transactions by item name and sum quantities
		Map<String, Integer> itemSales = new HashMap<>();

		for (Transaction transaction : sales) {
			String itemName = transaction.getItem().getType().name();
			if (transaction.getItem().hasItemMeta() && transaction.getItem().getItemMeta().hasDisplayName()) {
				itemName = transaction.getItem().getItemMeta().getDisplayName();
			}
			itemSales.merge(itemName, transaction.getQuantity(), Integer::sum);
		}

		// Sort by quantity and get top items
		return itemSales.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.limit(limit)
				.map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
				.collect(Collectors.toList());
	}

	private List<String> getTopBoughtItems(List<Transaction> purchases, int limit) {
		// Group transactions by item name and sum quantities
		Map<String, Integer> itemPurchases = new HashMap<>();

		for (Transaction transaction : purchases) {
			String itemName = transaction.getItem().getType().name();
			if (transaction.getItem().hasItemMeta() && transaction.getItem().getItemMeta().hasDisplayName()) {
				itemName = transaction.getItem().getItemMeta().getDisplayName();
			}
			itemPurchases.merge(itemName, transaction.getQuantity(), Integer::sum);
		}

		// Sort by quantity and get top items
		return itemPurchases.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.limit(limit)
				.map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
				.collect(Collectors.toList());
	}

	private String formatCurrency(double amount) {
		DecimalFormat formatter = new DecimalFormat("#,##0.00");
		return formatter.format(amount);
	}

	private List<String> getTopSalesByPrice(int limit) {
		// Group transactions by item name and sum total revenue (price * quantity)
		Map<String, Double> itemRevenue = new HashMap<>();

		for (Transaction transaction : sales) {
			String itemName = transaction.getItem().getType().name();
			if (transaction.getItem().hasItemMeta() && transaction.getItem().getItemMeta().hasDisplayName()) {
				itemName = transaction.getItem().getItemMeta().getDisplayName();
			}
			double revenue = transaction.getPrice();
			itemRevenue.merge(itemName, revenue, Double::sum);
		}

		// Sort by revenue and get top items
		return itemRevenue.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(limit)
				.map(entry -> entry.getKey() + " ($" + formatCurrency(entry.getValue()) + ")")
				.collect(Collectors.toList());
	}

	private List<String> getTopPurchasesByPrice(List<Transaction> purchases, int limit) {
		// Group transactions by item name and sum total spent
		Map<String, Double> itemSpent = new HashMap<>();

		for (Transaction transaction : purchases) {
			String itemName = transaction.getItem().getType().name();
			if (transaction.getItem().hasItemMeta() && transaction.getItem().getItemMeta().hasDisplayName()) {
				itemName = transaction.getItem().getItemMeta().getDisplayName();
			}
			double spent = transaction.getPrice();
			itemSpent.merge(itemName, spent, Double::sum);
		}

		// Sort by amount spent and get top items
		return itemSpent.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(limit)
				.map(entry -> entry.getKey() + " ($" + formatCurrency(entry.getValue()) + ")")
				.collect(Collectors.toList());
	}
}
