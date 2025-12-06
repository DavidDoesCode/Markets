# Timeout Analysis - Async Operations

## Analysis Date
2025-12-06

## Context
Server experiencing lockup/timeout symptoms. Investigation into async operations and potential blocking code in recent changes.

## Code Changes Reviewed

### 1. PlayerJoinListener (lines 52-63)
**Location:** `src/main/java/ca/tweetzy/markets/listeners/PlayerJoinListener.java`

**Code:**
```java
// Check for offline transactions asynchronously
Markets.getTransactionManager().getOfflineTransactionsForAsync(player.getUniqueId(), offlineTransactions -> {
    if (offlineTransactions.isEmpty()) return;

    // Show notification after 1 second delay
    Bukkit.getServer().getScheduler().runTaskLater(
            Markets.getInstance(),
            () -> Common.tellNoPrefix(player, TranslationManager.list(player,
                    Translations.OFFLINE_SALES_INFO,
                    "offline_sales_amount", offlineTransactions.size())),
            20L
    );
});
```

**Analysis:** ✅ **Non-blocking**
- Calls async method
- Callback is non-blocking
- No synchronous waits

---

### 2. TransactionManager.getOfflineTransactionsForAsync() (lines 90-107)
**Location:** `src/main/java/ca/tweetzy/markets/model/manager/TransactionManager.java`

**Code:**
```java
public void getOfflineTransactionsForAsync(@NonNull final UUID sellerUUID, @NonNull final Consumer<List<Transaction>> callback) {
    final MarketUser user = Markets.getPlayerManager().get(sellerUUID);
    if (user == null) {
        callback.accept(List.of());  // ← Line 93
        return;
    }

    // Run filtering on async thread
    Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
        // Perform filtering off main thread
        final List<Transaction> transactions = getManagerContent().stream()
                .filter(transaction -> transaction.getSeller().equals(sellerUUID) && transaction.getTimeCreated() >= user.getLastSeenAt())
                .collect(Collectors.toList());

        // Deliver result back on main thread (required for GUI updates)
        Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(transactions));
    });
}
```

**Initial Concern:** Line 93 - callback executed synchronously when user is null

**Analysis:** ✅ **Non-blocking**
- `callback.accept(List.of())` returns instantly
- No wait or blocking operation
- Empty list creation is O(1)
- This is NOT a timeout risk

---

### 3. MarketTransactionListener.getSafePlayerName() (lines 73-90)
**Location:** `src/main/java/ca/tweetzy/markets/listeners/MarketTransactionListener.java`

**Code:**
```java
private String getSafePlayerName(UUID uuid, String offlinePlayerName, String role) {
    // Try OfflinePlayer name first
    if (offlinePlayerName != null && !offlinePlayerName.isEmpty()) {
        return offlinePlayerName;
    }

    // Fallback 1: Try MarketUser cache
    MarketUser user = Markets.getPlayerManager().get(uuid);  // ← In-memory lookup
    if (user != null && user.getLastKnownName() != null && !user.getLastKnownName().isEmpty()) {
        Common.log("&eWarning: " + role + " name was null, using cached name: " + user.getLastKnownName());
        return user.getLastKnownName();
    }

    // Fallback 2: Use UUID prefix as last resort
    String fallbackName = "Unknown-" + uuid.toString().substring(0, 8);
    Common.log("&cWarning: " + role + " name was null and no cache available, using fallback: " + fallbackName);
    return fallbackName;
}
```

**Analysis:** ✅ **Non-blocking**
- `Markets.getPlayerManager().get(uuid)` is in-memory map lookup
- No database calls
- No network operations
- All operations are O(1) or O(log n)

---

### 4. TransactionsGUI.loadTransactionsAsync() (lines 57-101)
**Location:** `src/main/java/ca/tweetzy/markets/gui/user/TransactionsGUI.java`

