# Player Head Texture Caching Implementation Plan

## Problem Statement

Currently, player heads are loaded using `QuickItem.asyncPlayerHead()` which:
1. **Makes API calls** to Mojang's session server every time
2. **No persistent caching** - textures are re-fetched on every GUI open
3. **Rate limiting risk** - Multiple API calls can trigger Mojang rate limits
4. **Slower performance** - Network calls add latency (100-500ms per head)
5. **Offline issues** - Can't load heads if Mojang API is down

## Goals

1. **Cache player textures in YAML file** - Persist texture data permanently
2. **Load cache into memory on startup** - Fast in-memory lookups
3. **Update on login** - Refresh textures when players join (async)
4. **Rate limit updates** - Only update once per 24 hours
5. **Use local data first** - Avoid API calls when possible
6. **Fallback mechanism** - Handle missing/expired textures gracefully

## Bukkit/Spigot Player Profile API

### Available Options

**1. Online Player (Local Data - No API Call)**
```java
Player player = Bukkit.getPlayer(uuid);
PlayerProfile profile = player.getPlayerProfile();
String textureValue = // Extract from profile properties
String textureSignature = // Extract from profile properties
```
✅ **Instant** - No network call
✅ **Always available** when player is online
❌ **Only works for online players**

**2. Server PlayerProfile API (Async - Controlled)**
```java
CompletableFuture<PlayerProfile> future = Bukkit.createProfile(uuid);
future.thenAccept(profile -> {
    // Extract texture data
    Collection<ProfileProperty> properties = profile.getProperties();
    for (ProfileProperty property : properties) {
        if (property.getName().equals("textures")) {
            String value = property.getValue();
            String signature = property.getSignature();
            // Store in cache
        }
    }
});
```
✅ **Async** - Non-blocking
✅ **Controlled** - We decide when to call
⚠️ **Still makes API call** - But we can cache result

**3. Cached Texture Data (YAML File - No API Call)**
```java
// Load from memory (already loaded from YAML)
PlayerTextureCache cache = textureManager.getTexture(uuid);
if (cache.isExpired()) {
    // Trigger async update
    updateTextureAsync(uuid);
}
// Use cached texture immediately
return cache.getTextureValue();
```
✅ **Instant** - No API call
✅ **Works offline** - Uses cached data
✅ **Persistent** - Survives restarts
✅ **In-memory** - Fast lookups

## Solution Architecture

### YAML File Structure

**File:** `player-head-cache.yml`

```yaml
# Player Head Texture Cache
# Updated automatically when players login (max once per 24 hours)

cache:
  069a79f4-44e9-4726-a5be-fca90e38aaf5: # Player UUID
    texture_value: "ewogICJ0aW1lc3RhbXAiIDogMTYzODM2MDAwMDAwMCwKICAicHJvZmlsZUlkIiA6ICI..."
    texture_signature: "XYZ123ABC..."
    updated_at: 1638360000000 # Unix timestamp in milliseconds
    player_name: "Notch" # For reference only

  550e8400-e29b-41d4-a716-446655440000:
    texture_value: "base64encodeddata..."
    texture_signature: "signature..."
    updated_at: 1638360000000
    player_name: "jeb_"
```

**Fields:**
- `texture_value` - Base64 encoded texture data (contains skin URL)
- `texture_signature` - Mojang signature for texture verification
- `updated_at` - Timestamp of last texture update (for 24h rate limiting)
- `player_name` - Player name for debugging/reference (optional)

### In-Memory Cache Structure

**Data class:**
```java
public class CachedTexture {
    private final String textureValue;
    private final String textureSignature;
    private final long updatedAt;
    private final String playerName;

    // Constructor, getters

    public boolean isExpired() {
        return System.currentTimeMillis() - updatedAt > 86400000L; // 24 hours
    }

    public boolean isValid() {
        return textureValue != null && !textureValue.isEmpty();
    }
}
```

**In-memory map:**
```java
private final Map<UUID, CachedTexture> textureCache = new ConcurrentHashMap<>();
```

### TextureManager Service

