package ca.tweetzy.markets.model.manager;

import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.Filterer;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.manager.ListManager;
import ca.tweetzy.markets.api.market.core.AbstractMarket;
import ca.tweetzy.markets.api.market.core.Category;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketItem;
import ca.tweetzy.markets.impl.PlayerMarket;
import ca.tweetzy.markets.impl.ServerMarket;
import ca.tweetzy.markets.impl.layout.HomeLayout;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class MarketManager extends ListManager<Market> {

	public MarketManager() {
		super("Market");
	}

	/**
	 * Enhanced search that includes enchantment names for enchanted books
	 *
	 * @param keywords the search keywords
	 * @param item     the item to search
	 * @return true if the item matches the search criteria
	 */
	private boolean matchesSearch(@NonNull final String keywords, @NonNull final ItemStack item) {
		// First try the standard item info search
		if (Filterer.searchByItemInfo(keywords, item)) {
			return true;
		}

		// Check for enchantments on the item
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			String lowerKeywords = keywords.toLowerCase();

			// Check enchantments on enchanted books
			if (meta instanceof EnchantmentStorageMeta) {
				EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) meta;
				Map<Enchantment, Integer> storedEnchants = bookMeta.getStoredEnchants();

				for (Enchantment enchantment : storedEnchants.keySet()) {
					NamespacedKey key = enchantment.getKey();
					String enchantmentName = key.getKey().toLowerCase();

					// Match by enchantment name (e.g., "unbreaking", "sharpness")
					if (enchantmentName.contains(lowerKeywords)) {
						return true;
					}
				}
			}

			// Check regular enchantments on tools/armor
			Map<Enchantment, Integer> enchants = meta.getEnchants();
			for (Enchantment enchantment : enchants.keySet()) {
				NamespacedKey key = enchantment.getKey();
				String enchantmentName = key.getKey().toLowerCase();

				// Match by enchantment name
				if (enchantmentName.contains(lowerKeywords)) {
					return true;
				}
			}
		}

		return false;
	}

	public List<MarketItem> getSearchResults(@NonNull final Player searcher, @NonNull final String keywords) {
		final List<MarketItem> marketItems = new ArrayList<>();
		final List<Market> possibleSearchMarkets = getOpenMarketsExclusive(searcher).stream().filter(market -> !market.getBannedUsers().contains(searcher.getUniqueId())).toList();
//		final List<Market> possibleSearchMarkets = getOpenMarketsInclusive();

		// populate items into search list
		possibleSearchMarkets.forEach(market -> market.getCategories().forEach(category -> marketItems.addAll(category.getInStockItems())));
		return marketItems.stream().filter(marketItem -> matchesSearch(keywords, marketItem.getItem())).collect(Collectors.toList());
	}

	public List<MarketItem> getSearchResults(@NonNull final Player searcher, @NonNull final Market market, @NonNull final String keywords) {
		final List<MarketItem> marketItems = new ArrayList<>();
		market.getCategories().forEach(category -> marketItems.addAll(category.getInStockItems()));
		return marketItems.stream().filter(marketItem -> matchesSearch(keywords, marketItem.getItem())).collect(Collectors.toList());
	}

	public List<MarketItem> getSearchResults(@NonNull final Category category, @NonNull final String keywords) {
		return category.getInStockItems().stream().filter(marketItem -> matchesSearch(keywords, marketItem.getItem())).collect(Collectors.toList());
	}

	public List<Market> getOpenMarketsExclusive(@NonNull final OfflinePlayer ignoredUser) {
		return getManagerContent().stream().filter(market -> !market.getOwnerUUID().equals(ignoredUser.getUniqueId()) && market.isOpen() && !market.isEmpty()).collect(Collectors.toList());
	}

	public ServerMarket getServerMarket() {
		return getManagerContent()
				.stream()
				.filter(ServerMarket.class::isInstance)
				.map(ServerMarket.class::cast)
				.findFirst()
				.orElse(null);
	}

	public List<Market> getOpenMarketsInclusive() {
		return getManagerContent().stream().filter(Market::isOpen).collect(Collectors.toList());
	}

	public Market getByOwner(@NonNull final UUID uuid) {
		return getManagerContent().stream().filter(market -> market.getOwnerUUID().equals(uuid)).findFirst().orElse(null);
	}

	public Market getByOwnerName(@NonNull final String ownerName) {
		return getManagerContent().stream().filter(market -> market.getOwnerName().equalsIgnoreCase(ownerName)).findFirst().orElse(null);
	}

	public Market getByUUID(@NonNull final UUID uuid) {
		return getManagerContent().stream().filter(market -> market.getId().equals(uuid)).findFirst().orElse(null);
	}

	public boolean isBannedFrom(@NonNull final Market market, @NonNull final UUID user) {
		return market.getBannedUsers().contains(user);
	}

	public boolean isBannedFrom(@NonNull final Market market, @NonNull final OfflinePlayer user) {
		return isBannedFrom(market, user.getUniqueId());
	}

	public void create(@NonNull final Player player, @NonNull final Consumer<Boolean> created) {
		if (Settings.CREATION_COST_ENABLED.getBoolean()) {

			// item only mode
			if (Settings.CURRENCY_USE_ITEM_ONLY.getBoolean()) {
				if (!Markets.getCurrencyManager().has(player, Settings.CURRENCY_ITEM_DEFAULT_SELECTED.getItemStack(), (int) Settings.CREATION_COST_COST.getDouble())) {
					Common.tell(player, TranslationManager.string(player, Translations.CANNOT_PAY_CREATION_FEE));
					created.accept(false);
					return;
				}

				// withdraw the creation cost
				Markets.getCurrencyManager().withdraw(player, Settings.CURRENCY_ITEM_DEFAULT_SELECTED.getItemStack(), (int) Settings.CREATION_COST_COST.getDouble());


			} else {
				if (!Markets.getEconomy().has(player, Settings.CREATION_COST_COST.getDouble())) {
					Common.tell(player, TranslationManager.string(player, Translations.CANNOT_PAY_CREATION_FEE));
					created.accept(false);
					return;
				}

				// withdraw the creation cost
				Markets.getEconomy().withdrawPlayer(player, Settings.CREATION_COST_COST.getDouble());
			}
		}

		final Market market = new PlayerMarket(
				UUID.randomUUID(),
				player.getUniqueId(),
				player.getName(),
				TranslationManager.string(player, Translations.DEFAULTS_MARKET_DISPLAY_NAME, "player_name", player.getName()),
				TranslationManager.list(player, Translations.DEFAULTS_MARKET_DESCRIPTION),
				new ArrayList<>(),
				new ArrayList<>(),
				new ArrayList<>(),
				true,
				false,
				new HomeLayout(),
				new HomeLayout(),
				System.currentTimeMillis(),
				System.currentTimeMillis()
		);

		market.store(storedMarket -> {
			if (storedMarket != null) {
				add(storedMarket);
				created.accept(true);
			} else {
				created.accept(false);
			}
		});
	}

	@Override
	public void load() {
		clear();

		Markets.getDataManager().getMarkets((error, found) -> {
			if (error != null) return;
			Common.log("&aLoading Markets");
			found.forEach(market -> {
				Markets.getDataManager().getRatingsByMarket(market.getId(), (ratingError, foundRatings) -> {
					if (ratingError != null) return;
					market.getRatings().addAll(foundRatings);
					add(market);
				});
			});

			// after markets have been added let's load categories
			Markets.getCategoryManager().load();
		});
	}
}
