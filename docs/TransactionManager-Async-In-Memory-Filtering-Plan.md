# TransactionManager Async In-Memory Filtering Plan

## Goal

Convert the synchronous `.stream()` filtering in `TransactionManager` to async execution, allowing GUIs to load data asynchronously after being opened.

**Focus Methods:**
- `getSalesTransactionsFor(UUID sellerUUID)`
- `getPurchaseTransactionsFor(UUID buyerUUID)`

**Requirements:**
- Keep in-memory filtering (no database changes)
- Filter operations run on async thread
- GUI shows loading state, then populates when data is ready
- Minimal refactoring

## Current Implementation (Synchronous)

### TransactionManager.java

```java
public List<Transaction> getSalesTransactionsFor(@NonNull final UUID sellerUUID) {
    return getManagerContent().stream()
        .filter(transaction -> transaction.getSeller().equals(sellerUUID))
        .collect(Collectors.toList());
}

public List<Transaction> getPurchaseTransactionsFor(@NonNull final UUID buyerUUID) {
    return getManagerContent().stream()
        .filter(transaction -> transaction.getBuyer().equals(buyerUUID))
        .collect(Collectors.toList());
}
```

**Problems:**
1. Runs on calling thread (main thread for GUI/commands)
2. Blocks until filtering completes
3. With 10,000+ transactions, can cause lag spike

### TransactionsGUI.java (Current Usage)

```java
@Override
protected void prePopulate() {
    if (this.viewAll) {
        this.items = new ArrayList<>(Markets.getTransactionManager().getManagerContent());
    } else {
        if (this.filterType == PlayerRole.SELLER) {
            this.items = new ArrayList<>(Markets.getTransactionManager()
                .getSalesTransactionsFor((this.player.getUniqueId())));  // BLOCKS HERE
        } else {
            this.items = new ArrayList<>(Markets.getTransactionManager()
                .getPurchaseTransactionsFor((this.player.getUniqueId())));  // BLOCKS HERE
        }
    }
    this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
}
```

## Solution: Async Filtering with Callback Pattern

### Phase 1: Add Async Methods to TransactionManager

**File:** `src/main/java/ca/tweetzy/markets/model/manager/TransactionManager.java`

