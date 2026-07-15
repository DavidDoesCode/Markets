package ca.tweetzy.markets.gui.user.category;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.gui.GuiManager;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.PlayerUtil;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.flight.utils.input.TitleInput;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.core.Category;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketItem;
import ca.tweetzy.markets.gui.MarketsBaseGUI;
import ca.tweetzy.markets.gui.shared.selector.CurrencyPickerGUI;
import ca.tweetzy.markets.impl.CategoryItem;
import ca.tweetzy.markets.model.BlacklistChecker;
import ca.tweetzy.markets.model.DupeDetector;
import ca.tweetzy.markets.model.WorthPriceLimiter;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.apache.commons.lang3.math.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CategoryNewItemGUI extends MarketsBaseGUI {

	private final Player player;
	private final Market market;
	private final Category category;
	private final MarketItem marketItem;

	private Boolean clickLock = false;
	private ItemStack pendingItem = null;
	private boolean suppressItemReturn = false;

	public CategoryNewItemGUI(@NonNull final Player player, @NonNull final Market market, @NonNull final Category category, final MarketItem marketItem) {
		super(new MarketCategoryEditGUI(player, market, category), player, TranslationManager.string(Translations.GUI_CATEGORY_ADD_ITEM_TITLE, "category_name", category.getName()), 6);
		this.player = player;
		this.market = market;
		this.category = category;
		if (marketItem == null) this.marketItem = new CategoryItem(this.category.getId());
		else this.marketItem = marketItem;

		if (Settings.ITEMS_ARE_WHOLESALE_BY_DEFAULT.getBoolean())
			this.marketItem.setPriceIsForAll(true);

		// pre setup
		setAcceptsItems(true);
		setUnlocked(1, 4);

		// Fixed: Use direct inventory access to prevent item loss on disconnect
		// See BankGUI.java:167-176 for similar pattern
		setOnClose(close -> {
			if (this.suppressItemReturn) return;

			final ItemStack itemToReturn = this.pendingItem != null ? this.pendingItem : getItem(1, 4);
			if (itemToReturn != null && itemToReturn.getType() != CompMaterial.AIR.get()) {
				try {
					// Direct inventory access works even during disconnect
					final java.util.HashMap<Integer, ItemStack> leftover = close.player.getInventory().addItem(itemToReturn);

					// Drop overflow at player location
					if (!leftover.isEmpty()) {
						for (ItemStack item : leftover.values()) {
							close.player.getWorld().dropItemNaturally(close.player.getLocation(), item);
						}
					}
				} catch (Exception e) {
					// Last resort: drop at location even if fully disconnected
					close.player.getWorld().dropItemNaturally(close.player.getLocation(), itemToReturn);
				}
			}
		});

		setDefaultItem(QuickItem.bg(Settings.GUI_CATEGORY_ADD_ITEM_BACKGROUND.getItemStack()));
		draw();
	}

	public CategoryNewItemGUI(@NonNull final Player player, @NonNull final Market market, @NonNull final Category category) {
		this(player, market, category, null);
	}

	private void syncPlacedItemFromSlot(@NonNull final String action) {
		final ItemStack placedItem = getItem(1, 4);
		if (placedItem != null && placedItem.getType() != CompMaterial.AIR.get()) {
			this.marketItem.setItem(placedItem.clone());
			return;
		}

		detectAndLogDraftDupeAttempt(action);
		this.marketItem.setItem(CompMaterial.AIR.parseItem());
	}

	private void detectAndLogDraftDupeAttempt(@NonNull final String action) {
		final ItemStack draftItem = this.marketItem.getItem();
		if (draftItem == null || draftItem.getType() == CompMaterial.AIR.get())
			return;

		final ItemStack slotItem = getItem(1, 4);
		if (slotItem != null && slotItem.getType() != CompMaterial.AIR.get())
			return;

		if (PlayerUtil.getItemCountInPlayerInventory(this.player, draftItem) <= 0)
			return;

		DupeDetector.logGuiDraftDupeAttempt(this.player, "CategoryNewItemGUI", action, draftItem, this.market);
	}

	private void reopen(@NonNull final Player player, @NonNull final GuiManager manager) {
		this.suppressItemReturn = true;
		syncPlacedItemFromSlot("GUI_REFRESH");
		manager.showGUI(player, new CategoryNewItemGUI(this.player, this.market, this.category, this.marketItem));
	}

	@Override
	protected void draw() {

		if (this.marketItem.getItem().getType() != CompMaterial.AIR.get()) setItem(1, 4, this.marketItem.getItem());

		if (this.marketItem.getCurrencyItem() != null && this.marketItem.isCurrencyOfItem()) {
			final ItemStack currencyItem = this.marketItem.getCurrencyItem().clone();

			setItem(3, 4, QuickItem
					.of(currencyItem)
					.lore(TranslationManager.list(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_CUSTOM_CURRENCY_LORE))
					.make()
			);
		}

		setButton(2, 4, QuickItem
				.of(Settings.GUI_CATEGORY_ADD_ITEM_ITEMS_PRICE_ITEM.getItemStack())
				.name(TranslationManager.string(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_PRICE_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_PRICE_LORE, "market_item_price", this.marketItem.getPrice()))
				.make(), click -> {

			syncPlacedItemFromSlot("GUI_REFRESH");
			this.suppressItemReturn = true;

			click.gui.exit();

			new TitleInput(Markets.getInstance(), click.player, TranslationManager.string(click.player, Translations.PROMPT_ITEM_PRICE_TITLE), TranslationManager.string(click.player, Translations.PROMPT_ITEM_PRICE_SUBTITLE)) {

				@Override
				public void onExit(Player player) {
					CategoryNewItemGUI.this.suppressItemReturn = true;
					click.manager.showGUI(click.player, CategoryNewItemGUI.this);
				}

				@Override
				public boolean onResult(String string) {
					string = ChatColor.stripColor(string);

					if (!NumberUtils.isNumber(string)) {
						Common.tell(click.player, TranslationManager.string(click.player, Translations.NOT_A_NUMBER, "value", string));
						return false;
					}

					final double price = Double.parseDouble(string);
					if (price <= 0) {
						Common.tell(click.player, TranslationManager.string(click.player, Translations.MUST_BE_HIGHER_THAN_ZERO, "value", string));
						return false;
					}

					final int quantity = Math.max(1, CategoryNewItemGUI.this.marketItem.getItem().getAmount());
					if (!WorthPriceLimiter.validate(click.player, CategoryNewItemGUI.this.marketItem.getItem(), CategoryNewItemGUI.this.marketItem.getCurrency(), price, CategoryNewItemGUI.this.marketItem.isPriceForAll(), quantity))
						return false;

					CategoryNewItemGUI.this.marketItem.setPrice(price);

					CategoryNewItemGUI.this.reopen(click.player, click.manager);
					return true;
				}
			};
		});

		drawPriceForAllButton();

		// currency
		if (Settings.CURRENCY_ALLOW_PICK.getBoolean() || Settings.CURRENCY_USE_ITEM_ONLY.getBoolean())
			setButton(getRows() - 1, 8, QuickItem
					.of(Settings.GUI_CATEGORY_ADD_ITEM_ITEMS_CURRENCY_ITEM.getItemStack())
					.name(Translations.string(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_CURRENCY_NAME))
					.lore(Translations.list(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_CURRENCY_LORE,
							"left_click", Translations.string(this.player, Translations.MOUSE_LEFT_CLICK),
							"market_item_currency", this.marketItem.getCurrencyDisplayName()))
					.make(), click -> {

				syncPlacedItemFromSlot("GUI_REFRESH");
				this.suppressItemReturn = true;

				click.manager.showGUI(click.player, new CurrencyPickerGUI(this, click.player, (currency, item) -> {
					final String previousCurrency = CategoryNewItemGUI.this.marketItem.getCurrency();
					final ItemStack previousCurrencyItem = CategoryNewItemGUI.this.marketItem.getCurrencyItem() != null
							? CategoryNewItemGUI.this.marketItem.getCurrencyItem().clone()
							: null;

					CategoryNewItemGUI.this.marketItem.setCurrency(currency.getStoreableName());

					if (item != null)
						CategoryNewItemGUI.this.marketItem.setCurrencyItem(item);

					if (!WorthPriceLimiter.validateCurrentPrice(click.player, CategoryNewItemGUI.this.marketItem)) {
						CategoryNewItemGUI.this.marketItem.setCurrency(previousCurrency);
						if (previousCurrencyItem != null)
							CategoryNewItemGUI.this.marketItem.setCurrencyItem(previousCurrencyItem);
					}

					CategoryNewItemGUI.this.reopen(click.player, click.manager);
				}));
			});

		// server market can't have offers
		if (this.market.isServerMarket()) {
			drawInfiniteButton();
		} else {
			drawOffersButton();
		}


		// new item button
		setButton(getRows() - 1, 4, QuickItem.of(Settings.GUI_CATEGORY_ADD_ITEM_ITEMS_NEW_ITEM_ITEM.getItemStack()).name(TranslationManager.string(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_NEW_ITEM_NAME)).lore(TranslationManager.list(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_NEW_ITEM_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK))).make(), click -> {
			if (clickLock) {
				final ItemStack slotItem = getItem(1, 4);
				DupeDetector.logPreventedAttempt(
						"ADD_ITEM_DOUBLE_CLICK",
						click.player,
						null,
						slotItem != null ? slotItem : this.marketItem.getItem(),
						slotItem != null ? slotItem.getAmount() : 1,
						this.market,
						null,
						null
				);
				return;
			} else
				clickLock = true;

			final ItemStack placedItem = getItem(1, 4);
			if (placedItem == null) {
				Common.tell(click.player, TranslationManager.string(click.player, Translations.PLACE_ITEM_TO_ADD));
				clickLock = false;
				return;
			}

			// check blacklist
			if (!BlacklistChecker.passesChecks(click.player, placedItem)) {
				clickLock = false;
				return;
			}

			final int stockAmount = Markets.getPlayerManager().getAddableStock(click.player, 0, placedItem.getAmount());
			if (stockAmount <= 0) {
				Common.tell(click.player, TranslationManager.string(click.player, Translations.AT_MAX_STOCK_PER_LISTING,
						"max_stock", Markets.getPlayerManager().getMaxStockPerListing(click.player)));
				clickLock = false;
				return;
			}

			final ItemStack listingItem = placedItem.clone();
			listingItem.setAmount(stockAmount);

			if (stockAmount < placedItem.getAmount()) {
				final ItemStack overflow = placedItem.clone();
				overflow.setAmount(placedItem.getAmount() - stockAmount);
				PlayerUtil.giveItem(click.player, overflow);
			}

			this.marketItem.setItem(listingItem);
			this.marketItem.setStock(stockAmount);
			if (this.marketItem.getPrice() <= 0) {
				clickLock = false;
				return;
			}

			if (!WorthPriceLimiter.validate(click.player, listingItem, this.marketItem.getCurrency(), this.marketItem.getPrice(), this.marketItem.isPriceForAll(), stockAmount)) {
				clickLock = false;
				return;
			}

			if (this.market.isServerMarket()) {
				this.marketItem.setIsAcceptingOffers(false);
			}

			// Store item before clearing slot - allows recovery if player disconnects
			this.pendingItem = listingItem.clone();
			setItem(1, 4, CompMaterial.AIR.parseItem());

			// create the item
			Bukkit.getScheduler().runTaskLaterAsynchronously(Markets.getInstance(), () -> {
				if (!click.gui.isOpen()) {
					DupeDetector.logPreventedAttempt(
							"ADD_ITEM_GUI_CLOSED_EARLY",
							click.player,
							null,
							this.marketItem.getItem(),
							this.marketItem.getStock(),
							this.market,
							null,
							"gui_closed_before_async_create"
					);
					return;
				}

				Markets.getCategoryItemManager().create(this.category, this.marketItem.getItem(), this.marketItem.getCurrency(), this.marketItem.getCurrencyItem(), this.marketItem.getPrice(), this.marketItem.isPriceForAll(), this.marketItem.isAcceptingOffers(), this.marketItem.isInfinite(), created -> {
					if (created) {
						// Clear pending item on success - prevents duplicate returns
						this.pendingItem = null;
						click.manager.showGUI(click.player, new MarketCategoryEditGUI(this.player, this.market, this.category));
					}
					clickLock = false;
				});
			}, Settings.INTERNAL_ADD_ITEM_DELAY.getInt());

		});


		applyBackExit();
		// override logic here
		setAction(getRows() - 1, 0, click -> {
			final ItemStack placedItem = getItem(1, 4);

			if (placedItem != null) {
				Common.tell(click.player, TranslationManager.string(click.player, Translations.TAKE_OUT_ITEM_FIRST));
				return;
			}

			click.manager.showGUI(click.player, new MarketCategoryEditGUI(this.player, this.market, this.category));
		});
	}

	private void drawOffersButton() {
		if (!Settings.DISABLE_OFFERS.getBoolean()) {
			setButton(getRows() - 1, 2, QuickItem
					.of(Settings.GUI_CATEGORY_ADD_ITEM_ITEMS_OFFERS_ITEM.getItemStack())
					.name(TranslationManager.string(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_OFFERS_NAME))
					.lore(TranslationManager.list(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_OFFERS_LORE,
							"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK),
							"enabled", TranslationManager.string(this.player, this.marketItem.isAcceptingOffers() ? Translations.ENABLED : Translations.DISABLED)))
					.hideTags(true)
					.make(), click -> {

				syncPlacedItemFromSlot("OFFERS_TOGGLE");
				this.marketItem.setIsAcceptingOffers(!this.marketItem.isAcceptingOffers());
				drawOffersButton();
			});
		}
	}

	private void drawPriceForAllButton() {
		if (!Settings.DISABLE_WHOLESALE.getBoolean()) {
			setButton(getRows() - 1, 6, QuickItem
					.of(Settings.GUI_CATEGORY_ADD_ITEM_ITEMS_PRICE_FOR_ALL_ITEM.getItemStack())
					.name(TranslationManager.string(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_PRICE_FOR_ALL_NAME))
					.lore(TranslationManager.list(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_PRICE_FOR_ALL_LORE,
							"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK),
							"enabled", TranslationManager.string(this.player, this.marketItem.isPriceForAll() ? Translations.ENABLED : Translations.DISABLED)))
					.hideTags(true)
					.make(), click -> {

				syncPlacedItemFromSlot("WHOLESALE_TOGGLE");
				this.marketItem.setPriceIsForAll(!this.marketItem.isPriceForAll());
				drawPriceForAllButton();
			});
		}
	}

	private void drawInfiniteButton() {
		setButton(getRows() - 1, 2, QuickItem
				.of(Settings.GUI_CATEGORY_ADD_ITEM_ITEMS_INFINITE_ITEM.getItemStack())
				.name(TranslationManager.string(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_INFINITE_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_CATEGORY_ADD_ITEM_ITEMS_INFINITE_LORE,
						"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK),
						"enabled", TranslationManager.string(this.player, this.marketItem.isInfinite() ? Translations.ENABLED : Translations.DISABLED)))
				.hideTags(true)
				.make(), click -> {

			syncPlacedItemFromSlot("INFINITE_TOGGLE");
			this.marketItem.setInfinite(!this.marketItem.isInfinite());
			drawInfiniteButton();
		});
	}
}