Create a new service to handle all texture caching operations.

**File:** `src/main/java/ca/tweetzy/markets/model/manager/TextureManager.java`

```java
package ca.tweetzy.markets.model.manager;

import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class TextureManager {

    private static final long TEXTURE_CACHE_DURATION = 86400000L; // 24 hours in milliseconds

    private final File cacheFile;
    private final Map<UUID, CachedTexture> textureCache = new ConcurrentHashMap<>();
    private YamlConfiguration config;

    public TextureManager() {
        this.cacheFile = new File(Markets.getInstance().getDataFolder(), "player-head-cache.yml");
        loadCache();
    }

    /**
     * Load texture cache from YAML file into memory
     */
    private void loadCache() {
        if (!cacheFile.exists()) {
            try {
                cacheFile.createNewFile();
                config = new YamlConfiguration();
                config.createSection("cache");
                config.save(cacheFile);
                Common.log("&aCreated new player-head-cache.yml");
            } catch (IOException e) {
                Common.log("&cFailed to create player-head-cache.yml");
                e.printStackTrace();
                config = new YamlConfiguration();
            }
        } else {
            try {
                config = YamlConfiguration.loadConfiguration(cacheFile);
                Common.log("&aLoaded player-head-cache.yml");
            } catch (Exception e) {
                Common.log("&cFailed to load player-head-cache.yml");
                e.printStackTrace();
                config = new YamlConfiguration();
            }
        }

        // Load cache into memory
        ConfigurationSection cacheSection = config.getConfigurationSection("cache");
        if (cacheSection != null) {
            for (String uuidString : cacheSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    ConfigurationSection playerSection = cacheSection.getConfigurationSection(uuidString);

                    if (playerSection != null) {
                        String textureValue = playerSection.getString("texture_value");
                        String textureSignature = playerSection.getString("texture_signature");
                        long updatedAt = playerSection.getLong("updated_at");
                        String playerName = playerSection.getString("player_name", "Unknown");

                        CachedTexture cachedTexture = new CachedTexture(textureValue, textureSignature, updatedAt, playerName);
                        textureCache.put(uuid, cachedTexture);
                    }
                } catch (IllegalArgumentException e) {
                    Common.log("&cInvalid UUID in cache: " + uuidString);
                }
            }
            Common.log("&aLoaded " + textureCache.size() + " cached player textures");
        }
    }

    /**
     * Save texture cache to YAML file
     */
    private void saveCache() {
        Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
            try {
                // Clear existing cache section
                config.set("cache", null);
                ConfigurationSection cacheSection = config.createSection("cache");

                // Write all cached textures
                for (Map.Entry<UUID, CachedTexture> entry : textureCache.entrySet()) {
                    String uuidString = entry.getKey().toString();
                    CachedTexture texture = entry.getValue();

                    ConfigurationSection playerSection = cacheSection.createSection(uuidString);
                    playerSection.set("texture_value", texture.getTextureValue());
                    playerSection.set("texture_signature", texture.getTextureSignature());
                    playerSection.set("updated_at", texture.getUpdatedAt());
                    playerSection.set("player_name", texture.getPlayerName());
                }

                config.save(cacheFile);
            } catch (IOException e) {
                Common.log("&cFailed to save player-head-cache.yml");
                e.printStackTrace();
            }
        });
    }

    /**
     * Get or update player texture cache
     *
     * @param uuid Player UUID
     * @param forceUpdate Force texture update even if cache is valid
     * @param callback Called when texture is available (from cache or after update)
     */
    public void getOrUpdateTexture(@NonNull final UUID uuid, boolean forceUpdate, @NonNull final Consumer<CachedTexture> callback) {
        // Check if we have valid cached texture in memory
        CachedTexture cached = textureCache.get(uuid);

        if (!forceUpdate && cached != null && cached.isValid() && !cached.isExpired()) {
            // Use cached texture
            callback.accept(cached);
            return;
        }

        // Need to update texture - check if player is online first
        final Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            // Player is online - extract texture from their current profile (no API call)
            extractTextureFromOnlinePlayer(onlinePlayer, callback);
        } else {
            // Player is offline - need to fetch from Mojang API (async)
            // But first, if we have ANY cached texture, use it immediately and update in background
            if (cached != null && cached.isValid()) {
                callback.accept(cached);
                // Update in background for next time
                fetchTextureFromMojang(uuid, null);
            } else {
                // No cache at all, must fetch
                fetchTextureFromMojang(uuid, callback);
            }
        }
    }

    /**
     * Extract texture from online player profile (local data, no API call)
     */
    private void extractTextureFromOnlinePlayer(@NonNull final Player player, @NonNull final Consumer<CachedTexture> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
            try {
                final PlayerProfile profile = player.getPlayerProfile();
                final Collection<ProfileProperty> properties = profile.getProperties();

                String textureValue = null;
                String textureSignature = null;

                for (ProfileProperty property : properties) {
                    if ("textures".equals(property.getName())) {
                        textureValue = property.getValue();
                        textureSignature = property.getSignature();
                        break;
                    }
                }

                if (textureValue != null) {
                    // Create cached texture
                    final CachedTexture cachedTexture = new CachedTexture(
                        textureValue,
                        textureSignature,
                        System.currentTimeMillis(),
                        player.getName()
                    );

                    // Update in-memory cache
                    textureCache.put(player.getUniqueId(), cachedTexture);

                    // Save to file
                    saveCache();

                    // Callback with texture data on main thread
                    Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(cachedTexture));
                } else {
                    // No texture found - use cached if available
                    CachedTexture cached = textureCache.get(player.getUniqueId());
                    final CachedTexture fallback = cached != null && cached.isValid() ? cached : null;

                    Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(fallback));
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Fall back to cached data
                CachedTexture cached = textureCache.get(player.getUniqueId());
                final CachedTexture fallback = cached != null && cached.isValid() ? cached : null;

                Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(fallback));
            }
        });
    }

    /**
     * Fetch texture from Mojang API (async, makes network call)
     */
    private void fetchTextureFromMojang(@NonNull final UUID uuid, final Consumer<CachedTexture> callback) {
        // Use Bukkit's async profile creation (makes Mojang API call)
        CompletableFuture<PlayerProfile> profileFuture = Bukkit.createProfile(uuid);

        profileFuture.thenAccept(profile -> {
            try {
                final Collection<ProfileProperty> properties = profile.getProperties();

                String textureValue = null;
                String textureSignature = null;

                for (ProfileProperty property : properties) {
                    if ("textures".equals(property.getName())) {
                        textureValue = property.getValue();
                        textureSignature = property.getSignature();
                        break;
                    }
                }

                if (textureValue != null) {
                    // Create cached texture
                    final CachedTexture cachedTexture = new CachedTexture(
                        textureValue,
                        textureSignature,
                        System.currentTimeMillis(),
                        profile.getName() != null ? profile.getName() : "Unknown"
                    );

                    // Update in-memory cache
                    textureCache.put(uuid, cachedTexture);

                    // Save to file
                    saveCache();

                    // Callback with texture data on main thread
                    if (callback != null) {
                        Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(cachedTexture));
                    }
                } else {
                    // No texture found - use cached if available
                    CachedTexture cached = textureCache.get(uuid);
                    final CachedTexture fallback = cached != null && cached.isValid() ? cached : null;

                    if (callback != null) {
                        Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(fallback));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Fall back to cached data
                CachedTexture cached = textureCache.get(uuid);
                final CachedTexture fallback = cached != null && cached.isValid() ? cached : null;

                if (callback != null) {
                    Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(fallback));
                }
            }
        }).exceptionally(throwable -> {
            throwable.printStackTrace();
            // Fall back to cached data
            CachedTexture cached = textureCache.get(uuid);
            final CachedTexture fallback = cached != null && cached.isValid() ? cached : null;

            if (callback != null) {
                Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(fallback));
            }
            return null;
        });
    }

    /**
     * Create a player head ItemStack from cached texture data
     */
    public ItemStack createPlayerHead(@NonNull final CachedTexture textureData) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID()); // Random UUID for texture-only profile
            profile.setProperty(new ProfileProperty("textures", textureData.getTextureValue(), textureData.getTextureSignature()));
            meta.setPlayerProfile(profile);
            skull.setItemMeta(meta);
        }

        return skull;
    }

    /**
     * Update texture for player on login (respects 24h rate limit)
     */
    public void updateTextureOnLogin(@NonNull final Player player) {
        CachedTexture cached = textureCache.get(player.getUniqueId());

        // Check if update is needed (24h rate limit)
        if (cached != null && cached.isValid() && !cached.isExpired()) {
            // Texture cache is still valid, skip update
            return;
        }

        // Update texture from online player (no API call, uses local data)
        extractTextureFromOnlinePlayer(player, cachedTexture -> {
            // Texture updated and cached
            // No need to do anything else
        });
    }

    /**
     * Get cached texture count
     */
    public int getCacheSize() {
        return textureCache.size();
    }

    /**
     * Clear expired cache entries (for maintenance)
     */
    public void cleanupExpiredCache() {
        Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
            int removed = 0;
            for (Map.Entry<UUID, CachedTexture> entry : textureCache.entrySet()) {
                if (entry.getValue().isExpired()) {
                    textureCache.remove(entry.getKey());
                    removed++;
                }
            }

            if (removed > 0) {
                saveCache();
                Common.log("&aCleaned up " + removed + " expired texture cache entries");
            }
        });
    }

    /**
     * Cached texture data
     */
    @Getter
    public static class CachedTexture {
        private final String textureValue;
        private final String textureSignature;
        private final long updatedAt;
        private final String playerName;

        public CachedTexture(String textureValue, String textureSignature, long updatedAt, String playerName) {
            this.textureValue = textureValue;
            this.textureSignature = textureSignature;
            this.updatedAt = updatedAt;
            this.playerName = playerName;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - updatedAt > 86400000L; // 24 hours
        }

        public boolean isValid() {
            return textureValue != null && !textureValue.isEmpty();
        }
    }
}
```