```java
package ca.tweetzy.markets.model.manager;

import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.manager.ListManager;
import ca.tweetzy.markets.api.market.Transaction;
import ca.tweetzy.markets.api.market.core.MarketUser;
import lombok.NonNull;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class TransactionManager extends ListManager<Transaction> {

    public TransactionManager() {
        super("Transaction");
    }

    // ==================== ASYNC METHODS (NEW) ====================

    /**
     * Async: Get sales transactions (player is seller)
     * Filtering happens on async thread, result delivered via callback
     *
     * @param sellerUUID The seller's UUID
     * @param callback Consumer that receives the filtered list
     */
    public void getSalesTransactionsForAsync(@NonNull final UUID sellerUUID, @NonNull final Consumer<List<Transaction>> callback) {
        // Run filtering on async thread
        Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
            // Perform filtering off main thread
            final List<Transaction> transactions = getManagerContent().stream()
                .filter(transaction -> transaction.getSeller().equals(sellerUUID))
                .collect(Collectors.toList());

            // Deliver result back on main thread (required for GUI updates)
            Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(transactions));
        });
    }

    /**
     * Async: Get purchase transactions (player is buyer)
     * Filtering happens on async thread, result delivered via callback
     *
     * @param buyerUUID The buyer's UUID
     * @param callback Consumer that receives the filtered list
     */
    public void getPurchaseTransactionsForAsync(@NonNull final UUID buyerUUID, @NonNull final Consumer<List<Transaction>> callback) {
        // Run filtering on async thread
        Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
            // Perform filtering off main thread
            final List<Transaction> transactions = getManagerContent().stream()
                .filter(transaction -> transaction.getBuyer().equals(buyerUUID))
                .collect(Collectors.toList());

            // Deliver result back on main thread (required for GUI updates)
            Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(transactions));
        });
    }

    /**
     * Async: Get all transactions for player (buyer OR seller)
     * Filtering happens on async thread, result delivered via callback
     *
     * @param playerUUID The player's UUID
     * @param callback Consumer that receives the filtered list
     */
    public void getTransactionsForAsync(@NonNull final UUID playerUUID, @NonNull final Consumer<List<Transaction>> callback) {
        // Run filtering on async thread
        Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
            // Perform filtering off main thread
            final List<Transaction> transactions = getManagerContent().stream()
                .filter(transaction -> transaction.getSeller().equals(playerUUID) || transaction.getBuyer().equals(playerUUID))
                .collect(Collectors.toList());

            // Deliver result back on main thread (required for GUI updates)
            Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(transactions));
        });
    }

    /**
     * Async: Get offline transactions (since last seen)
     * Filtering happens on async thread, result delivered via callback
     *
     * @param sellerUUID The seller's UUID
     * @param callback Consumer that receives the filtered list
     */
    public void getOfflineTransactionsForAsync(@NonNull final UUID sellerUUID, @NonNull final Consumer<List<Transaction>> callback) {
        final MarketUser user = Markets.getPlayerManager().get(sellerUUID);
        if (user == null) {
            callback.accept(List.of());
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

    // ==================== SYNC METHODS (KEEP FOR BACKWARD COMPATIBILITY) ====================

    /**
     * @deprecated Use {@link #getSalesTransactionsForAsync} instead
     * Synchronous filtering blocks the calling thread
     */
    @Deprecated
    public List<Transaction> getSalesTransactionsFor(@NonNull final UUID sellerUUID) {
        return getManagerContent().stream()
            .filter(transaction -> transaction.getSeller().equals(sellerUUID))
            .collect(Collectors.toList());
    }

    /**
     * @deprecated Use {@link #getPurchaseTransactionsForAsync} instead
     * Synchronous filtering blocks the calling thread
     */
    @Deprecated
    public List<Transaction> getPurchaseTransactionsFor(@NonNull final UUID buyerUUID) {
        return getManagerContent().stream()
            .filter(transaction -> transaction.getBuyer().equals(buyerUUID))
            .collect(Collectors.toList());
    }

    /**
     * @deprecated Use {@link #getTransactionsForAsync} instead
     * Synchronous filtering blocks the calling thread
     */
    @Deprecated
    public List<Transaction> getTransactionsFor(@NonNull final UUID playerUUID) {
        return getManagerContent().stream()
            .filter(transaction -> transaction.getSeller().equals(playerUUID) || transaction.getBuyer().equals(playerUUID))
            .collect(Collectors.toList());
    }

    /**
     * @deprecated Use {@link #getOfflineTransactionsForAsync} instead
     * Synchronous filtering blocks the calling thread
     */
    @Deprecated
    public List<Transaction> getOfflineTransactionsFor(@NonNull final UUID sellerUUID) {
        final MarketUser user = Markets.getPlayerManager().get(sellerUUID);
        return getManagerContent().stream()
            .filter(transaction -> transaction.getSeller().equals(sellerUUID) && transaction.getTimeCreated() >= user.getLastSeenAt())
            .collect(Collectors.toList());
    }

    public int getTransactionsMadeToMarket(@NonNull final UUID sellerUUID, @NonNull final UUID buyerUUID) {
        return (int) this.managerContent.stream()
            .filter(transaction -> transaction.getBuyer().equals(buyerUUID) && transaction.getSeller().equals(sellerUUID))
            .count();
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

**Key Points:**
- `runTaskAsynchronously()` - Runs filtering on Bukkit's async thread pool
- `runTask()` - Returns result to main thread for GUI updates
- Callback pattern - Matches existing codebase style
- `@Deprecated` old methods - Maintains backward compatibility

### Phase 2: Update TransactionsGUI with Async Loading

**File:** `src/main/java/ca/tweetzy/markets/gui/user/TransactionsGUI.java`

```java
package ca.tweetzy.markets.gui.user;

