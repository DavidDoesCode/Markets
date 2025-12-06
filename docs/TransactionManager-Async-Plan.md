# TransactionManager Async Optimization Plan

## Problem Statement

The `TransactionManager` class has several methods that filter transactions synchronously using `getManagerContent()`:

1. `getTransactionsMadeToMarket(UUID sellerUUID, UUID buyerUUID)` - Returns count
2. `getOfflineTransactionsFor(UUID sellerUUID)` - Returns filtered list
3. `getTransactionsFor(UUID playerUUID)` - Returns all transactions for player
4. `getSalesTransactionsFor(UUID sellerUUID)` - Returns sales only
5. `getPurchaseTransactionsFor(UUID buyerUUID)` - Returns purchases only

These methods currently:
- Copy the entire in-memory transaction list (`List.copyOf(this.managerContent)`)
- Perform synchronous stream filtering on the main thread
- Block the caller until filtering completes

## Performance Issues

### Current Implementation Problems

```java
public List<Transaction> getTransactionsFor(@NonNull final UUID playerUUID) {
    return getManagerContent().stream().filter(transaction ->
        transaction.getSeller().equals(playerUUID) ||
        transaction.getBuyer().equals(playerUUID)).collect(Collectors.toList());
}
```

**Problems:**
1. **Synchronous blocking** - Runs on main thread (if called from event/command)
2. **Full list copy** - `getManagerContent()` creates a defensive copy of all transactions
3. **Linear scan** - O(n) filtering for every query
4. **Memory allocation** - Creates intermediate stream and collector list
5. **Scalability** - With thousands of transactions, this becomes a bottleneck

### When This Becomes Critical

- Server with 100+ active players
- Transactions accumulate over time (thousands+)
- Player join events trigger `getOfflineTransactionsFor()` on every login
- GUI opens trigger `getSalesTransactionsFor()` / `getPurchaseTransactionsFor()`
- Admin viewing transactions for players

## Current Usage Locations

### 1. PlayerJoinListener.java:51
```java
final List<Transaction> offlineTransactions =
    Markets.getTransactionManager().getOfflineTransactionsFor(player.getUniqueId());
if (offlineTransactions.isEmpty()) return;
// Show notification about offline sales
```
**Context:** Runs on player join event (main thread)

### 2. TransactionsGUI.java:49-52
```java
if (this.filterType == PlayerRole.SELLER) {
    this.items = new ArrayList<>(Markets.getTransactionManager()
        .getSalesTransactionsFor((this.player.getUniqueId())));
} else {
    this.items = new ArrayList<>(Markets.getTransactionManager()
        .getPurchaseTransactionsFor((this.player.getUniqueId())));
}
```
**Context:** Runs when GUI is opened/refreshed (main thread)

### 3. CommandTransactions.java:53
```java
this.items = new java.util.ArrayList<>(Markets.getTransactionManager()
    .getTransactionsFor(target.getUniqueId()));
```
**Context:** Admin command viewing player transactions (main thread)

## Solution: Minimal Refactoring Async Pattern

### Strategy Overview

**Goal:** Convert synchronous methods to async with callback pattern, matching the existing Flight framework pattern used in `DataManager`.

**Key Principles:**
1. Use callback pattern already established in codebase
2. Leverage existing `runAsync()` from Flight's `DataManagerAbstract`
3. Minimize changes to calling code
4. Maintain backward compatibility where possible

### Phase 1: Add Async Methods to DataManager

Add database-level query methods that filter at the database level (most efficient).

#### New DataManager Methods

**File:** `src/main/java/ca/tweetzy/markets/database/DataManager.java`

