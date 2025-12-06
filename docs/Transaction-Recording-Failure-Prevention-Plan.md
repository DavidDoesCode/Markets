# Transaction Recording Failure Prevention Plan

## Problem Statement

Transaction recording is failing with `SQLITE_CONSTRAINT_NOTNULL` errors when `seller_name` is NULL:

```
[SQLITE_CONSTRAINT_NOTNULL] A NOT NULL constraint failed
(NOT NULL constraint failed: markets_transaction.seller_name)
at DataManager.lambda$createTransaction$64(DataManager.java:821)
```

## Root Cause Analysis

### Transaction Creation Flow

**Step 1: Transaction Event Created** (2 locations)

1. **CategoryItem.java:240, 337-345** - Item purchases
   ```java
   final OfflinePlayer seller = Bukkit.getOfflinePlayer(market.getOwnerUUID());
   Bukkit.getServer().getPluginManager().callEvent(new MarketTransactionEvent(
       buyer,      // Online player (safe)
       seller,     // OfflinePlayer - getName() CAN BE NULL
       ...
   ));
   ```

2. **RequestsGUI.java:89, 161-169** - Request fulfillments
   ```java
   final OfflinePlayer requestedOwner = Bukkit.getOfflinePlayer(request.getOwner());
   Bukkit.getServer().getPluginManager().callEvent(new MarketTransactionEvent(
       fulfiller,       // Online player (safe)
       requestedOwner,  // OfflinePlayer - getName() CAN BE NULL
       ...
   ));
   ```

**Step 2: Transaction Listener Processes Event**

`MarketTransactionListener.java:17-30`
```java
final Transaction transaction = new MarketTransaction(
    UUID.randomUUID(),
    event.getBuyer().getUniqueId(),
    event.getBuyer().getName(),     // Can be null
    event.getSeller().getUniqueId(),
    event.getSeller().getUniqueId().equals(UUID.fromString(Settings.SERVER_MARKET_UUID.getString()))
        ? Settings.NAME.getString()  // Server market - safe
        : event.getSeller().getName(), // ← CAN BE NULL for player markets
    ...
);
```

**Step 3: Transaction Stored to Database**

`DataManager.java:812`
```java
preparedStatement.setString(5, transaction.getSellerName()); // NULL value causes constraint violation
```

### Why `OfflinePlayer.getName()` Returns NULL

1. **Player never joined server** - Profile not cached
2. **Player profile not loaded** - Mojang API hasn't been called yet
3. **Imported/migrated data** - UUID exists but profile unknown
4. **Server restart** - Cache cleared before profile loads

## Solution: Multi-Layer Prevention

### Layer 1: Use Cached Names at Event Creation ✅ BEST

**Modify event creation to use cached names instead of `OfflinePlayer.getName()`**

#### 1.1 CategoryItem.java - Item Purchases

**Current (BROKEN):**
```java
final OfflinePlayer seller = Bukkit.getOfflinePlayer(market.getOwnerUUID());
Bukkit.getServer().getPluginManager().callEvent(new MarketTransactionEvent(
    buyer,
    seller,  // seller.getName() can be NULL
    ...
));
```

**Fixed:**
```java
// Use cached owner name from market object
Bukkit.getServer().getPluginManager().callEvent(new MarketTransactionEvent(
    buyer,
    Bukkit.getOfflinePlayer(market.getOwnerUUID()), // Still pass OfflinePlayer
    TransactionType.ITEM_PURCHASE,
    this.item,
    getCurrencyDisplayName(),
    newPurchaseAmount,
    totalFixed
));
```

**Then update listener to extract name from market:**

Actually, we need to change the approach. Let's pass cached names directly.

**Better Fix - Pass cached name explicitly:**

Change `MarketTransactionEvent` constructor to accept names directly OR modify listener to use cached data.

#### 1.2 RequestsGUI.java - Request Fulfillments

**Current (BROKEN):**
```java
final OfflinePlayer requestedOwner = Bukkit.getOfflinePlayer(request.getOwner());
Bukkit.getServer().getPluginManager().callEvent(new MarketTransactionEvent(
    fulfiller,
    requestedOwner,  // requestedOwner.getName() can be NULL
    ...
));
```

