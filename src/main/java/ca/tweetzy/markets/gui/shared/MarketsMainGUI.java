package ca.tweetzy.markets.gui.shared;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.gui.MarketsBaseGUI;
import ca.tweetzy.markets.gui.shared.selector.ConfirmGUI;
import ca.tweetzy.markets.gui.shared.view.AllMarketsViewGUI;
import ca.tweetzy.markets.gui.shared.view.AllReviewsGUI;
import ca.tweetzy.markets.gui.shared.view.requests.RequestsGUI;
import ca.tweetzy.markets.gui.user.BankGUI;
import ca.tweetzy.markets.gui.user.OffersGUI;
import ca.tweetzy.markets.gui.user.OfflinePaymentsGUI;
import ca.tweetzy.markets.gui.user.TransactionsGUI;
import ca.tweetzy.markets.gui.user.market.MarketOverviewGUI;
import ca.tweetzy.markets.gui.user.market.MarketStatsGUI;
import ca.tweetzy.markets.model.MarketLockHelper;
import ca.tweetzy.markets.model.shipping.ShippingBreakdown;
import ca.tweetzy.markets.model.shipping.ShippingCalculator;
import ca.tweetzy.markets.model.shipping.ShippingMoney;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class MarketsMainGUI extends MarketsBaseGUI {

	private final Player player;

	public MarketsMainGUI(@NonNull final Player player) {
		super(null, player, TranslationManager.string(player, Translations.GUI_MAIN_VIEW_TITLE), Settings.GUI_MAIN_VIEW_ROWS.getInt());
		this.player = player;
		setDefaultItem(QuickItem.bg(Settings.GUI_MAIN_VIEW_BACKGROUND.getItemStack()));
		draw();
	}

	@Override
	protected void draw() {
		final Market playerMarket = Markets.getMarketManager().getByOwner(this.player.getUniqueId());
		final int openShops = Markets.getMarketManager().getOpenMarketsInclusive().size();

		// shipping estimate
		final ShippingBreakdown shippingBreakdown = ShippingCalculator.calculate(this.player);
		final List<String> shippingLore = shippingBreakdown.getNoChargeReason() == ShippingBreakdown.NoChargeReason.DISABLED
				? TranslationManager.list(this.player, Translations.GUI_MAIN_VIEW_ITEMS_SHIPPING_LORE_FREE,
				"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK))
				: TranslationManager.list(this.player, Translations.GUI_MAIN_VIEW_ITEMS_SHIPPING_LORE,
				"world_name", shippingBreakdown.getWorldName(),
				"shipping_total", ShippingMoney.format(shippingBreakdown.getTotal()),
				"distance", String.format("%,.0f", shippingBreakdown.getDistance()),
				"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK));
		setButton(Settings.GUI_MAIN_VIEW_ITEMS_SHIPPING_SLOT.getInt(),
				QuickItem
						.of(Settings.GUI_MAIN_VIEW_ITEMS_SHIPPING.getItemStack())
						.hideTags(true)
						.name(TranslationManager.string(this.player, Translations.GUI_MAIN_VIEW_ITEMS_SHIPPING_NAME))
						.lore(shippingLore)
						.make(), click -> {
					click.gui.exit();
					final List<String> mainAliases = Settings.CMD_ALIAS_MAIN.getStringList();
					final List<String> shippingAliases = Settings.CMD_ALIAS_SUB_SHIPPING.getStringList();
					final String mainAlias = mainAliases.isEmpty() ? "market" : mainAliases.get(0);
					final String shippingAlias = shippingAliases.isEmpty() ? "shipping" : shippingAliases.get(0);
					click.player.performCommand(mainAlias + " " + shippingAlias);
				});

		// global markets
		setButton(Settings.GUI_MAIN_VIEW_ITEMS_ALL_MARKETS_SLOT.getInt(),
				QuickItem
						.of(Settings.GUI_MAIN_VIEW_ITEMS_ALL_MARKETS.getItemStack())
						.hideTags(true)
						.name(TranslationManager.string(this.player, Translations.GUI_MAIN_VIEW_ITEMS_GLOBAL_NAME, "open_shops", openShops))
						.lore(TranslationManager.list(this.player, Translations.GUI_MAIN_VIEW_ITEMS_GLOBAL_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
						.make(), click -> click.manager.showGUI(click.player, new AllMarketsViewGUI(new MarketsMainGUI(click.player), click.player)));

		// all reviews
		setButton(Settings.DISABLE_REVIEWS.getBoolean() ? -1 : Settings.GUI_MAIN_VIEW_ITEMS_ALL_REVIEWS_SLOT.getInt(),
				QuickItem
						.of(Settings.GUI_MAIN_VIEW_ITEMS_ALL_REVIEWS.getItemStack())
						.hideTags(true)
						.name(TranslationManager.string(this.player, Translations.GUI_MAIN_VIEW_ITEMS_ALL_REVIEWS_NAME))
						.lore(TranslationManager.list(this.player, Translations.GUI_MAIN_VIEW_ITEMS_ALL_REVIEWS_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
						.make(), click -> click.manager.showGUI(click.player, new AllReviewsGUI(new MarketsMainGUI(click.player), click.player)));

		// your market
		final int yourMarketSlot = Settings.GUI_MAIN_VIEW_ITEMS_YOUR_MARKET_SLOT.getInt();

		// Show placeholder first
		setButton(yourMarketSlot,
				QuickItem
						.of(CompMaterial.PLAYER_HEAD)
						.hideTags(true)
						.name(TranslationManager.string(player, Translations.GUI_MAIN_VIEW_ITEMS_YOUR_MARKET_NAME))
						.lore(playerMarket == null ? TranslationManager.list(player, Translations.GUI_MAIN_VIEW_ITEMS_YOUR_MARKET_LORE_CREATE, "market_cost", String.format("%,.2f", Settings.CREATION_COST_COST.getDouble())) : TranslationManager.list(player, Translations.GUI_MAIN_VIEW_ITEMS_YOUR_MARKET_LORE_VIEW))
						.make(), click -> {

					if (playerMarket == null) {
						if (!Settings.ALLOW_ANYONE_TO_CREATE_MARKET.getBoolean() && !click.player.hasPermission("markets.createmarket")) {
							Common.tell(click.player, TranslationManager.string(click.player, Translations.NOT_ALLOWED_TO_CREATE));
							return;
						}

						if (Settings.USE_ADDITIONAL_CONFIRMS.getBoolean()) {
							click.manager.showGUI(click.player, new ConfirmGUI(this, click.player, confirmed -> {
								if (confirmed)
									Markets.getMarketManager().create(this.player, created -> {
										if (created)
											click.manager.showGUI(click.player, new MarketsMainGUI(click.player));
									});
								else
									click.manager.showGUI(click.player, new MarketsMainGUI(click.player));
							}));

						} else {
							Markets.getMarketManager().create(this.player, created -> {
								if (created)
									click.manager.showGUI(click.player, new MarketsMainGUI(click.player));
							});
						}

						return;
					}

					// open market
					if (MarketLockHelper.shouldBlockManagementGui(click.player, playerMarket))
						return;

					click.manager.showGUI(click.player, new MarketOverviewGUI(click.player, playerMarket));
				});

		// Load actual player head asynchronously
		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			QuickItem.asyncPlayerHead(this.player).thenAccept(skull -> {
				ItemStack finalItem = QuickItem.of(skull)
						.hideTags(true)
						.name(TranslationManager.string(player, Translations.GUI_MAIN_VIEW_ITEMS_YOUR_MARKET_NAME))
						.lore(playerMarket == null ? TranslationManager.list(player, Translations.GUI_MAIN_VIEW_ITEMS_YOUR_MARKET_LORE_CREATE, "market_cost", String.format("%,.2f", Settings.CREATION_COST_COST.getDouble())) : TranslationManager.list(player, Translations.GUI_MAIN_VIEW_ITEMS_YOUR_MARKET_LORE_VIEW))
						.make();

				Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
					setItem(yourMarketSlot, finalItem);
				});
			});
		});

		// requests
		setButton(Settings.ALLOW_REQUESTS.getBoolean() ? Settings.GUI_MAIN_VIEW_ITEMS_REQUESTS_SLOT.getInt() : -1,
				QuickItem
						.of(Settings.GUI_MAIN_VIEW_ITEMS_REQUESTS.getItemStack())
						.hideTags(true)
						.name(TranslationManager.string(this.player, Translations.GUI_MAIN_VIEW_ITEMS_REQUESTS_NAME))
						.lore(TranslationManager.list(this.player, Translations.GUI_MAIN_VIEW_ITEMS_REQUESTS_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
						.make(), click -> click.manager.showGUI(click.player, new RequestsGUI(new MarketsMainGUI(click.player), click.player, Settings.REQUEST_MENU_SHOWS_OWN_FIRST.getBoolean())));

		// payments
		setButton(Settings.GUI_MAIN_VIEW_ITEMS_PAYMENTS_SLOT.getInt(),
				QuickItem
						.of(Settings.GUI_MAIN_VIEW_ITEMS_PAYMENTS.getItemStack())
						.hideTags(true).name(TranslationManager.string(this.player, Translations.GUI_MAIN_VIEW_ITEMS_PAYMENTS_NAME))
						.lore(TranslationManager.list(this.player, Translations.GUI_MAIN_VIEW_ITEMS_PAYMENTS_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
						.make(), click -> click.manager.showGUI(click.player, new OfflinePaymentsGUI(new MarketsMainGUI(click.player), click.player)));

		// transactions
		setButton(Settings.GUI_MAIN_VIEW_ITEMS_TRANSACTIONS_SLOT.getInt(),
				QuickItem
						.of(Settings.GUI_MAIN_VIEW_ITEMS_TRANSACTIONS.getItemStack())
						.hideTags(true).name(TranslationManager.string(this.player, Translations.GUI_MAIN_VIEW_ITEMS_TRANSACTIONS_NAME))
						.lore(TranslationManager.list(this.player, Translations.GUI_MAIN_VIEW_ITEMS_TRANSACTIONS_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
						.make(), click -> click.manager.showGUI(click.player, new TransactionsGUI(new MarketsMainGUI(click.player), click.player, false)));

		// stats
		setButton(Settings.GUI_MAIN_VIEW_ITEMS_STATS_SLOT.getInt(),
				QuickItem
						.of(Settings.GUI_MAIN_VIEW_ITEMS_STATS.getItemStack())
						.hideTags(true).name(TranslationManager.string(this.player, Translations.GUI_MAIN_VIEW_ITEMS_STATS_NAME))
						.lore(TranslationManager.list(this.player, Translations.GUI_MAIN_VIEW_ITEMS_STATS_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
						.make(), click -> {
			// Check permission before opening stats GUI
			if (!click.player.hasPermission("markets.viewstats")) {
				Common.tell(click.player, TranslationManager.string(click.player, Translations.NO_PERMISSION));
				return;
			}
			click.manager.showGUI(click.player, new MarketStatsGUI(click.player, playerMarket));
		});

		// bank
		setButton(Settings.ALLOW_BANK.getBoolean() ? Settings.GUI_MAIN_VIEW_ITEMS_BANK_SLOT.getInt() : -1,
				QuickItem
						.of(Settings.GUI_MAIN_VIEW_ITEMS_BANK.getItemStack())
						.hideTags(true)
						.name(TranslationManager.string(this.player, Translations.GUI_MAIN_VIEW_ITEMS_BANK_NAME))
						.lore(TranslationManager.list(this.player, Translations.GUI_MAIN_VIEW_ITEMS_BANK_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
						.make(), click -> click.manager.showGUI(click.player, new BankGUI(new MarketsMainGUI(click.player), click.player)));

		// offers
		setButton(Settings.GUI_MAIN_VIEW_ITEMS_OFFERS_SLOT.getInt(),
				QuickItem
						.of(Settings.GUI_MAIN_VIEW_ITEMS_OFFERS.getItemStack())
						.hideTags(true)
						.name(TranslationManager.string(this.player, Translations.GUI_MAIN_VIEW_ITEMS_OFFERS_NAME))
						.lore(TranslationManager.list(this.player, Translations.GUI_MAIN_VIEW_ITEMS_OFFERS_LORE, "left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK)))
						.make(), click -> click.manager.showGUI(click.player, new OffersGUI(new MarketsMainGUI(click.player), click.player)));

	}
}