### Markets.java Integration

Add TextureManager to main plugin class.

**File:** `src/main/java/ca/tweetzy/markets/Markets.java`

```java
@Getter
private static TextureManager textureManager;

@Override
public void onEnable() {
    // ... existing initialization ...

    // Initialize texture manager (loads cache into memory)
    textureManager = new TextureManager();

    // ... rest of initialization ...
}
```

### PlayerJoinListener Update

Update the player join listener to refresh textures on login.

**File:** `src/main/java/ca/tweetzy/markets/listeners/PlayerJoinListener.java`

```java
@EventHandler
public void onPlayerJoin(final PlayerJoinEvent event) {
    final Player player = event.getPlayer();
    final MarketUser marketUser = Markets.getPlayerManager().get(player.getUniqueId());

    if (marketUser != null) {
        // Update name if changed
        if (!marketUser.getLastKnownName().equalsIgnoreCase(player.getName())) {
            marketUser.setLastKnownName(player.getName());
        }

        // Update player texture cache (respects 24h rate limit, uses local data)
        Markets.getTextureManager().updateTextureOnLogin(player);

        // Perform sync
        marketUser.sync(result -> {
            if (result == SynchronizeResult.FAILURE)
                Common.log("&cSomething went wrong while updating the market profile for&F: &e" + player.getName());
        });

        // ... rest of existing code ...
    }
}
```