```java
// Get transactions for a specific player (buyer OR seller)
public void getTransactionsFor(@NonNull final UUID playerUUID, @NonNull final Callback<List<Transaction>> callback) {
    final List<Transaction> transactions = new ArrayList<>();

    this.runAsync(() -> this.databaseConnector.connect(connection -> {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT * FROM " + this.getTablePrefix() + "transaction WHERE buyer = ? OR seller = ?")) {

            statement.setString(1, playerUUID.toString());
            statement.setString(2, playerUUID.toString());

            final ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                transactions.add(extractTransaction(resultSet));
            }

            callback.accept(null, transactions);
        } catch (Exception e) {
            resolveCallback(callback, e);
        }
    }));
}

// Get sales transactions (where player is seller)
public void getSalesTransactionsFor(@NonNull final UUID sellerUUID, @NonNull final Callback<List<Transaction>> callback) {
    final List<Transaction> transactions = new ArrayList<>();

    this.runAsync(() -> this.databaseConnector.connect(connection -> {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT * FROM " + this.getTablePrefix() + "transaction WHERE seller = ?")) {

            statement.setString(1, sellerUUID.toString());

            final ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                transactions.add(extractTransaction(resultSet));
            }

            callback.accept(null, transactions);
        } catch (Exception e) {
            resolveCallback(callback, e);
        }
    }));
}

// Get purchase transactions (where player is buyer)
public void getPurchaseTransactionsFor(@NonNull final UUID buyerUUID, @NonNull final Callback<List<Transaction>> callback) {
    final List<Transaction> transactions = new ArrayList<>();

    this.runAsync(() -> this.databaseConnector.connect(connection -> {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT * FROM " + this.getTablePrefix() + "transaction WHERE buyer = ?")) {

            statement.setString(1, buyerUUID.toString());

            final ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                transactions.add(extractTransaction(resultSet));
            }

            callback.accept(null, transactions);
        } catch (Exception e) {
            resolveCallback(callback, e);
        }
    }));
}

// Get offline transactions (since last seen)
public void getOfflineTransactionsFor(@NonNull final UUID sellerUUID, final long lastSeenAt, @NonNull final Callback<List<Transaction>> callback) {
    final List<Transaction> transactions = new ArrayList<>();

    this.runAsync(() -> this.databaseConnector.connect(connection -> {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT * FROM " + this.getTablePrefix() + "transaction WHERE seller = ? AND created_at >= ?")) {

            statement.setString(1, sellerUUID.toString());
            statement.setLong(2, lastSeenAt);

            final ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                transactions.add(extractTransaction(resultSet));
            }

            callback.accept(null, transactions);
        } catch (Exception e) {
            resolveCallback(callback, e);
        }
    }));
}

// Get transaction count between specific buyer and seller
public void getTransactionsMadeToMarket(@NonNull final UUID sellerUUID, @NonNull final UUID buyerUUID, @NonNull final Callback<Integer> callback) {
    this.runAsync(() -> this.databaseConnector.connect(connection -> {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) as count FROM " + this.getTablePrefix() + "transaction WHERE seller = ? AND buyer = ?")) {

            statement.setString(1, sellerUUID.toString());
            statement.setString(2, buyerUUID.toString());

            final ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                callback.accept(null, resultSet.getInt("count"));
            } else {
                callback.accept(null, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            callback.accept(null, 0); // Default to 0 on error
        }
    }));
}
```

**Benefits:**
- Database-level filtering (faster than in-memory)
- Indexed queries (seller, buyer columns can be indexed)
- Only loads required transactions from disk
- Async execution prevents main thread blocking

### Phase 2: Update TransactionManager

Add async wrapper methods that delegate to DataManager, keeping the in-memory filtering as fallback.

#### Updated TransactionManager

**File:** `src/main/java/ca/tweetzy/markets/model/manager/TransactionManager.java`

