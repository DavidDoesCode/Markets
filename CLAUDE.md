# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Markets is a Spigot/Bukkit plugin for Minecraft that provides a GUI-based player marketplace system. It replaces traditional chest-based shops with a fully GUI-driven experience where players can create markets, sell items, accept offers, and manage transactions.

## Build Commands

**Build the plugin:**
```bash
mvn clean package
```

**Build with dependencies shaded:**
The Maven shade plugin automatically packages Flight framework, Gson, and TaskChain dependencies into the final JAR. The built artifact is located at `target/Markets.jar`.

**Auto-deployment (configured):**
The pom.xml includes automatic deployment to a local test server at `D:\Development\Spigot Servers\Latest Version\plugins` when building.

## Core Architecture

### Manager Pattern
The plugin uses a centralized manager pattern for all major subsystems. All managers are instantiated in `Markets.java` and accessed statically via getter methods:

- `MarketManager` - Player and server markets
- `PlayerManager` - Player profiles (MarketUser objects)
- `CategoryManager` - Market categories
- `CategoryItemManager` - Items within categories
- `OfferManager` - Purchase offers from buyers
- `CurrencyManager` - Multi-currency support (Vault, Items, Funds, EcoBits, UltraEconomy)
- `BankManager` - Tax collection and storage
- `RatingManager` - Market ratings/reviews
- `RequestManager` - Player item requests
- `TransactionManager` - Transaction history
- `OfflineItemPaymentManager` - Offline payment collection

Access pattern: `Markets.getXManager()` (e.g., `Markets.getPlayerManager()`)

### Database Layer

**Flight Framework Integration:**
The plugin uses the Flight framework's database abstraction layer with migration support.

**Database Selection:**
- Configured via `Settings.DATABASE_USE` boolean
- MySQL: Used when enabled with connection details from Settings
- SQLite: Default fallback for local development/testing

**Migrations:**
Located in `database/migrations/`, numbered sequentially (`_1_InitialMigration.java` through `_17_BankEntryPriceMigration.java`). All migrations are registered in `Markets.onFlight()`.

**DataManager:**
Handles all CRUD operations. Database queries use Flight's DataManager methods with async callbacks.

### API Layer Structure

**Interfaces (in `api/` package):**
Core behavior contracts that define the plugin's domain model:

- `Storeable<T>` - Database persistence with async store/unStore callbacks
- `Synchronize` - Database synchronization with result callbacks
- `Identifiable` - UUID-based entity identification
- `Trackable` - Creation/update timestamps
- `Displayable` - GUI display information
- `Jsonable` - JSON serialization support
- `UserIdentifiable` - Links entities to player UUIDs
- `Navigable` - GUI navigation state

**Implementations (in `impl/` package):**
Concrete classes implementing the interfaces above.

**Key Domain Models:**
- `Market` interface with `PlayerMarket` and `ServerMarket` implementations
- `MarketUser` - Player profile with preferences and market data
- `Category` - Market category groupings
- `MarketItem` - Items for sale (CategoryItem)
- `Transaction` - Purchase/sale records with buyer/seller info
- `Offer` - Player offers on market items
- `Request` - Player item requests
- `Rating` - Market reviews
- `BankEntry` - Tax collection entries

### GUI Architecture

**Base Classes:**
- `MarketsBaseGUI` - Extends Flight's `BaseGUI`, provides standard navigation buttons (back, exit, prev/next at slots 48/50)
- `MarketsPagedGUI<T>` - Pagination support for lists of items

**GUI Organization:**
- `gui/admin/` - Admin interfaces
- `gui/user/` - Player-facing UIs (bank, offers, payments, transactions, market management)
- `gui/shared/` - Shared interfaces (main GUI, checkout, selectors, view GUIs)

**Flight Framework GUI Pattern:**
GUIs use the Flight framework's `QuickItem` builder for ItemStack creation and `TranslationManager` for localized text. All button positions are slot-based (0-53 for 6-row inventory).

### Translation System

**Translations.java:**
Central translation key registry. All user-facing text is defined as `TranslationEntry` fields using the `create()` method with dot-notation keys (e.g., `"gui.transactions.items.entry.lore"`).

**Pattern:**
```java
public static TranslationEntry KEY_NAME = create("path.to.key", "Default text", "Line 2", ...);
```

**Usage:**
```java
TranslationManager.string(player, Translations.KEY_NAME)
TranslationManager.list(player, Translations.KEY_NAME, "placeholder", value)
```

**Important:** When adding GUI features, always add corresponding translation entries in `Translations.java`.

### Settings System

**Settings.java:**
Configuration values loaded from config files. Uses Flight's Settings framework with typed getters (`.getString()`, `.getInt()`, `.getBoolean()`, `.getItemStack()`).

### Currency System

**Multi-Currency Support:**
The plugin supports multiple currency types through the `AbstractCurrency` interface:
- `VaultCurrency` - Vault economy integration
- `ItemCurrency` - Item-based trading
- `FundsCurrency` - Funds plugin integration
- `EcoBitsCurrency` - EcoBits plugin integration
- `UltraEconomyCurrency` - UltraEconomy plugin integration

**Currency Loaders:**
Each currency type has a loader (e.g., `VaultEconomyLoader`) that checks for plugin availability and registers the currency.

### Bedrock Support

The plugin includes FloodGate integration for Bedrock players. Use `FloodGateCheck` utility for cross-platform compatibility checks.

## Important Patterns & Conventions

### Async Operations
Database operations use callback patterns:
```java
object.store(result -> {
    // Handle stored object
});

object.sync(syncResult -> {
    // Handle sync result
});
```

### Manager Loading
Managers load their data in `Markets.onFlight()` after database migrations. Load order matters - dependencies must load first (e.g., PlayerManager before MarketManager).

### GUI Button Positioning
- Row indices: 0-5 (6 rows)
- Column indices: 0-8 (9 columns)
- Slot calculation: `row * 9 + column`
- Navigation: Previous (48), Next (50), Back/Exit via `MarketsBaseGUI` methods

### Player Name Caching
When working with OfflinePlayer objects, NEVER use `OfflinePlayer.getName()` directly as it can return null for unloaded profiles. Always:
1. Use cached names from domain objects (e.g., `MarketUser.getName()`, `Market.getOwnerName()`)
2. For async head loading, use `QuickItem.of(skull)` with async callbacks
3. Cache player names when creating/updating entities

### Transaction Types
```java
enum TransactionType {
    ITEM_PURCHASE,        // Regular purchase from market
    REQUEST_FULFILLMENT   // Fulfilling a player request (sales)
}
```

When displaying transactions, note that buyers/sellers switch context based on transaction type.

## Code Style

- Use Lombok annotations (`@NonNull`, `@Getter`, etc.)
- Prefer immutability where possible (final fields)
- Use method references and lambda expressions
- Follow builder patterns for complex objects (QuickItem)
- All entity IDs are UUIDs
- Timestamps are long milliseconds (epoch time)

## Testing

The plugin includes automatic deployment to a local test server during Maven build. Manual testing requires:
1. A running Spigot 1.21+ server
2. Flight framework dependencies (auto-shaded)
3. Optional: Vault, EcoBits, UltraEconomy for currency features

## Key Dependencies

- **Spigot 1.21.8** - Minecraft server API
- **Flight Framework 3.38.1** - Core framework (GUI, database, commands, settings)
- **Funds 1.10.0** - Currency support
- **Vault** - Economy integration
- **TaskChain** - Async task management
- **Lombok** - Boilerplate reduction
- **FloodGate** - Bedrock player support

All dependencies except Spigot are shaded into the final JAR.