import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.gui.helper.InventoryBorder;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.Transaction;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TransactionsGUI extends MarketsPagedGUI<Transaction> {

    private final Player player;
    private boolean viewAll;
    private PlayerRole filterType = PlayerRole.SELLER;
    private boolean isLoading = false;  // Track loading state

    private enum PlayerRole {
        BUYER, SELLER
    }

    public TransactionsGUI(Gui parent, @NonNull final Player player, boolean viewAll) {
        super(parent, player, TranslationManager.string(player, Translations.GUI_TRANSACTIONS_TITLE), 6, new ArrayList<>());
        this.player = player;
        this.viewAll = viewAll;
        setAcceptsItems(true);
        setDefaultItem(QuickItem.bg(Settings.GUI_TRANSACTIONS_BACKGROUND.getItemStack()));

        draw();
    }

    @Override
    protected void prePopulate() {
        // Start with empty list, will be populated async
        this.items = new ArrayList<>();
        this.isLoading = true;

        if (this.viewAll) {
            // For "view all", use synchronous (already in memory, no filtering needed)
            this.items = new ArrayList<>(Markets.getTransactionManager().getManagerContent());
            this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
            this.isLoading = false;
        } else {
            // Load filtered transactions asynchronously
            loadTransactionsAsync();
        }
    }

    /**
     * Load transactions asynchronously based on current filter type
     */
    private void loadTransactionsAsync() {
        this.isLoading = true;

        if (this.filterType == PlayerRole.SELLER) {
            // Load sales transactions async
            Markets.getTransactionManager().getSalesTransactionsForAsync(this.player.getUniqueId(), transactions -> {
                // This callback runs on main thread
                this.items = new ArrayList<>(transactions);
                this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
                this.isLoading = false;

                // Redraw GUI with loaded data
                draw();
            });
        } else {
            // Load purchase transactions async
            Markets.getTransactionManager().getPurchaseTransactionsForAsync(this.player.getUniqueId(), transactions -> {
                // This callback runs on main thread
                this.items = new ArrayList<>(transactions);
                this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
                this.isLoading = false;

                // Redraw GUI with loaded data
                draw();
            });
        }
    }

    @Override
    protected void drawFixed() {
        if (!Settings.USE_ADDITIONAL_CONFIRMS.getBoolean()) {
            setTransactionViewButton();
        } else {
            if (this.player.hasPermission("markets.viewalltransactions"))
                setTransactionViewButton();
        }

        setTransactionTypeToggle();

        // Show loading indicator if data is still loading
        if (this.isLoading) {
            showLoadingIndicator();
        }
    }

    /**
     * Display a loading indicator in the GUI center
     */
    private void showLoadingIndicator() {
        // Place loading indicator in center of GUI
        setButton(2, 4, QuickItem
                .of(Material.HOPPER)
                .name(TranslationManager.string(Translations.GUI_LOADING_INDICATOR_NAME))
                .lore(TranslationManager.list(Translations.GUI_LOADING_INDICATOR_LORE))
                .make());
    }

    private void setTransactionViewButton() {
        setButton(getRows() - 1, 8, QuickItem
                .of(Settings.GUI_TRANSACTIONS_VIEW_ALL_ITEM.getItemStack())
                .name(TranslationManager.string(Translations.GUI_TRANSACTIONS_ITEMS_VIEW_ALL_NAME))
                .lore(TranslationManager.list(Translations.GUI_TRANSACTIONS_ITEMS_VIEW_ALL_LORE,
                    "is_true", TranslationManager.string(this.viewAll ? Translations.TRUE : Translations.FALSE),
                    "left_click", TranslationManager.string(Translations.MOUSE_LEFT_CLICK)))
                .make(), click -> {

            this.viewAll = !this.viewAll;
            draw();  // This will call prePopulate() again
        });
    }

    private void setTransactionTypeToggle() {
        final String currentFilter = this.filterType == PlayerRole.SELLER ? "Sales" : "Purchases";

        setButton(5, 4, QuickItem
                .of(new ItemStack(Material.LEVER))
                .name(TranslationManager.string(Translations.GUI_TRANSACTIONS_ITEMS_TYPE_TOGGLE_NAME))
                .lore(TranslationManager.list(Translations.GUI_TRANSACTIONS_ITEMS_TYPE_TOGGLE_LORE,
                    "current_filter", currentFilter,
                    "left_click", TranslationManager.string(Translations.MOUSE_LEFT_CLICK)))
                .make(), click -> {

            // Cycle filter type
            if (this.filterType == PlayerRole.SELLER) {
                this.filterType = PlayerRole.BUYER;
            } else {
                this.filterType = PlayerRole.SELLER;
            }

            // Reload with new filter
            draw();  // This will call prePopulate() and loadTransactionsAsync()
        });
    }

    @Override
    protected ItemStack makeDisplayItem(Transaction transaction) {
        final ItemStack item = transaction.getItem();

        return QuickItem
                .of(item)
                .amount(Math.min(transaction.getQuantity(), item.getMaxStackSize()))
                .lore(TranslationManager.list(this.player, Translations.GUI_TRANSACTIONS_ITEMS_ENTRY_LORE,
                        "item_quantity", transaction.getQuantity(),
                        "market_item_price", transaction.getPrice(),
                        "market_item_currency", transaction.getCurrency(),
                        "buyer_name", transaction.getBuyerName(),
                        "seller_name", transaction.getSellerName(),
                        "transaction_date", transaction.getFormattedDate()
                )).make();
    }

    @Override
    protected void onClick(Transaction transaction, GuiClickEvent click) {

    }

    @Override
    protected List<Integer> fillSlots() {
        return InventoryBorder.getInsideBorders(5);
    }
}
```

**Key Changes:**
1. **`isLoading` flag** - Tracks async loading state
2. **`loadTransactionsAsync()` method** - Handles async data loading
3. **`showLoadingIndicator()` method** - Shows loading state in GUI
4. **Callback in `loadTransactionsAsync()`** - Calls `draw()` when data is ready
5. **Empty initial items** - Starts with empty list, populates async

### Phase 3: Add Translation Entries

**File:** `src/main/java/ca/tweetzy/markets/settings/Translations.java`

Add these translation keys:

```java
// Loading indicator for async operations
public static TranslationEntry GUI_LOADING_INDICATOR_NAME = create(
    "gui.loading.name",
    "&eLoading..."
);