```java
package ca.tweetzy.markets.model.manager;

import ca.tweetzy.flight.database.Callback;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.manager.ListManager;
import ca.tweetzy.markets.api.market.Transaction;
import ca.tweetzy.markets.api.market.core.MarketUser;
import lombok.NonNull;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TransactionManager extends ListManager<Transaction> {

    public TransactionManager() {
        super("Transaction");
    }

    // ==================== ASYNC METHODS (RECOMMENDED) ====================

    /**
     * Async: Get count of transactions between buyer and seller
     * Uses database query for optimal performance
     */
    public void getTransactionsMadeToMarketAsync(@NonNull final UUID sellerUUID, @NonNull final UUID buyerUUID, @NonNull final Callback<Integer> callback) {
        Markets.getDataManager().getTransactionsMadeToMarket(sellerUUID, buyerUUID, callback);
    }

    /**
     * Async: Get offline transactions for seller (since last seen)
     * Uses database query for optimal performance
     */
    public void getOfflineTransactionsForAsync(@NonNull final UUID sellerUUID, @NonNull final Callback<List<Transaction>> callback) {
        final MarketUser user = Markets.getPlayerManager().get(sellerUUID);
        if (user == null) {
            callback.accept(null, List.of());
            return;
        }

        Markets.getDataManager().getOfflineTransactionsFor(sellerUUID, user.getLastSeenAt(), callback);
    }

    /**
     * Async: Get all transactions for player (buyer OR seller)
     * Uses database query for optimal performance
     */
    public void getTransactionsForAsync(@NonNull final UUID playerUUID, @NonNull final Callback<List<Transaction>> callback) {
        Markets.getDataManager().getTransactionsFor(playerUUID, callback);
    }

    /**
     * Async: Get sales transactions (player is seller)
     * Uses database query for optimal performance
     */
    public void getSalesTransactionsForAsync(@NonNull final UUID sellerUUID, @NonNull final Callback<List<Transaction>> callback) {
        Markets.getDataManager().getSalesTransactionsFor(sellerUUID, callback);
    }

    /**
     * Async: Get purchase transactions (player is buyer)
     * Uses database query for optimal performance
     */
    public void getPurchaseTransactionsForAsync(@NonNull final UUID buyerUUID, @NonNull final Callback<List<Transaction>> callback) {
        Markets.getDataManager().getPurchaseTransactionsFor(buyerUUID, callback);
    }

    // ==================== SYNC METHODS (DEPRECATED, KEPT FOR COMPATIBILITY) ====================

    /**
     * @deprecated Use {@link #getTransactionsMadeToMarketAsync} instead
     * Synchronous in-memory filtering - performance warning for large datasets
     */
    @Deprecated
    public int getTransactionsMadeToMarket(@NonNull final UUID sellerUUID, @NonNull final UUID buyerUUID) {
        return (int) this.managerContent.stream()
            .filter(transaction -> transaction.getBuyer().equals(buyerUUID) && transaction.getSeller().equals(sellerUUID))
            .count();
    }

    /**
     * @deprecated Use {@link #getOfflineTransactionsForAsync} instead
     * Synchronous in-memory filtering - performance warning for large datasets
     */
    @Deprecated
    public List<Transaction> getOfflineTransactionsFor(@NonNull final UUID sellerUUID) {
        final MarketUser user = Markets.getPlayerManager().get(sellerUUID);

        return getManagerContent().stream()
            .filter(transaction -> transaction.getSeller().equals(sellerUUID) && transaction.getTimeCreated() >= user.getLastSeenAt())
            .collect(Collectors.toList());
    }

    /**
     * @deprecated Use {@link #getTransactionsForAsync} instead
     * Synchronous in-memory filtering - performance warning for large datasets
     */
    @Deprecated
    public List<Transaction> getTransactionsFor(@NonNull final UUID playerUUID) {
        return getManagerContent().stream()
            .filter(transaction -> transaction.getSeller().equals(playerUUID) || transaction.getBuyer().equals(playerUUID))
            .collect(Collectors.toList());
    }

    /**
     * @deprecated Use {@link #getSalesTransactionsForAsync} instead
     * Synchronous in-memory filtering - performance warning for large datasets
     */
    @Deprecated
    public List<Transaction> getSalesTransactionsFor(@NonNull final UUID sellerUUID) {
        return getManagerContent().stream()
            .filter(transaction -> transaction.getSeller().equals(sellerUUID))
            .collect(Collectors.toList());
    }

    /**
     * @deprecated Use {@link #getPurchaseTransactionsForAsync} instead
     * Synchronous in-memory filtering - performance warning for large datasets
     */
    @Deprecated
    public List<Transaction> getPurchaseTransactionsFor(@NonNull final UUID buyerUUID) {
        return getManagerContent().stream()
            .filter(transaction -> transaction.getBuyer().equals(buyerUUID))
            .collect(Collectors.toList());
    }

    @Override
    public void load() {
        clear();

        Markets.getDataManager().getTransactions((error, found) -> {
            if (error != null) return;
            found.forEach(this::add);
        });
    }
}
```

