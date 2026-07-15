package ca.tweetzy.markets.model;

import litebans.api.Database;
import litebans.api.Events;
import litebans.api.Entry;
import litebans.api.exception.MissingImplementationException;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Soft-depend helper for LiteBans. Only call methods on this class when
 * {@link #isAvailable()} is true so {@code NoClassDefFoundError} is avoided
 * on servers without LiteBans.
 */
@UtilityClass
public final class LiteBansBanCheck {

	public boolean isAvailable() {
		return Bukkit.getServer().getPluginManager().isPluginEnabled("LiteBans");
	}

	/**
	 * Checks which of the given owners are actively banned in LiteBans.
	 * Must be called off the main server thread.
	 *
	 * @param owners map of owner UUID → owner name (lowercased names optional)
	 * @return pair of banned UUIDs and banned lowercase names
	 */
	public BannedOwners findBannedOwners(@NonNull final Map<UUID, String> owners) {
		final Set<UUID> uuids = new HashSet<>();
		final Set<String> names = new HashSet<>();

		try {
			final Database database = Database.get();
			for (Map.Entry<UUID, String> owner : owners.entrySet()) {
				if (database.isPlayerBanned(owner.getKey(), null, Database.ANY_SERVER_SCOPE)) {
					uuids.add(owner.getKey());
					if (owner.getValue() != null && !owner.getValue().isEmpty()) {
						names.add(owner.getValue().toLowerCase());
					}
				}
			}
		} catch (MissingImplementationException | IllegalStateException ignored) {
			// API not ready or called on main thread — treat as no LiteBans bans
		}

		return new BannedOwners(uuids, names);
	}

	/**
	 * Registers LiteBans ban add/remove listeners. The callback receives
	 * {@code (uuid, banned)} where {@code banned} is true on ban and false on unban.
	 * Events fire async; the callback should schedule main-thread work itself if needed.
	 */
	public void registerBanListeners(@NonNull final BiConsumer<UUID, Boolean> onBanChange) {
		try {
			Events.get().register(new Events.Listener() {
				@Override
				public void entryAdded(Entry entry) {
					if (!"ban".equalsIgnoreCase(entry.getType())) return;
					final UUID uuid = parseUuid(entry.getUuid());
					if (uuid != null) {
						onBanChange.accept(uuid, true);
					}
				}

				@Override
				public void entryRemoved(Entry entry) {
					if (!"ban".equalsIgnoreCase(entry.getType())) return;
					final UUID uuid = parseUuid(entry.getUuid());
					if (uuid != null) {
						onBanChange.accept(uuid, false);
					}
				}
			});
		} catch (MissingImplementationException ignored) {
			// LiteBans API not ready
		}
	}

	private UUID parseUuid(final String raw) {
		if (raw == null || raw.isEmpty()) return null;
		try {
			if (raw.length() == 32) {
				return UUID.fromString(raw.replaceFirst(
						"(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
						"$1-$2-$3-$4-$5"
				));
			}
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public record BannedOwners(@NonNull Set<UUID> uuids, @NonNull Set<String> names) {
		public BannedOwners {
			uuids = Set.copyOf(uuids);
			names = Set.copyOf(names);
		}

		public boolean isEmpty() {
			return uuids.isEmpty() && names.isEmpty();
		}
	}
}
