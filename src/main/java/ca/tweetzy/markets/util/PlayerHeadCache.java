package ca.tweetzy.markets.util;

import ca.tweetzy.flight.utils.QuickItem;
import lombok.NonNull;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerHeadCache {

	private static final ConcurrentHashMap<UUID, ItemStack> CACHE = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<UUID, CompletableFuture<ItemStack>> IN_FLIGHT = new ConcurrentHashMap<>();

	private PlayerHeadCache() {
	}

	public static @NonNull CompletableFuture<ItemStack> getOrFetch(@NonNull final OfflinePlayer player) {
		final UUID uuid = player.getUniqueId();

		final ItemStack cached = CACHE.get(uuid);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached.clone());
		}

		return IN_FLIGHT.computeIfAbsent(uuid, id -> QuickItem.asyncPlayerHead(player)
				.thenApply(skull -> {
					final ItemStack stored = skull.clone();
					CACHE.put(id, stored.clone());
					IN_FLIGHT.remove(id);
					return stored;
				})
				.whenComplete((result, error) -> {
					if (error != null) {
						IN_FLIGHT.remove(id);
					}
				}));
	}

}