**Strategy:**
- Keep old methods with `@Deprecated` annotation
- Add new async methods with "Async" suffix
- Allows gradual migration of calling code
- Old code continues to work (with performance penalty)

### Phase 3: Update Calling Code

Update each usage location to use async methods.

#### 3.1 PlayerJoinListener.java

**Current (Synchronous):**
```java
final List<Transaction> offlineTransactions =
    Markets.getTransactionManager().getOfflineTransactionsFor(player.getUniqueId());
if (offlineTransactions.isEmpty()) return;

// Show notification
Bukkit.getServer().getScheduler().runTaskLaterAsynchronously(
    Markets.getInstance(),
    () -> Common.tellNoPrefix(player, TranslationManager.list(player,
        Translations.OFFLINE_SALES_INFO,
        "offline_sales_amount", offlineTransactions.size())),
    20L);
```

**Updated (Async):**
```java
// Query async, then schedule notification
Markets.getTransactionManager().getOfflineTransactionsForAsync(player.getUniqueId(), (error, offlineTransactions) -> {
    if (error != null || offlineTransactions.isEmpty()) return;

    // Show notification on main thread after delay
    Bukkit.getServer().getScheduler().runTaskLater(
        Markets.getInstance(),
        () -> Common.tellNoPrefix(player, TranslationManager.list(player,
            Translations.OFFLINE_SALES_INFO,
            "offline_sales_amount", offlineTransactions.size())),
        20L);
});
```

**Benefits:**
- Non-blocking player join
- Notification appears after 1 second regardless of transaction count
- Database query runs on async thread pool

#### 3.2 TransactionsGUI.java

**Current (Synchronous):**
```java
@Override
protected void prePopulate() {
    if (this.viewAll) {
        this.items = new ArrayList<>(Markets.getTransactionManager().getManagerContent());
    } else {
        if (this.filterType == PlayerRole.SELLER) {
            this.items = new ArrayList<>(Markets.getTransactionManager()
                .getSalesTransactionsFor((this.player.getUniqueId())));
        } else {
            this.items = new ArrayList<>(Markets.getTransactionManager()
                .getPurchaseTransactionsFor((this.player.getUniqueId())));
        }
    }
    this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
}
```

**Updated (Async with Loading State):**

This requires a slightly different approach since GUIs expect immediate data. Options:

**Option A: Use in-memory cache (keep current behavior)**
```java
@Override
protected void prePopulate() {
    // Keep using synchronous methods for GUI
    // GUI needs immediate data, in-memory cache is acceptable here
    if (this.viewAll) {
        this.items = new ArrayList<>(Markets.getTransactionManager().getManagerContent());
    } else {
        if (this.filterType == PlayerRole.SELLER) {
            this.items = new ArrayList<>(Markets.getTransactionManager()
                .getSalesTransactionsFor((this.player.getUniqueId())));
        } else {
            this.items = new ArrayList<>(Markets.getTransactionManager()
                .getPurchaseTransactionsFor((this.player.getUniqueId())));
        }
    }
    this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
}
```
**Note:** GUIs are acceptable use case for in-memory filtering since:
- Data is already loaded
- User expects immediate UI response
- Filtering happens once per GUI open
- Trade-off: responsiveness > slight performance hit