### GUI Updates

Replace `QuickItem.asyncPlayerHead()` calls with cached texture loading.

**Example: AllMarketsViewGUI.java**

```java
private void loadPlayerHeadsAsync() {
    // Get the current page items
    final List<Market> itemsToDisplay = this.items.stream()
            .skip((page - 1) * (long) fillSlots().size())
            .limit(fillSlots().size())
            .toList();

    // Load player heads asynchronously for player markets
    for (int i = 0; i < itemsToDisplay.size(); i++) {
        final Market market = itemsToDisplay.get(i);
        final int slotIndex = fillSlots().get(i);

        // Skip server markets (they already have their texture)
        if (market.isServerMarket()) {
            continue;
        }

        // Load texture from cache (no API call in most cases)
        Markets.getTextureManager().getOrUpdateTexture(
            market.getOwnerUUID(),
            false, // Don't force update
            cachedTexture -> {
                if (cachedTexture == null) {
                    // No texture available, keep default head
                    return;
                }

                // Create skull with cached texture
                ItemStack skull = Markets.getTextureManager().createPlayerHead(cachedTexture);

                // Build the final item with the loaded skull
                ItemStack finalItem = QuickItem.of(skull)
                        .name(market.getDisplayName())
                        .lore(market.getDescription())
                        .lore(TranslationManager.list(this.player, Translations.GUI_ALL_MARKETS_ITEMS_MARKET_LORE,
                                "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK),
                                "market_ratings_total", market.getRatings().size(),
                                "market_ratings_stars", StringUtils.repeat("★", (int) market.getReviewAvg())
                        )).make();

                // Update the slot (already on main thread from callback)
                setItem(slotIndex, finalItem);
            }
        );
    }
}
```