**Code:**
```java
private void loadTransactionsAsync() {
    this.isLoading = true;
    this.dataLoaded = false;
    this.items = new ArrayList<>();  // Clear items immediately

    if (this.viewAll) {
        // For "view all", use synchronous (already in memory, no filtering needed)
        this.items = new ArrayList<>(Markets.getTransactionManager().getManagerContent());
        this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
        this.isLoading = false;
        this.dataLoaded = true;
        draw();  // ← Line 68
    } else {
        // Show loading indicator for async operations (only if GUI already shown)
        if (this.guiShown) {
            draw();  // ← Line 72
        }

        if (this.filterType == PlayerRole.SELLER) {
            // Load sales transactions async
            Markets.getTransactionManager().getSalesTransactionsForAsync(this.player.getUniqueId(), transactions -> {
                this.items = new ArrayList<>(transactions);
                this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
                this.isLoading = false;
                this.dataLoaded = true;
                draw();  // ← Line 85
            });
        } else {
            // Load purchase transactions async
            Markets.getTransactionManager().getPurchaseTransactionsForAsync(this.player.getUniqueId(), transactions -> {
                this.items = new ArrayList<>(transactions);
                this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
                this.isLoading = false;
                this.dataLoaded = true;
                draw();  // ← Line 97
            });
        }
    }
}
```

**Potential Risk:** ⚠️ **Possible blocking if draw() is heavy**
- Multiple calls to `draw()` at lines 68, 72, 85, 97
- If GUI rendering is slow with large datasets, this could cause lag
- If `draw()` somehow triggers another `loadTransactionsAsync()`, infinite loop possible
- However, `prePopulate()` is empty to prevent reload loop

**Analysis:** ⚠️ **Monitoring recommended**
- No direct blocking code
- Async pattern is correct
- Risk: Heavy GUI rendering with large transaction lists
- Risk: Potential draw() loop if logic error exists

---

## Async Pattern Analysis

### All Async Methods Follow Proper Pattern:

1. **getSalesTransactionsForAsync()**
2. **getPurchaseTransactionsForAsync()**
3. **getTransactionsForAsync()**
4. **getOfflineTransactionsForAsync()**

**Pattern:**
```java
Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
    // Heavy operation (filtering, sorting) on async thread
    final List<Transaction> transactions = /* ... */;

    // Return to main thread for callback
    Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(transactions));
});
```

✅ **Pattern is correct and non-blocking**

---

## Conclusions

### Non-Blocking Operations (Confirmed Safe):
1. All async transaction filtering methods
2. Player name fallback chain (in-memory lookups only)
3. Offline transaction notifications on player join
4. Callback executions (all instant operations)

### Potential Issues (Not Introduced by Recent Changes):
1. **GUI rendering performance** - `draw()` calls with large datasets
2. **Database operations elsewhere** - Not in reviewed code
3. **Infinite loops** - Protected by empty `prePopulate()` but worth monitoring
4. **Third-party plugins** - Could be unrelated to Markets

### Recommendation:
The recent async changes do **NOT introduce blocking operations**. If server timeout occurs:
- Check server logs for errors during the timeout
- Monitor transaction list sizes (very large lists could slow GUI rendering)
- Check for other plugin conflicts
- Verify database connection health
- Look for errors in console around timeout time

---

## False Alarm Details

### Initial Incorrect Assessment:
Suspected `callback.accept(List.of())` at line 93 of blocking when user is null.

**Why This Was Wrong:**
- `callback.accept(List.of())` executes instantly
- No wait, no loop, no blocking
- Empty list creation is O(1)
- Returns immediately

**Correction:**
This is perfectly safe and non-blocking code.

---

## Code Review Checklist for Future Changes

When adding async operations, verify:
- [ ] Heavy operations run on `runTaskAsynchronously()`
- [ ] Callbacks run on `runTask()` (main thread)
- [ ] No synchronous waits in event handlers
- [ ] No database calls on main thread
- [ ] No infinite loops in draw/update cycles
- [ ] Large dataset operations are chunked or paginated
- [ ] Proper null checks before operations
- [ ] No blocking I/O on main thread

---

## Related Files
- `src/main/java/ca/tweetzy/markets/listeners/PlayerJoinListener.java`
- `src/main/java/ca/tweetzy/markets/listeners/MarketTransactionListener.java`
- `src/main/java/ca/tweetzy/markets/model/manager/TransactionManager.java`
- `src/main/java/ca/tweetzy/markets/gui/user/TransactionsGUI.java`

---

## Document Version
- Created: 2025-12-06
- Last Updated: 2025-12-06
- Status: Analysis Complete - No blocking code found in recent changes