**Fixed:**
Need to get cached owner name from Request object (if available) or MarketUser.

### Layer 2: Fallback in Transaction Listener ✅ SAFETY NET

**Modify `MarketTransactionListener.java` to handle NULL names with fallbacks**

**Current (VULNERABLE):**
```java
public void onTransactionEvent(final MarketTransactionEvent event) {
    final Transaction transaction = new MarketTransaction(
        UUID.randomUUID(),
        event.getBuyer().getUniqueId(),
        event.getBuyer().getName(),  // Can be null
        event.getSeller().getUniqueId(),
        event.getSeller().getUniqueId().equals(UUID.fromString(Settings.SERVER_MARKET_UUID.getString()))
            ? Settings.NAME.getString()
            : event.getSeller().getName(),  // Can be null for player markets
        ...
    );
}
```

**Fixed with NULL-safe fallback:**
```java
public void onTransactionEvent(final MarketTransactionEvent event) {
    // Get buyer name with fallback
    String buyerName = event.getBuyer().getName();
    if (buyerName == null || buyerName.isEmpty()) {
        // Fallback 1: Try MarketUser cache
        MarketUser buyer = Markets.getPlayerManager().get(event.getBuyer().getUniqueId());
        if (buyer != null) {
            buyerName = buyer.getLastKnownName();
        } else {
            // Fallback 2: Use UUID as last resort
            buyerName = "Unknown-" + event.getBuyer().getUniqueId().toString().substring(0, 8);
        }
    }

    // Get seller name with fallback
    String sellerName;
    if (event.getSeller().getUniqueId().equals(UUID.fromString(Settings.SERVER_MARKET_UUID.getString()))) {
        // Server market - use configured name
        sellerName = Settings.NAME.getString();
    } else {
        // Player market - get name safely
        sellerName = event.getSeller().getName();

        if (sellerName == null || sellerName.isEmpty()) {
            // Fallback 1: Try MarketUser cache
            MarketUser seller = Markets.getPlayerManager().get(event.getSeller().getUniqueId());
            if (seller != null) {
                sellerName = seller.getLastKnownName();
            } else {
                // Fallback 2: Use UUID as last resort
                sellerName = "Unknown-" + event.getSeller().getUniqueId().toString().substring(0, 8);
            }
        }
    }

    final Transaction transaction = new MarketTransaction(
        UUID.randomUUID(),
        event.getBuyer().getUniqueId(),
        buyerName,  // Never null
        event.getSeller().getUniqueId(),
        sellerName,  // Never null
        event.getType(),
        event.getItem(),
        event.getCurrency(),
        event.getQuantity(),
        event.getPrice(),
        System.currentTimeMillis()
    );

    transaction.store(storeTransaction -> {
        if (storeTransaction == null) {
            Common.log("&cFailed to store transaction: " + transaction.getId());
            Common.log("&cBuyer: " + buyerName + " (" + event.getBuyer().getUniqueId() + ")");
            Common.log("&cSeller: " + sellerName + " (" + event.getSeller().getUniqueId() + ")");
        } else {
            Markets.getTransactionManager().add(storeTransaction);
        }
    });
}
```

### Layer 3: Database Validation ✅ LAST RESORT

**Add validation before database INSERT**

**File:** `DataManager.java`

**Add validation in `createTransaction` method:**

```java
public void createTransaction(@NonNull final Transaction transaction, final Callback<Transaction> callback) {
    // VALIDATE before attempting insert
    if (transaction.getBuyerName() == null || transaction.getBuyerName().isEmpty()) {
        Common.log("&cCannot create transaction: buyer_name is null");
        Common.log("&cBuyer UUID: " + transaction.getBuyer());
        if (callback != null) {
            callback.accept(new Exception("buyer_name is null"), null);
        }
        return;
    }

    if (transaction.getSellerName() == null || transaction.getSellerName().isEmpty()) {
        Common.log("&cCannot create transaction: seller_name is null");
        Common.log("&cSeller UUID: " + transaction.getSeller());
        if (callback != null) {
            callback.accept(new Exception("seller_name is null"), null);
        }
        return;
    }

    // Proceed with insert
    this.runAsync(() -> this.databaseConnector.connect(connection -> {
        final String query = "INSERT INTO " + this.getTablePrefix() + "transaction (id, buyer, buyer_name, seller, seller_name, type, item, currency, quantity, price, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // ... rest of existing code
    }));
}
```