**Example: UserProfileGUI.java**

```java
private void loadProfileHead() {
    // Load player head asynchronously
    Markets.getTextureManager().getOrUpdateTexture(
        this.profileUser.getUniqueId(),
        false, // Don't force update
        cachedTexture -> {
            if (cachedTexture == null) {
                // No texture available, keep default head
                return;
            }

            ItemStack skull = Markets.getTextureManager().createPlayerHead(cachedTexture);
            ItemStack finalItem = QuickItem.of(skull)
                    .name(TranslationManager.string(this.player, Translations.GUI_USER_PROFILE_ITEMS_USER_NAME, "player_name", this.profileUserName))
                    .lore(TranslationManager.list(this.player, Translations.GUI_USER_PROFILE_ITEMS_USER_LORE,
                            "user_last_seen", TimeUtil.convertToReadableDate(user.getLastSeenAt(), Settings.DATETIME_FORMAT.getString()),
                            // ... rest of lore
                    )).make();

            // Update the slot
            setButton(1, 4, finalItem);
        }
    );
}
```

## Performance Comparison

### Current Implementation (QuickItem.asyncPlayerHead)

**Opening GUI with 10 player markets:**
- 10 API calls to Mojang session server
- ~100-500ms per call = 1-5 seconds total
- Rate limiting risk
- Fails if Mojang API is down

### New Implementation (Cached Textures)

**First time (texture not cached):**
- 10 API calls (same as current)
- ~100-500ms per call = 1-5 seconds total
- But: Textures are now cached in YAML file

**Subsequent opens (texture cached in memory):**
- 0 API calls - uses in-memory cache
- ~0.1-1ms per head from memory
- 10 heads = 1-10ms total
- **100-1000x faster**

**Player login (texture refresh):**
- 0 API calls - uses online player's local profile
- ~5ms to extract and cache
- Non-blocking, runs async
- Only if cache is expired (>24h old)

## API Call Reduction

### Scenario: 100 players, 50 active markets

**Current (no caching):**
- Player opens market list: 50 API calls
- They open it 10 times: 500 API calls
- 100 players do this: **50,000 API calls**

**New (with YAML caching):**
- First time each player opens: 50 API calls × 100 players = 5,000 API calls
- Subsequent opens: 0 API calls (uses in-memory cache)
- Daily texture refreshes: 100 logins × ~50 unique market owners = ~150 API calls (uses local data, only for new players)
- **Total: ~5,150 API calls (90% reduction)**

## File Storage Impact

### Storage Requirements

**player-head-cache.yml:**
- Per player entry: ~1KB (texture_value + texture_signature + metadata)
- For 10,000 players: ~10 MB
- **Negligible storage impact**

**Memory Usage:**
- In-memory map: ~1KB per player loaded
- For 1,000 cached entries: ~1 MB RAM
- **Minimal memory impact**

## Migration Strategy

### Phase 1: TextureManager Service
1. Create `TextureManager.java`
2. Implement YAML file loading/saving
3. Implement in-memory cache
4. Test texture extraction and caching

