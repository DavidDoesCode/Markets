# OfflinePlayer.getName() NULL Issue - Technical Analysis

## Overview

This document details a critical bug in the Markets plugin where `OfflinePlayer.getName()` returns `null`, causing SQLite NOT NULL constraint failures when recording transactions.

## The Error

```
org.sqlite.SQLiteException: [SQLITE_CONSTRAINT_NOTNULL] A NOT NULL constraint failed
(NOT NULL constraint failed: markets_transaction.seller_name)
    at ca.tweetzy.markets.database.DataManager.lambda$createTransaction$64(DataManager.java:821)
```

## Root Cause

### The Problem

**`OfflinePlayer.getName()` can return `null` in Bukkit/Spigot when:**
1. The player has never joined the server
2. The player's profile hasn't been loaded from Mojang's API
3. The server has restarted and the player name cache has been cleared
4. The player UUID exists but the profile data is not yet available

### Where It Occurs

The issue manifests in `MarketTransactionListener.java:23`:

```java
@EventHandler
public void onTransactionEvent(final MarketTransactionEvent event) {
    final Transaction transaction = new MarketTransaction(
        UUID.randomUUID(),
        event.getBuyer().getUniqueId(),
        event.getBuyer().getName(),              // ← Can be NULL
        event.getSeller().getUniqueId(),
        event.getSeller().getUniqueId().equals(UUID.fromString(Settings.SERVER_MARKET_UUID.getString()))
            ? Settings.NAME.getString()
            : event.getSeller().getName(),        // ← Can be NULL for player markets!
        event.getType(),
        event.getItem(),
        event.getCurrency(),
        event.getQuantity(),
        event.getPrice(),
        System.currentTimeMillis()
    );
    // ...
}
```

### Affected Code Paths

#### 1. Item Purchases (CategoryItem.java:240, 337-345)

**Location:** `src/main/java/ca/tweetzy/markets/impl/CategoryItem.java`

```java
// Line 240: Getting the seller
final OfflinePlayer seller = Bukkit.getOfflinePlayer(market.getOwnerUUID());

// Lines 337-345: Creating transaction event
Bukkit.getServer().getPluginManager().callEvent(new MarketTransactionEvent(
    buyer,
    seller,  // ← seller.getName() can be NULL!
    TransactionType.ITEM_PURCHASE,
    this.item,
    getCurrencyDisplayName(),
    newPurchaseAmount,
    totalFixed
));
```

**Problem:** When a player purchases an item from a market, the market owner is retrieved using `Bukkit.getOfflinePlayer(market.getOwnerUUID())`. If the owner's profile isn't cached, `seller.getName()` returns `null`.

#### 2. Request Fulfillments (RequestsGUI.java:89, 161-169)

**Location:** `src/main/java/ca/tweetzy/markets/gui/shared/view/requests/RequestsGUI.java`

```java
// Line 89: Getting the request owner
final OfflinePlayer requestedOwner = Bukkit.getOfflinePlayer(request.getOwner());

// Lines 161-169: Creating transaction event
Bukkit.getServer().getPluginManager().callEvent(new MarketTransactionEvent(
    fulfiller,
    requestedOwner,  // ← requestedOwner.getName() can be NULL!
    TransactionType.REQUEST_FULFILLMENT,
    QuickItem.of(request.getCurrencyItem()).amount(1).make(),
    request.getCurrencyDisplayName(),
    request.getRequestedAmount(),
    request.isCurrencyOfItem() ? request.getPrice() : (int) request.getPrice()
));
```

**Problem:** When a player fulfills a request, the request owner is retrieved using `Bukkit.getOfflinePlayer(request.getOwner())`. If the request owner's profile isn't cached, `requestedOwner.getName()` returns `null`.

## Database Schema Constraint

The `markets_transaction` table (created in `_15_TransactionsMigration.java`) defines:

```java
"CREATE TABLE " + tablePrefix + "transaction (" +
    "id VARCHAR(36) PRIMARY KEY, " +
    "buyer VARCHAR(36) NOT NULL, " +
    "buyer_name VARCHAR(16) NOT NULL, " +    // ← NOT NULL constraint
    "seller VARCHAR(36) NOT NULL, " +
    "seller_name VARCHAR(16) NOT NULL, " +   // ← NOT NULL constraint
    "type VARCHAR(32) NOT NULL, " +
    "item TEXT NOT NULL, " +
    "currency TEXT NOT NULL, " +
    "quantity INT NOT NULL, " +
    "price DOUBLE NOT NULL, " +
    "created_at BigInt NOT NULL " +
")"
```

When `seller_name` or `buyer_name` is `null`, the INSERT statement fails with the NOT NULL constraint error.

## Existing Solutions in Codebase

### Player Name Caching (Best Practice)

The codebase **already implements player name caching** in domain objects:

1. **Market Interface** - `getOwnerName()` method
   - Returns cached owner name
   - Set when market is created/loaded from database

2. **MarketUser Class** - `getName()` method
   - Returns cached player name
   - Updated when player joins

3. **Project Guidelines** (from CLAUDE.md):
   ```
   Player Name Caching
   When working with OfflinePlayer objects, NEVER use OfflinePlayer.getName()
   directly as it can return null for unloaded profiles. Always:
   1. Use cached names from domain objects (e.g., MarketUser.getName(), Market.getOwnerName())
   2. For async head loading, use QuickItem.of(skull) with async callbacks
   3. Cache player names when creating/updating entities
   ```

### Current Partial Workaround

The transaction listener has a partial workaround for **server markets only**:

```java
event.getSeller().getUniqueId().equals(UUID.fromString(Settings.SERVER_MARKET_UUID.getString()))
    ? Settings.NAME.getString()     // ✓ Safe - uses config value
    : event.getSeller().getName()   // ✗ Unsafe - can be NULL for player markets!
```