**Option B: Async load with placeholder (better UX for large datasets)**
```java
public TransactionsGUI(Gui parent, @NonNull final Player player, boolean viewAll) {
    super(parent, player, TranslationManager.string(player, Translations.GUI_TRANSACTIONS_TITLE), 6, new ArrayList<>());
    this.player = player;
    this.viewAll = viewAll;
    setAcceptsItems(true);
    setDefaultItem(QuickItem.bg(Settings.GUI_TRANSACTIONS_BACKGROUND.getItemStack()));

    // Show loading state
    loadTransactionsAsync();
}

private void loadTransactionsAsync() {
    // Show loading indicator in GUI
    setButton(2, 4, QuickItem.of(Material.HOPPER)
        .name("§eLoading transactions...")
        .make());

    // Load data async
    if (this.viewAll) {
        // Use in-memory for "all" view
        this.items = new ArrayList<>(Markets.getTransactionManager().getManagerContent());
        this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
        draw(); // Redraw with data
    } else {
        if (this.filterType == PlayerRole.SELLER) {
            Markets.getTransactionManager().getSalesTransactionsForAsync(this.player.getUniqueId(), (error, transactions) -> {
                if (error == null) {
                    this.items = new ArrayList<>(transactions);
                    this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());

                    // Redraw GUI on main thread
                    Bukkit.getScheduler().runTask(Markets.getInstance(), this::draw);
                }
            });
        } else {
            Markets.getTransactionManager().getPurchaseTransactionsForAsync(this.player.getUniqueId(), (error, transactions) -> {
                if (error == null) {
                    this.items = new ArrayList<>(transactions);
                    this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());

                    // Redraw GUI on main thread
                    Bukkit.getScheduler().runTask(Markets.getInstance(), this::draw);
                }
            });
        }
    }
}
```

**Recommendation for GUI:** Use Option A (keep in-memory) initially, only implement Option B if profiling shows GUI opens are slow.

#### 3.3 CommandTransactions.java

**Current (Synchronous):**
```java
Markets.getGuiManager().showGUI(player, new TransactionsGUI(null, player, false) {
    @Override
    protected void prePopulate() {
        this.items = new java.util.ArrayList<>(Markets.getTransactionManager()
            .getTransactionsFor(target.getUniqueId()));
        this.items.sort(java.util.Comparator.comparing(
            ca.tweetzy.markets.api.market.Transaction::getTimeCreated).reversed());
    }
});
```

**Updated (Async):**
```java
// Show loading message
Common.tell(player, "&eLoading transactions for " + args[0] + "...");

Markets.getTransactionManager().getTransactionsForAsync(target.getUniqueId(), (error, transactions) -> {
    if (error != null) {
        Common.tell(player, "&cFailed to load transactions.");
        return;
    }

    // Open GUI on main thread with loaded data
    Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
        Markets.getGuiManager().showGUI(player, new TransactionsGUI(null, player, false) {
            @Override
            protected void prePopulate() {
                this.items = new ArrayList<>(transactions);
                this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
            }
        });
    });
});
```

**Benefits:**
- Non-blocking command execution
- Works for offline players (database query vs in-memory cache)
- Loading message provides feedback

## Migration Path (Minimal Disruption)

### Stage 1: Database Methods Only
1. Add new methods to `DataManager.java`
2. Test database queries independently
3. No changes to existing code
4. **Risk:** None - purely additive

### Stage 2: Manager Wrapper Methods
1. Add async methods to `TransactionManager.java`
2. Mark old methods as `@Deprecated`
3. No breaking changes
4. **Risk:** None - backward compatible