### Phase 2: Main Plugin Integration
1. Add TextureManager to `Markets.java`
2. Initialize on plugin enable
3. Test file creation and loading

### Phase 3: PlayerJoinListener Integration
1. Add texture update on login
2. Test 24h rate limiting
3. Verify local data extraction (no API calls for online players)

### Phase 4: GUI Updates
1. Update `AllMarketsViewGUI.java`
2. Update `UserProfileGUI.java`
3. Update any other GUIs using player heads
4. Remove `QuickItem.asyncPlayerHead()` calls

### Phase 5: Testing
1. Test first-time texture loading (Mojang API)
2. Test cached texture loading (in-memory)
3. Test texture refresh on login
4. Test 24h rate limiting
5. Test offline fallback (Mojang API down)
6. Test YAML file persistence across restarts

## Rollback Plan

If issues occur:

1. **Immediate rollback:**
   - Revert GUI changes to use `QuickItem.asyncPlayerHead()`
   - Remove TextureManager initialization
   - Keep `player-head-cache.yml` (no harm)
   - **Time:** 10 minutes

2. **File cleanup (optional):**
   - Delete `player-head-cache.yml`
   - **Time:** 1 minute

## Benefits Summary

✅ **90% reduction in Mojang API calls**
✅ **100-1000x faster head loading** (after first cache)
✅ **Works offline** - Uses cached textures when Mojang API is down
✅ **Rate limit compliant** - Only updates once per 24 hours
✅ **No blocking** - Online players use local data (instant)
✅ **Persistent cache** - Survives server restarts (YAML file)
✅ **In-memory** - Lightning-fast lookups after load
✅ **Minimal storage** - ~1KB per player in YAML
✅ **No database changes** - Uses simple YAML file
✅ **Easy to inspect** - Human-readable YAML format

## Technical Details

### Texture Data Format

**texture_value (Base64 JSON):**
```json
{
  "timestamp": 1638360000000,
  "profileId": "069a79f444e94726a5befca90e38aaf5",
  "profileName": "Notch",
  "textures": {
    "SKIN": {
      "url": "http://textures.minecraft.net/texture/..."
    }
  }
}
```

**texture_signature:**
- Mojang's RSA signature of the texture data
- Used to verify texture authenticity
- Required for proper skull rendering

### Bukkit PlayerProfile API

**Methods used:**
- `player.getPlayerProfile()` - Get online player's profile (local, instant)
- `Bukkit.createProfile(uuid)` - Fetch profile from Mojang (async, network call)
- `profile.getProperties()` - Get texture properties
- `profile.setProperty()` - Set texture for skull rendering

**Why this works:**
- Spigot/Paper cache player profiles in memory when players join
- We extract this cached data and persist it to YAML file
- YAML cache loads into memory on startup for instant lookups
- File-based cache survives restarts

### Cache Cleanup

**Automatic cleanup:**
- Can run periodic cleanup task to remove expired (>24h) entries
- Keeps YAML file from growing indefinitely
- Optional - expired entries don't hurt, just take up space

**Manual cleanup:**
```java
// In Markets.java onEnable or as scheduled task
Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
    Markets.getTextureManager().cleanupExpiredCache();
}, 20L * 60 * 60, 20L * 60 * 60 * 24); // Every 24 hours
```

## Conclusion

This implementation provides **persistent player head caching using YAML file** that:
- **Eliminates repeated API calls** by caching textures in file + memory
- **Loads into memory on startup** for instant lookups
- **Updates automatically** when players login (using local data, no API call)
- **Respects rate limits** with 24-hour update intervals
- **Falls back gracefully** when textures aren't cached
- **Improves performance** by 100-1000x for cached heads
- **No database changes** - Simple YAML file storage
- **Easy to debug** - Human-readable YAML format

The solution leverages Bukkit's player profile API to extract texture data locally when players are online, avoiding unnecessary API calls while maintaining up-to-date player heads. All cached data is persisted in a YAML file and loaded into memory on startup for lightning-fast lookups.