This only protects server market transactions, not player-owned markets.

## Solution Options

### Option 1: Pass Cached Names to Event (Recommended)

**Change the event to accept cached names instead of OfflinePlayer objects:**

```java
// Modify MarketTransactionEvent constructor
public MarketTransactionEvent(
    UUID buyerId,
    String buyerName,    // ← Accept String instead of relying on OfflinePlayer
    UUID sellerId,
    String sellerName,   // ← Accept String instead of relying on OfflinePlayer
    TransactionType type,
    ItemStack item,
    String currency,
    int quantity,
    double price
)
```

**Update callers:**

```java
// CategoryItem.java - Item purchases
Bukkit.getServer().getPluginManager().callEvent(new MarketTransactionEvent(
    buyer.getUniqueId(),
    buyer.getName(),           // Direct player name (safe - buyer is online)
    seller.getUniqueId(),
    market.getOwnerName(),     // ✓ Use cached name from Market object
    TransactionType.ITEM_PURCHASE,
    this.item,
    getCurrencyDisplayName(),
    newPurchaseAmount,
    totalFixed
));

// RequestsGUI.java - Request fulfillments
Bukkit.getServer().getPluginManager().callEvent(new MarketTransactionEvent(
    fulfiller.getUniqueId(),
    fulfiller.getName(),       // Direct player name (safe - fulfiller is online)
    request.getOwner(),
    request.getOwnerName(),    // ✓ Use cached name from Request object (if available)
    TransactionType.REQUEST_FULFILLMENT,
    QuickItem.of(request.getCurrencyItem()).amount(1).make(),
    request.getCurrencyDisplayName(),
    request.getRequestedAmount(),
    request.isCurrencyOfItem() ? request.getPrice() : (int) request.getPrice()
));
```

### Option 2: Lookup Cached Names in Listener

**Add null-safety with fallback to cached names:**

```java
@EventHandler
public void onTransactionEvent(final MarketTransactionEvent event) {
    // Safely get buyer name
    String buyerName = event.getBuyer().getName();
    if (buyerName == null) {
        MarketUser buyer = Markets.getPlayerManager().getByUUID(event.getBuyer().getUniqueId());
        buyerName = buyer != null ? buyer.getName() : "Unknown";
    }

    // Safely get seller name
    String sellerName;
    if (event.getSeller().getUniqueId().equals(UUID.fromString(Settings.SERVER_MARKET_UUID.getString()))) {
        sellerName = Settings.NAME.getString();
    } else {
        sellerName = event.getSeller().getName();
        if (sellerName == null) {
            MarketUser seller = Markets.getPlayerManager().getByUUID(event.getSeller().getUniqueId());
            sellerName = seller != null ? seller.getName() : "Unknown";
        }
    }

    final Transaction transaction = new MarketTransaction(
        UUID.randomUUID(),
        event.getBuyer().getUniqueId(),
        buyerName,
        event.getSeller().getUniqueId(),
        sellerName,
        event.getType(),
        event.getItem(),
        event.getCurrency(),
        event.getQuantity(),
        event.getPrice(),
        System.currentTimeMillis()
    );
    // ...
}
```

### Option 3: Add Missing Cached Names to Domain Objects

**Ensure all domain objects that need player names have cached name fields:**

1. Check if `Request` class has owner name caching
2. Ensure all markets cache owner names properly
3. Add migration if needed to populate missing cached names

## Testing Scenarios

To reproduce and test the fix:

1. **Create market with offline owner:**
   - Import market data with UUID of player who never joined
   - Attempt purchase from that market

2. **Create request from offline player:**
   - Use admin command to create request with offline player UUID
   - Attempt to fulfill the request

3. **Server restart test:**
   - Restart server
   - Immediately trigger transaction before profile loads
   - Verify cached name is used instead of OfflinePlayer.getName()

## Impact Assessment

### When This Occurs

- **High Risk:** Data imports/migrations with player UUIDs that never joined
- **Medium Risk:** Server restarts with immediate transaction activity
- **Low Risk:** Normal gameplay (most players online when transacting)

### Consequences

- Transactions fail to save to database
- Transaction history incomplete
- No permanent record of purchase/sale
- Potential for money/item duplication if transaction partially completes

## Related Code References

- `MarketTransactionListener.java` - Event handler with the bug
- `CategoryItem.java:240, 337-345` - Item purchase transaction creation
- `RequestsGUI.java:89, 161-169` - Request fulfillment transaction creation
- `_15_TransactionsMigration.java` - Database schema with NOT NULL constraints
- `DataManager.java:821` - Transaction INSERT that fails
- `MarketTransaction.java` - Transaction domain object
- `MarketTransactionEvent.java` - Event class definition

## Prevention Guidelines

**For Future Development:**

1. **Never call `OfflinePlayer.getName()` directly**
   - Always use cached names from domain objects
   - Always have a fallback for null names

2. **Cache player names in domain objects**
   - When creating entities with player UUIDs
   - When loading entities from database
   - When players join/update profiles

3. **Event design best practices**
   - Don't pass OfflinePlayer to events if you need the name
   - Pass cached data (UUIDs + names) instead
   - Let event listeners work with guaranteed non-null data

4. **Database constraints**
   - Respect NOT NULL constraints
   - Validate data before database operations
   - Handle constraint failures gracefully

## Conclusion

This is a well-documented issue in Bukkit/Spigot development. The Markets plugin already has the infrastructure (cached names in domain objects) to avoid this problem. The fix requires updating the transaction event creation to use these cached names instead of relying on `OfflinePlayer.getName()`.

**Recommended Fix:** Option 1 (Pass cached names to event) - cleanest and most maintainable approach that aligns with existing codebase patterns.