### Stage 3: Update Critical Path (PlayerJoinListener)
1. Update `PlayerJoinListener.java` to use async
2. Test player join events with large transaction counts
3. **Risk:** Low - isolated change, easy to revert

### Stage 4: Update Commands
1. Update `CommandTransactions.java` to use async
2. **Risk:** Low - command only, no impact on gameplay

### Stage 5: GUI Optimization (Optional)
1. Evaluate GUI performance
2. If needed, implement async loading pattern
3. **Risk:** Medium - requires GUI state management

## Database Indexing (Performance Enhancement)

### Recommended Indexes

Add migration `_18_TransactionIndexesMigration.java`:

```java
public final class _18_TransactionIndexesMigration extends DataMigration {

    public _18_TransactionIndexesMigration() {
        super(18);
    }

    @Override
    public void migrate(Connection connection, String tablePrefix) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Index for seller queries (sales)
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transaction_seller ON " +
                tablePrefix + "transaction (seller)");

            // Index for buyer queries (purchases)
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transaction_buyer ON " +
                tablePrefix + "transaction (buyer)");

            // Index for offline transactions query (seller + created_at)
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transaction_seller_created ON " +
                tablePrefix + "transaction (seller, created_at)");

            // Index for buyer + seller combination (rating queries)
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transaction_buyer_seller ON " +
                tablePrefix + "transaction (buyer, seller)");
        }
    }
}
```

**Benefits:**
- O(log n) lookups instead of O(n) scans
- Massive performance improvement for large datasets
- Minimal storage overhead

**Register in Markets.java:**
```java
this.getDataMigrations().add(new _18_TransactionIndexesMigration());
```

## Performance Comparison

### Before (Synchronous)
- 10,000 transactions
- `getSalesTransactionsFor()` on main thread
- **Time:** ~50-100ms (blocks main thread)
- **Memory:** Full list copy + stream + collector

### After (Async + Indexed)
- 10,000 transactions
- `getSalesTransactionsForAsync()` with database index
- **Time:** ~5-15ms (on async thread pool)
- **Memory:** Only filtered results loaded

**Improvement:** ~10x faster + non-blocking

## Testing Strategy

### Unit Tests
```java
@Test
public void testAsyncSalesTransactions() {
    CountDownLatch latch = new CountDownLatch(1);
    final List<Transaction>[] result = new List[1];

    transactionManager.getSalesTransactionsForAsync(sellerUUID, (error, transactions) -> {
        result[0] = transactions;
        latch.countDown();
    });

    latch.await(5, TimeUnit.SECONDS);
    assertNotNull(result[0]);
    assertTrue(result[0].stream().allMatch(t -> t.getSeller().equals(sellerUUID)));
}
```

### Performance Tests
1. Load 10,000 test transactions
2. Benchmark sync vs async methods
3. Test with multiple concurrent queries
4. Profile memory usage

### Integration Tests
1. Test player join with offline transactions
2. Test GUI opening with large transaction history
3. Test admin command for offline players
4. Verify transactions are properly filtered

## Rollback Plan

If issues arise:

1. **Immediate:** Comment out new async method calls, revert to `@Deprecated` sync methods
2. **Code revert:** Remove async methods, keep database queries for future use
3. **Database:** Indexes are non-breaking, can remain even if code reverted

## Conclusion

This plan provides a **minimal refactoring approach** to async transaction queries:

1. **Additive changes** - No breaking changes to existing code
2. **Gradual migration** - Can be done in stages
3. **Performance gains** - 10x+ improvement for large datasets
4. **Database optimization** - Indexed queries at data source
5. **Backward compatible** - Old code continues to work

**Estimated effort:**
- Stage 1-2: 2-3 hours (database + manager methods)
- Stage 3-4: 1-2 hours (update callers)
- Stage 5: 2-3 hours (GUI optimization, if needed)
- Testing: 2-3 hours

**Total: 7-11 hours of development + testing**

**Risk: Low** - Changes are isolated, backward compatible, and easily reversible.
