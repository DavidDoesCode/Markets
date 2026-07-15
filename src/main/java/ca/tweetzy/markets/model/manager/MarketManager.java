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
import ca.tweetzy.markets.model.LiteBansBanCheck;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class MarketManager extends ListManager<Market> {

	private final Set<UUID> bannedOwnerUUIDs = ConcurrentHashMap.newKeySet();
	private final Set<String> bannedOwnerNames = ConcurrentHashMap.newKeySet();
	private final Set<UUID> bukkitBannedOwnerUUIDs = ConcurrentHashMap.newKeySet();
	private final Set<String> bukkitBannedOwnerNames = ConcurrentHashMap.newKeySet();
	private final Set<UUID> liteBansBannedOwnerUUIDs = ConcurrentHashMap.newKeySet();
	private final Set<String> liteBansBannedOwnerNames = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean liteBansRefreshInFlight = new AtomicBoolean(false);
	private long bannedCacheLastRefresh = 0L;
	private boolean liteBansListenersRegistered = false;

	public MarketManager() {
		super("Market");
	}

	/**
	 * Registers LiteBans ban/unban listeners (once) so the owner-ban cache updates
	 * immediately instead of waiting for the TTL. Safe to call when LiteBans is absent.
	 */
	public void registerLiteBansListeners() {
		if (this.liteBansListenersRegistered || !LiteBansBanCheck.isAvailable())
			return;

		LiteBansBanCheck.registerBanListeners((uuid, banned) ->
				Bukkit.getScheduler().runTask(Markets.getInstance(), () -> applyLiteBansOwnerChange(uuid, banned)));
		this.liteBansListenersRegistered = true;
	}

	/**
	 * Rebuilds the cached set of server-banned owners from Bukkit (sync) and LiteBans (async),
	 * but only if the cache is older than {@link Settings#BANNED_OWNER_CACHE_TTL}. This keeps
	 * per-market ban checks an O(1) set lookup instead of querying ban sources per market.
	 */
	private void refreshBanCacheIfStale() {
		final long ttlMillis = Settings.BANNED_OWNER_CACHE_TTL.getInt() * 1000L;
		if (System.currentTimeMillis() - this.bannedCacheLastRefresh < ttlMillis)
			return;

		final Set<UUID> uuids = new HashSet<>();
		final Set<String> names = new HashSet<>();

		// profile bans (modern, UUID based)
		try {
			final BanList<PlayerProfile> profileBanList = Bukkit.getBanList(BanList.Type.PROFILE);
			for (BanEntry<PlayerProfile> entry : profileBanList.getBanEntries()) {
				final PlayerProfile profile = entry.getBanTarget();
				if (profile == null) continue;

				if (profile.getUniqueId() != null)
					uuids.add(profile.getUniqueId());
				if (profile.getName() != null)
					names.add(profile.getName().toLowerCase());
			}
		} catch (Exception ignored) {
			// API not available / unexpected ban list shape, fall back to name bans only
		}

		// legacy name bans
		try {
			final BanList<?> nameBanList = Bukkit.getBanList(BanList.Type.NAME);
			for (BanEntry<?> entry : nameBanList.getBanEntries()) {
				final Object target = entry.getBanTarget();
				if (target != null)
					names.add(target.toString().toLowerCase());
			}
		} catch (Exception ignored) {
			// ignore, name bans are best-effort
		}

		this.bukkitBannedOwnerUUIDs.clear();
		this.bukkitBannedOwnerUUIDs.addAll(uuids);
		this.bukkitBannedOwnerNames.clear();
		this.bukkitBannedOwnerNames.addAll(names);
		this.bannedCacheLastRefresh = System.currentTimeMillis();
		rebuildMergedBanCache();

		scheduleLiteBansBanRefresh();
	}

	private void scheduleLiteBansBanRefresh() {
		if (!LiteBansBanCheck.isAvailable())
			return;
		if (!this.liteBansRefreshInFlight.compareAndSet(false, true))
			return;

		final Map<UUID, String> owners = new HashMap<>();
		for (Market market : getManagerContent()) {
			owners.putIfAbsent(market.getOwnerUUID(), market.getOwnerName());
		}

		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			try {
				final LiteBansBanCheck.BannedOwners banned = LiteBansBanCheck.findBannedOwners(owners);
				Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
					this.liteBansBannedOwnerUUIDs.clear();
					this.liteBansBannedOwnerUUIDs.addAll(banned.uuids());
					this.liteBansBannedOwnerNames.clear();
					this.liteBansBannedOwnerNames.addAll(banned.names());
					rebuildMergedBanCache();
					this.liteBansRefreshInFlight.set(false);
				});
			} catch (Exception ex) {
				Bukkit.getScheduler().runTask(Markets.getInstance(), () -> this.liteBansRefreshInFlight.set(false));
			}
		});
	}

	private void applyLiteBansOwnerChange(@NonNull final UUID ownerUUID, final boolean banned) {
		final Market market = getByOwner(ownerUUID);
		final String ownerName = market != null ? market.getOwnerName() : null;

		if (banned) {
			this.liteBansBannedOwnerUUIDs.add(ownerUUID);
			if (ownerName != null)
				this.liteBansBannedOwnerNames.add(ownerName.toLowerCase());
		} else {
			this.liteBansBannedOwnerUUIDs.remove(ownerUUID);
			if (ownerName != null)
				this.liteBansBannedOwnerNames.remove(ownerName.toLowerCase());
			else
				// Owner may no longer have a market; drop any lingering name tied only via UUID on next full refresh
				this.bannedCacheLastRefresh = 0L;
		}

		rebuildMergedBanCache();
	}

	private void rebuildMergedBanCache() {
		this.bannedOwnerUUIDs.clear();
		this.bannedOwnerUUIDs.addAll(this.bukkitBannedOwnerUUIDs);
		this.bannedOwnerUUIDs.addAll(this.liteBansBannedOwnerUUIDs);

		this.bannedOwnerNames.clear();
		this.bannedOwnerNames.addAll(this.bukkitBannedOwnerNames);
		this.bannedOwnerNames.addAll(this.liteBansBannedOwnerNames);
	}

	/**
	 * @return true if the owner of the given market is currently banned from the server. Returns
	 * false when the feature is disabled via {@link Settings#HIDE_BANNED_OWNER_MARKETS}.
	 */
	public boolean isOwnerServerBanned(@NonNull final Market market) {
		if (!Settings.HIDE_BANNED_OWNER_MARKETS.getBoolean())
			return false;

		refreshBanCacheIfStale();
		return this.bannedOwnerUUIDs.contains(market.getOwnerUUID()) || this.bannedOwnerNames.contains(market.getOwnerName().toLowerCase());
	}

	/**
	 * @return true if the player may open closed / server-banned-owner markets (owner or admin).
	 */
	public boolean canViewRestrictedMarkets(@NonNull final Player player, @NonNull final Market market) {
		return market.getOwnerUUID().equals(player.getUniqueId())
				|| player.hasPermission("markets.admin.viewrestricted")
				|| player.isOp();
	}

	/**
	 * Enhanced search that includes enchantment names for enchanted books and potion effects for potions
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

			// Check potion effects on potions
			if (meta instanceof PotionMeta) {
				PotionMeta potionMeta = (PotionMeta) meta;

				// Check custom potion effects
				if (potionMeta.hasCustomEffects()) {
					for (PotionEffect effect : potionMeta.getCustomEffects()) {
						NamespacedKey key = effect.getType().getKey();
						String effectName = key.getKey().toLowerCase();

						// Match by potion effect name (e.g., "healing", "strength", "speed")
						if (effectName.contains(lowerKeywords)) {
							return true;
						}
					}
				}

				// Check base potion data (for non-custom potions)
				if (potionMeta.getBasePotionType() != null) {
					String basePotionName = potionMeta.getBasePotionType().name().toLowerCase();

					// Match by base potion type (e.g., "healing", "strength")
					if (basePotionName.contains(lowerKeywords)) {
						return true;
					}
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
		refreshBanCacheIfStale();
		return getManagerContent().stream().filter(market -> !market.getOwnerUUID().equals(ignoredUser.getUniqueId()) && market.isOpen() && !market.isEmpty() && !isOwnerServerBanned(market)).collect(Collectors.toList());
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
		refreshBanCacheIfStale();
		return getManagerContent().stream().filter(market -> market.isOpen() && !isOwnerServerBanned(market)).collect(Collectors.toList());
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

			Markets.getDataManager().getMarketLocks((lockError, lockedMarketIds) -> {
				final Set<UUID> lockedIds = lockError == null && lockedMarketIds != null
						? new HashSet<>(lockedMarketIds)
						: Set.of();

				found.forEach(market -> {
					if (lockedIds.contains(market.getId()))
						market.setLocked(true);

					Markets.getDataManager().getRatingsByMarket(market.getId(), (ratingError, foundRatings) -> {
						if (ratingError != null) return;
						market.getRatings().addAll(foundRatings);
						add(market);
					});
				});

				// after markets have been added let's load categories
				Markets.getCategoryManager().load();
			});
		});
	}

	public boolean isManagementLocked(@NonNull final Market market) {
		return market.isLocked();
	}

	public List<Market> getLockedMarkets() {
		return getManagerContent().stream().filter(Market::isLocked).collect(Collectors.toList());
	}

	public List<Market> getClosedMarkets() {
		return getManagerContent().stream().filter(market -> !market.isOpen()).collect(Collectors.toList());
	}

	public List<Market> getBannedOwnerMarkets() {
		refreshBanCacheIfStale();
		return getManagerContent().stream().filter(this::isOwnerServerBannedAdmin).collect(Collectors.toList());
	}

	/**
	 * @return true if the owner is server-banned, regardless of {@link Settings#HIDE_BANNED_OWNER_MARKETS}.
	 */
	public boolean isOwnerServerBannedAdmin(@NonNull final Market market) {
		refreshBanCacheIfStale();
		return this.bannedOwnerUUIDs.contains(market.getOwnerUUID()) || this.bannedOwnerNames.contains(market.getOwnerName().toLowerCase());
	}
}