public static TranslationEntry GUI_LOADING_INDICATOR_LORE = create(
    "gui.loading.lore",
    "&7Please wait while data is being loaded",
    "&7This should only take a moment"
);
```

### Phase 4: Update CommandTransactions (Admin Command)

**File:** `src/main/java/ca/tweetzy/markets/commands/CommandTransactions.java`

Update the admin usage section:

```java
// Admin usage: /markets transactions <player>
if (args.length >= 1) {
    // Check if player has admin permission
    if (!player.hasPermission("markets.admin.transactions")) {
        Common.tell(player, TranslationManager.string(player, Translations.NO_PERMISSION));
        return ReturnType.FAIL;
    }

    // Get target player
    final OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

    if (target == null || !target.hasPlayedBefore()) {
        Common.tell(player, TranslationManager.string(player, Translations.PLAYER_OFFLINE, "value", args[0]));
        return ReturnType.FAIL;
    }

    // Show loading message
    Common.tell(player, TranslationManager.string(Translations.LOADING_TRANSACTIONS, "player_name", args[0]));

    // Load transactions async
    Markets.getTransactionManager().getTransactionsForAsync(target.getUniqueId(), transactions -> {
        // Open GUI with loaded data (already on main thread from callback)
        Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
        if (onlineTarget == null) {
            // For offline players, show their transactions to admin
            Markets.getGuiManager().showGUI(player, new TransactionsGUI(null, player, false) {
                @Override
                protected void prePopulate() {
                    // Override with pre-loaded transactions
                    this.items = new ArrayList<>(transactions);
                    this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
                    this.isLoading = false; // Mark as loaded
                }
            });
        } else {
            // Show online player's transactions (will load async)
            Markets.getGuiManager().showGUI(player, new TransactionsGUI(null, onlineTarget, false));
        }
    });

    return ReturnType.SUCCESS;
}
```

Add translation:
```java
public static TranslationEntry LOADING_TRANSACTIONS = create(
    "command.transactions.loading",
    "&eLoading transactions for &b{player_name}&e..."
);
```

### Phase 5: Update PlayerJoinListener

**File:** `src/main/java/ca/tweetzy/markets/listeners/PlayerJoinListener.java`

Update the offline transactions notification:

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

**Change:** Replace lines 51-55 with the async version above.

## User Experience Flow

### Before (Synchronous)
1. Player clicks `/markets transactions`
2. **GUI freezes** while filtering 10,000 transactions
3. After 50-100ms delay, GUI appears with data
4. **Result:** Visible lag spike

### After (Async)
1. Player clicks `/markets transactions`
2. GUI opens **immediately** with loading indicator
3. Filtering happens on async thread (0ms main thread time)
4. After ~50-100ms, GUI refreshes with data
5. **Result:** No perceived lag, smooth experience

## Implementation Steps

### Step 1: Add Async Methods to TransactionManager
- Add 4 async methods with callback pattern
- Mark old methods as `@Deprecated`
- **Test:** Verify callbacks receive correct filtered data

### Step 2: Update TransactionsGUI
- Add `isLoading` flag
- Add `loadTransactionsAsync()` method
- Add loading indicator display
- **Test:** Open GUI, verify loading indicator appears then data loads

### Step 3: Add Translation Entries
- Add loading indicator translations
- Add command loading message translation
- **Test:** Verify translations display correctly

### Step 4: Update CommandTransactions
- Replace sync call with async version
- Add loading message
- **Test:** Admin command shows loading message then opens GUI

### Step 5: Update PlayerJoinListener
- Replace sync call with async version
- **Test:** Player join events don't lag, notification appears correctly

## Performance Comparison

### Main Thread Impact

**Synchronous (Current):**
```
[Main Thread] GUI Open → Filter 10,000 items (100ms) → Display GUI
Total blocking time: 100ms
```

**Async (New):**
```
[Main Thread] GUI Open → Display loading (1ms) → Done
[Async Thread] Filter 10,000 items (100ms)
[Main Thread] Redraw GUI with data (1ms)
Total blocking time: 2ms
```

**Improvement:** 50x reduction in main thread blocking time

### Memory Usage
- **No change** - Same data structures, just different execution thread
- Still uses defensive copy from `getManagerContent()`

## Edge Cases & Error Handling

### 1. GUI Closed Before Data Loads
**Problem:** Player closes GUI while filtering is in progress

**Solution:** Callback checks if GUI is still open
```java
Markets.getTransactionManager().getSalesTransactionsForAsync(this.player.getUniqueId(), transactions -> {
    // Check if player still has this GUI open
    if (!this.player.getOpenInventory().getTopInventory().equals(this.inventory)) {
        return; // GUI closed, don't update
    }

    this.items = new ArrayList<>(transactions);
    this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
    this.isLoading = false;
    draw();
});
```

### 2. Rapid Filter Changes
**Problem:** Player rapidly clicks filter toggle before data loads

**Solution:** Debounce or cancel previous loads
```java
private void setTransactionTypeToggle() {
    // ... existing code ...
    .make(), click -> {
        if (this.isLoading) {
            // Prevent filter changes while loading
            Common.tell(click.player, "&cPlease wait for current data to load");
            return;
        }

        // ... rest of existing code ...
    });
}
```

### 3. Empty Results
**Problem:** Filter returns no transactions

**Solution:** Show "no transactions" message
```java
private void loadTransactionsAsync() {
    this.isLoading = true;

    Markets.getTransactionManager().getSalesTransactionsForAsync(this.player.getUniqueId(), transactions -> {
        this.items = new ArrayList<>(transactions);
        this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
        this.isLoading = false;

        if (this.items.isEmpty()) {
            // Show "no transactions" indicator
            setButton(2, 4, QuickItem
                .of(Material.BARRIER)
                .name("&cNo Transactions Found")
                .lore("&7You have no " + (filterType == PlayerRole.SELLER ? "sales" : "purchases"))
                .make());
        } else {
            draw();
        }
    });
}
```

## Testing Checklist

### Unit Tests
- [ ] `getSalesTransactionsForAsync()` returns correct filtered list
- [ ] `getPurchaseTransactionsForAsync()` returns correct filtered list
- [ ] Callbacks execute on main thread
- [ ] Empty list handling

### Integration Tests
- [ ] GUI opens with loading indicator
- [ ] GUI updates when data loads
- [ ] Filter toggle works correctly
- [ ] View all toggle works correctly
- [ ] Player close GUI before load completes (no errors)
- [ ] Admin command loads data correctly

### Performance Tests
- [ ] Load 1,000 transactions - measure main thread time
- [ ] Load 10,000 transactions - measure main thread time
- [ ] Load 100,000 transactions - verify no freezing

### User Experience Tests
- [ ] Loading indicator is visible
- [ ] No perceived lag when opening GUI
- [ ] Smooth transition from loading to data display

## Rollback Plan

If issues occur:

1. **Immediate rollback:**
   - Change GUI to call deprecated sync methods
   - Comment out loading indicator logic
   - **Time:** 5 minutes

2. **Full revert:**
   - Remove async methods from TransactionManager
   - Revert GUI changes
   - Remove translations
   - **Time:** 15 minutes

3. **Keep code, disable feature:**
   - Add config option to toggle async/sync
   - Fall back to sync by default

## Migration Checklist

- [ ] Step 1: Add async methods to TransactionManager
- [ ] Step 2: Add translations for loading indicators
- [ ] Step 3: Update TransactionsGUI with async loading
- [ ] Step 4: Test GUI opening and filtering
- [ ] Step 5: Update CommandTransactions
- [ ] Step 6: Update PlayerJoinListener
- [ ] Step 7: Test all usages
- [ ] Step 8: Performance testing with large datasets
- [ ] Step 9: Deploy to test server
- [ ] Step 10: Monitor for errors/issues

## Conclusion

This plan converts the in-memory `.stream()` filtering to async execution with:

- **Minimal refactoring** - Only TransactionManager and calling code
- **Backward compatible** - Old methods still work
- **Better UX** - Loading indicators, no lag
- **Main thread relief** - Filtering moves to async thread
- **Simple pattern** - Bukkit scheduler + callback

**Estimated Time:**
- Step 1: 30 minutes (TransactionManager async methods)
- Step 2: 30 minutes (GUI loading state)
- Step 3: 15 minutes (Translations)
- Step 4: 15 minutes (Command update)
- Step 5: 15 minutes (Listener update)
- Testing: 1 hour

**Total: ~3 hours**

**Risk: Low** - Changes are isolated, backward compatible, easy to revert.