## Recommended Implementation

### **Approach: Layer 2 (Transaction Listener Fallback)**

This is the **safest and least invasive** approach because:

✅ **Centralized** - One place to fix (MarketTransactionListener)
✅ **No API changes** - Event structure stays the same
✅ **Backward compatible** - Works with existing code
✅ **Comprehensive** - Handles all edge cases
✅ **Fallback chain** - Multiple safety nets

### **Why Not Layer 1 (Event Creation)?**

- Requires changes in 2+ locations (CategoryItem, RequestsGUI)
- Need to pass cached names through multiple layers
- More complex refactoring
- Can still implement Layer 2 as safety net anyway

### **Why Not Layer 3 (Database Validation)?**

- Fails silently (transaction not recorded)
- Doesn't fix the root cause
- Still need to handle NULL somewhere
- Better as addition to Layer 2, not replacement

## Implementation Steps

### Step 1: Update MarketTransactionListener

**File:** `src/main/java/ca/tweetzy/markets/listeners/MarketTransactionListener.java`

```java
package ca.tweetzy.markets.listeners;

import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.event.MarketTransactionEvent;
import ca.tweetzy.markets.api.market.Transaction;
import ca.tweetzy.markets.api.market.core.MarketUser;
import ca.tweetzy.markets.impl.MarketTransaction;
import ca.tweetzy.markets.settings.Settings;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public final class MarketTransactionListener implements Listener {

    @EventHandler
    public void onTransactionEvent(final MarketTransactionEvent event) {
        // Safely extract buyer name with fallback chain
        String buyerName = getSafePlayerName(event.getBuyer().getUniqueId(), event.getBuyer().getName(), "Buyer");

        // Safely extract seller name with fallback chain
        String sellerName;
        if (event.getSeller().getUniqueId().equals(UUID.fromString(Settings.SERVER_MARKET_UUID.getString()))) {
            // Server market - use configured name
            sellerName = Settings.NAME.getString();
        } else {
            // Player market - get name safely
            sellerName = getSafePlayerName(event.getSeller().getUniqueId(), event.getSeller().getName(), "Seller");
        }

        // Create transaction with guaranteed non-null names
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

        // Store transaction
        transaction.store(storeTransaction -> {
            if (storeTransaction == null) {
                Common.log("&cFailed to store transaction: " + transaction.getId());
                Common.log("&c  Buyer: " + buyerName + " (" + event.getBuyer().getUniqueId() + ")");
                Common.log("&c  Seller: " + sellerName + " (" + event.getSeller().getUniqueId() + ")");
                Common.log("&c  Type: " + event.getType());
                Common.log("&c  Item: " + event.getItem().getType());
                Common.log("&c  Quantity: " + event.getQuantity());
            } else {
                Markets.getTransactionManager().add(storeTransaction);
            }
        });
    }

    /**
     * Safely get player name with fallback chain
     *
     * @param uuid Player UUID
     * @param offlinePlayerName Name from OfflinePlayer.getName() (can be null)
     * @param role Role description for logging (e.g., "Buyer", "Seller")
     * @return Non-null player name
     */
    private String getSafePlayerName(UUID uuid, String offlinePlayerName, String role) {
        // Try OfflinePlayer name first
        if (offlinePlayerName != null && !offlinePlayerName.isEmpty()) {
            return offlinePlayerName;
        }

        // Fallback 1: Try MarketUser cache
        MarketUser user = Markets.getPlayerManager().get(uuid);
        if (user != null && user.getLastKnownName() != null && !user.getLastKnownName().isEmpty()) {
            Common.log("&eWarning: " + role + " name was null, using cached name: " + user.getLastKnownName());
            return user.getLastKnownName();
        }

        // Fallback 2: Use UUID prefix as last resort
        String fallbackName = "Unknown-" + uuid.toString().substring(0, 8);
        Common.log("&cWarning: " + role + " name was null and no cache available, using fallback: " + fallbackName);
        return fallbackName;
    }
}
```

