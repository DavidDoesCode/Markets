package ca.tweetzy.markets.gui.user.category;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.MathUtil;
import ca.tweetzy.flight.utils.PlayerUtil;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.flight.utils.input.TitleInput;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.SynchronizeResult;
import ca.tweetzy.markets.api.market.core.Category;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketItem;
import ca.tweetzy.markets.gui.MarketsBaseGUI;
import ca.tweetzy.markets.gui.shared.selector.CurrencyPickerGUI;
import ca.tweetzy.markets.model.FloodGateCheck;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.apache.commons.lang3.math.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public final class MarketItemEditGUI extends MarketsBaseGUI {

	private final Market market;
	private final Category category;
	private final MarketItem marketItem;
	private Boolean playerLock = false;
	private Boolean inputLock = false;

	public MarketItemEditGUI(@NonNull final Player player, @NonNull final Market market, @NonNull final Category category, @NonNull final MarketItem marketItem) {
		super(new MarketCategoryEditGUI(player, market, category), player, TranslationManager.string(player, Translations.GUI_EDIT_ITEM_TITLE), 6);
		this.market = market;
		this.category = category;
		this.marketItem = marketItem;

		setAcceptsItems(true);
		setDefaultItem(QuickItem.bg(Settings.GUI_EDIT_ITEM_BACKGROUND.getItemStack()));
		draw();
	}

	@Override
	protected void draw() {
		setItem(1, 4, this.marketItem.getItem());

		drawWholesaleButton();
		drawOffersButton();
		drawStockButton();
		drawPriceButton();
		drawCurrencyButton();

		applyBackExit();
	}

	private synchronized  void drawPriceButton() {
		setButton(3, 6, QuickItem
				.of(Settings.GUI_CATEGORY_ADD_ITEM_ITEMS_PRICE_ITEM.getItemStack())
				.name(Translations.string(this.player, Translations.GUI_EDIT_ITEM_ITEMS_PRICE_NAME))
				.lore(Translations.list(this.player, Translations.GUI_EDIT_ITEM_ITEMS_PRICE_LORE,
						"left_click", Translations.string(this.player, Translations.MOUSE_LEFT_CLICK),
						"market_item_price", this.marketItem.getPrice()))
				.make(), click -> {

			new TitleInput(Markets.getInstance(), click.player, TranslationManager.string(click.player, Translations.PROMPT_ITEM_PRICE_TITLE), TranslationManager.string(click.player, Translations.PROMPT_ITEM_PRICE_SUBTITLE)) {
				@Override
				public void onExit(Player player) {
					click.manager.showGUI(click.player, MarketItemEditGUI.this);
				}

				@Override
				public boolean onResult(String string) {
					string = ChatColor.stripColor(string);

					if (!NumberUtils.isNumber(string)) {
						Common.tell(click.player, TranslationManager.string(click.player, Translations.NOT_A_NUMBER, "value", string));
						return false;
					}

					final double price = Double.parseDouble(string);
					marketItem.setPrice(price);
					marketItem.sync(result -> reopen(click));
					return true;
				}
			};

		});
	}

	private void reopen(@NonNull GuiClickEvent click) {
		click.manager.showGUI(click.player, new MarketItemEditGUI(click.player, MarketItemEditGUI.this.market, MarketItemEditGUI.this.category, MarketItemEditGUI.this.marketItem));
	}

	private synchronized void drawStockButton() {
		setButton(3, 7, QuickItem.of(Settings.GUI_EDIT_ITEM_ITEMS_STOCK_ITEM.getItemStack())
				.name(TranslationManager.string(this.player, Translations.GUI_EDIT_ITEM_ITEMS_STOCK_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_EDIT_ITEM_ITEMS_STOCK_LORE,
						"market_item_stock", this.marketItem.getStock(),
						"right_click", TranslationManager.string(this.player, Translations.MOUSE_RIGHT_CLICK),
						"shift_left_click", TranslationManager.string(this.player, Translations.MOUSE_SHIFT_LEFT_CLICK),
						"drop_button", TranslationManager.string(this.player, Translations.DROP_KEY)
				))
				.make(), click -> {

			if (click.clickType == ClickType.LEFT || FloodGateCheck.isBedrock(this.player)) {
				synchronized (this) {
					if (playerLock) {
						Bukkit.getLogger().severe(click.player.getName() + " attempting to deposit twice.");
						return;
					} else
						playerLock = true;
				}

				final ItemStack cursor = click.cursor;
				if (cursor != null && cursor.getType() != CompMaterial.AIR.get()) {
					if (!this.marketItem.getItem().isSimilar(cursor)) return;

					this.marketItem.addStock(cursor, result -> {
						if (result == SynchronizeResult.FAILURE) {
							playerLock = false;
							return;
						}

						click.player.setItemOnCursor(CompMaterial.AIR.parseItem());
						drawStockButton();
						playerLock = false;
					});
				} else
					playerLock = false;
			}

			if (click.clickType == ClickType.SHIFT_LEFT) {
				synchronized (this) {
					if (playerLock) {
						Bukkit.getLogger().severe(click.player.getName() + " attempting to bulk deposit twice.");
						return;
					} else
						playerLock = true;
				}

				int itemCount = PlayerUtil.getItemCountInPlayerInventory(click.player, this.marketItem.getItem());
				if (itemCount == 0) {
					playerLock = false;
					return;
				}

				this.marketItem.setStock(this.marketItem.getStock() + itemCount);
				PlayerUtil.removeSpecificItemQuantityFromPlayer(click.player, this.marketItem.getItem(), itemCount);

				this.marketItem.sync(result -> {
					if (result == SynchronizeResult.FAILURE) return;
					drawStockButton();
					playerLock = false;
				});
			}

			if (click.clickType == ClickType.RIGHT || click.clickType == ClickType.DROP) {
				synchronized (this) {
					if (playerLock) {
						Bukkit.getLogger().severe(click.player.getName() + " attempting to withdraw twice.");
						return;
					} else
						playerLock = true;
				}

				new TitleInput(Markets.getInstance(), click.player, TranslationManager.string(click.player, Translations.PROMPT_STOCK_WITHDRAW_TITLE), TranslationManager.string(click.player, Translations.PROMPT_STOCK_WITHDRAW_SUBTITLE)) {
					@Override
					public void onExit(Player player) {
						click.manager.showGUI(click.player, MarketItemEditGUI.this);
					}

					@Override
					public boolean onResult(String string) {
						synchronized (this) {
							if (inputLock) {
								Bukkit.getLogger().severe(click.player.getName() + " attempting to submit qty twice.");
								return false;
							} else
								inputLock = true;
						}

						string = ChatColor.stripColor(string);

						if (!MathUtil.isInt(string)) {
							Common.tell(click.player, TranslationManager.string(click.player, Translations.NOT_A_NUMBER, "value", string));
							playerLock = false;
							return false;
						}

						int qty = Integer.parseInt(string);

						if (qty > 640) {
							Common.tell(click.player, "You may only withdraw 10 stacks at a time.");
							return false;
						}

						if (marketItem.getStock() < qty) {
							Common.tell(click.player, TranslationManager.string(click.player, Translations.NOT_ENOUGH_STOCK));
							playerLock = false;
							return false;
						}

						marketItem.setStock(marketItem.getStock() - qty);

						final ItemStack item = marketItem.getItem().clone();
						item.setAmount(1);

						Bukkit.getServer().getScheduler().runTask(Markets.getInstance(), () -> {
							for (int i = 0; i < qty; i++)
								PlayerUtil.giveItem(click.player, item);
						});

						marketItem.sync(result -> {
							inputLock = false;
							playerLock = false;
							click.manager.showGUI(click.player, new MarketItemEditGUI(click.player, MarketItemEditGUI.this.market, MarketItemEditGUI.this.category, MarketItemEditGUI.this.marketItem));
						});
						return true;
					}
				};
			}
		});
	}

	private void drawOffersButton() {
		if (!Settings.DISABLE_OFFERS.getBoolean()) {
			setButton(3, 1, QuickItem
					.of(this.marketItem.isAcceptingOffers() ? Settings.GUI_EDIT_ITEM_ITEMS_ACCEPTING_OFFERS_ITEM.getItemStack() : Settings.GUI_EDIT_ITEM_ITEMS_REJECTING_OFFERS_ITEM.getItemStack())
					.name(TranslationManager.string(this.player, Translations.GUI_EDIT_ITEM_ITEMS_OFFERS_NAME))
					.lore(TranslationManager.list(this.player, Translations.GUI_EDIT_ITEM_ITEMS_OFFERS_LORE,
							"enabled", TranslationManager.string(this.player, this.marketItem.isAcceptingOffers() ? Translations.ENABLED : Translations.DISABLED),
							"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)
					))
					.make(), click -> {

				this.marketItem.setIsAcceptingOffers(!this.marketItem.isAcceptingOffers());
				this.marketItem.sync(result -> {
					if (result == SynchronizeResult.SUCCESS)
						drawOffersButton();
				});
			});
		}
	}

	private void drawWholesaleButton() {
		if (!Settings.DISABLE_WHOLESALE.getBoolean()) {
			setButton(3, 3, QuickItem
					.of(this.marketItem.isPriceForAll() ? Settings.GUI_EDIT_ITEM_ITEMS_IS_WHOLESALE_ITEM.getItemStack() : Settings.GUI_EDIT_ITEM_ITEMS_NOT_WHOLESALE_ITEM.getItemStack())
					.name(TranslationManager.string(this.player, Translations.GUI_EDIT_ITEM_ITEMS_WHOLESALE_NAME))
					.lore(TranslationManager.list(this.player, Translations.GUI_EDIT_ITEM_ITEMS_WHOLESALE_LORE,
							"enabled", TranslationManager.string(this.player, this.marketItem.isPriceForAll() ? Translations.ENABLED : Translations.DISABLED),
							"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)
					))
					.make(), click -> {

				this.marketItem.setPriceIsForAll(!this.marketItem.isPriceForAll());
				this.marketItem.sync(result -> {
					if (result == SynchronizeResult.SUCCESS)
						drawWholesaleButton();
				});
			});
		}
	}

	private void drawCurrencyButton() {
		if (Settings.CURRENCY_ALLOW_PICK.getBoolean() || Settings.CURRENCY_USE_ITEM_ONLY.getBoolean())
			setButton(3, 5, QuickItem
					.of(Settings.GUI_EDIT_ITEM_ITEMS_CURRENCY_ITEM.getItemStack())
					.name(Translations.string(this.player, Translations.GUI_EDIT_ITEM_ITEMS_CURRENCY_NAME))
					.lore(Translations.list(this.player, Translations.GUI_EDIT_ITEM_ITEMS_CURRENCY_LORE,
							"left_click", Translations.string(this.player, Translations.MOUSE_LEFT_CLICK),
							"market_item_currency", this.marketItem.getCurrencyDisplayName()))
					.make(), click -> click.manager.showGUI(click.player, new CurrencyPickerGUI(this, click.player, (currency, item) -> {

				this.marketItem.setCurrency(currency.getStoreableName());

				if (item != null)
					this.marketItem.setCurrencyItem(item);

				this.marketItem.sync(result -> click.manager.showGUI(click.player, new MarketItemEditGUI(MarketItemEditGUI.this.player, MarketItemEditGUI.this.market, MarketItemEditGUI.this.category, MarketItemEditGUI.this.marketItem)));
			})));
	}
}