### Step 2: Add Database Validation (Optional Safety Layer)

**File:** `src/main/java/ca/tweetzy/markets/database/DataManager.java`

Add validation at the beginning of `createTransaction`:

```java
public void createTransaction(@NonNull final Transaction transaction, final Callback<Transaction> callback) {
    // Validate transaction data before attempting insert
    if (transaction.getBuyerName() == null || transaction.getBuyerName().isEmpty()) {
        String error = "Cannot create transaction: buyer_name is null or empty (Buyer UUID: " + transaction.getBuyer() + ")";
        Common.log("&c" + error);
        if (callback != null) {
            callback.accept(new IllegalArgumentException(error), null);
        }
        return;
    }

    if (transaction.getSellerName() == null || transaction.getSellerName().isEmpty()) {
        String error = "Cannot create transaction: seller_name is null or empty (Seller UUID: " + transaction.getSeller() + ")";
        Common.log("&c" + error);
        if (callback != null) {
            callback.accept(new IllegalArgumentException(error), null);
        }
        return;
    }

    // Existing code continues...
    this.runAsync(() -> this.databaseConnector.connect(connection -> {
        // ... existing insert code
    }));
}
```

## Testing Strategy

### Test Case 1: Normal Transaction (Player vs Player)
- **Setup:** Two online players
- **Action:** Player A buys from Player B's market
- **Expected:** Transaction recorded with both player names

### Test Case 2: Offline Market Owner
- **Setup:** Player A (online) buys from Player B's market (B offline, never joined)
- **Action:** Trigger purchase
- **Expected:** Transaction recorded with Player A name and fallback for Player B

### Test Case 3: Server Market
- **Setup:** Player buys from server market
- **Action:** Trigger purchase
- **Expected:** Transaction recorded with server market name from config

### Test Case 4: Request Fulfillment
- **Setup:** Player fulfills request from offline player
- **Action:** Fulfill request
- **Expected:** Transaction recorded with cached names

### Test Case 5: NULL Name Fallback
- **Setup:** Create scenario where OfflinePlayer.getName() returns null
- **Action:** Trigger transaction
- **Expected:** Fallback to MarketUser cache, then UUID prefix if needed

## Monitoring & Logging

### Add Metrics

Track fallback usage to identify patterns:

```java
private static int nullNameWarningCount = 0;

private String getSafePlayerName(UUID uuid, String offlinePlayerName, String role) {
    if (offlinePlayerName != null && !offlinePlayerName.isEmpty()) {
        return offlinePlayerName;
    }

    nullNameWarningCount++;

    // Log warning every 10 occurrences
    if (nullNameWarningCount % 10 == 0) {
        Common.log("&eWarning: NULL player names encountered " + nullNameWarningCount + " times");
    }

    // ... rest of fallback logic
}
```

## Benefits

✅ **Zero transaction failures** - All names guaranteed non-null
✅ **Graceful degradation** - Falls back through multiple sources
✅ **Comprehensive logging** - Identifies when fallbacks are used
✅ **Backward compatible** - No API or event changes required
✅ **Centralized fix** - One file to update
✅ **Production safe** - Can deploy immediately

## Risks & Mitigation

### Risk: Fallback names not user-friendly

**Mitigation:**
- Primary fallback uses cached `LastKnownName` (usually correct)
- UUID fallback only as last resort
- Log warnings to identify problematic cases

### Risk: Performance impact from MarketUser lookups

**Mitigation:**
- MarketUser is already in-memory cache (fast)
- Only called when OfflinePlayer.getName() returns null
- Negligible performance impact

## Conclusion

This plan provides **multi-layer protection** against transaction recording failures:

1. **Layer 2 (Primary):** Transaction listener with NULL-safe name extraction
2. **Layer 3 (Optional):** Database validation as final safety check

Implementation is:
- **Simple** - Minimal code changes
- **Safe** - Multiple fallback mechanisms
- **Fast** - Can be implemented and tested quickly
- **Comprehensive** - Handles all edge cases

The root cause (OfflinePlayer.getName() returning NULL) is addressed through defensive programming with a clear fallback chain.
