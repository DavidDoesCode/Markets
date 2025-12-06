package ca.tweetzy.markets.gui.shared.view.requests;

import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.gui.helper.InventoryBorder;
import ca.tweetzy.flight.settings.TranslationEntry;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.ItemUtil;
import ca.tweetzy.flight.utils.PlayerUtil;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.SynchronizeResult;
import ca.tweetzy.markets.api.event.MarketTransactionEvent;
import ca.tweetzy.markets.api.market.BankEntry;
import ca.tweetzy.markets.api.market.Request;
import ca.tweetzy.markets.api.market.TransactionType;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.gui.shared.selector.ConfirmGUI;
import ca.tweetzy.markets.impl.MarketRequest;
import ca.tweetzy.markets.model.AdminActionLogger;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class RequestsGUI extends MarketsPagedGUI<Request> {

	private final boolean viewOwnRequests;
	private boolean clickLock = false;

	public RequestsGUI(Gui parent, @NonNull Player player, final boolean viewOwnRequests) {
		super(parent, player, TranslationManager.string(player, viewOwnRequests ? Translations.GUI_REQUEST_TITLE_YOURS : Translations.GUI_REQUEST_TITLE_ALL), 6, viewOwnRequests ? Markets.getRequestManager().getRequestsBy(player) : Markets.getRequestManager().getRequestsExclude(player));
		this.viewOwnRequests = viewOwnRequests;
		setDefaultItem(QuickItem.bg(Settings.GUI_REQUEST_BACKGROUND.getItemStack()));
		draw();
	}

	@Override
	protected void drawFixed() {
		setButton(getRows() - 1, 7, QuickItem
				.of(Settings.GUI_REQUEST_ITEMS_TOGGLE_ITEM.getItemStack())
				.name(TranslationManager.string(player, Translations.GUI_REQUEST_ITEMS_TOGGLE_NAME))
				.lore(TranslationManager.list(player, Translations.GUI_REQUEST_ITEMS_TOGGLE_LORE, "left_click", TranslationManager.string(player, Translations.MOUSE_LEFT_CLICK)))
				.make(), click -> {
			// Toggle view and create new GUI
			click.manager.showGUI(click.player, new RequestsGUI(this.parent, click.player, !this.viewOwnRequests));
		});

		setButton(getRows() - 1, 4, QuickItem
				.of(Settings.GUI_REQUEST_ITEMS_CREATE_ITEM.getItemStack())
				.name(TranslationManager.string(player, Translations.GUI_REQUEST_ITEMS_CREATE_NAME))
				.lore(TranslationManager.list(player, Translations.GUI_REQUEST_ITEMS_CREATE_LORE, "left_click", TranslationManager.string(player, Translations.MOUSE_LEFT_CLICK)))
				.make(), click -> click.manager.showGUI(click.player, new RequestCreateGUI(this, this.player, new MarketRequest(click.player))));
	}

	@Override
	protected ItemStack makeDisplayItem(Request request) {
		// Determine which lore to use based on ownership and admin status
		boolean isOwnRequest = request.getOwner().equals(this.player.getUniqueId());
		boolean isAdmin = this.player.isOp() || this.player.hasPermission("markets.admin.requests");

		// Select appropriate translation entry
		TranslationEntry loreEntry;
		if (isOwnRequest) {
			loreEntry = Translations.GUI_REQUEST_ITEMS_REQUEST_LORE_SELF;
		} else if (isAdmin) {
			loreEntry = Translations.GUI_REQUEST_ITEMS_REQUEST_LORE_OTHER_ADMIN;
		} else {
			loreEntry = Translations.GUI_REQUEST_ITEMS_REQUEST_LORE_OTHER;
		}

		return QuickItem
				.of(request.getRequestItem())
				.lore(TranslationManager.list(this.player, loreEntry,
						"request_owner_name", request.getOwnerName(),
						"request_date", request.getFormattedDate(),
						"request_price", String.format("%,.2f", request.getPrice()),
						"request_currency", request.getCurrencyDisplayName(),
						"request_amount", request.getRequestedAmount(),
						"left_click", TranslationManager.string(player, Translations.MOUSE_LEFT_CLICK),
						"drop_key", TranslationManager.string(player, Translations.MOUSE_DROP)
				))
				.make();
	}

	@Override
	protected void onClick(Request request, GuiClickEvent click) {
		// Handle admin deletion with Q key (DROP click type)
		if (click.clickType == ClickType.DROP) {
			if (click.player.isOp() || click.player.hasPermission("markets.admin.requests")) {
				click.manager.showGUI(click.player, new ConfirmGUI(this, click.player, confirmed -> {
					if (confirmed) {
						// Log admin action
						AdminActionLogger.log(click.player.getName(), "Removed request" +
							"Request Owner: " + request.getOwnerName() + " (" + request.getOwner() + ")" +
							"Item: " + request.getRequestItem().getType().name() +
							"Amount: " + request.getRequestedAmount() +
							"Price: " + request.getPrice() + " " + request.getCurrencyDisplayName()
						);

						// Delete the request
						request.unStore(result -> {
							if (result == SynchronizeResult.FAILURE) {
								Common.tell(click.player, "&cSomething went wrong trying to delete the player request for " + request.getOwnerName());
								return;
							}

							// Refresh GUI
							click.manager.showGUI(click.player, new RequestsGUI(this.parent, click.player, this.viewOwnRequests));
							Common.tell(click.player, "&aRequest removed successfully");
						});
					} else {
						// User cancelled - return to this GUI
						click.manager.showGUI(click.player, this);
					}
				}));
			}
			return;
		}

		// Handle player cancelling their own request
		if (request.getOwner().equals(click.player.getUniqueId())) {
			request.unStore(result -> {
				if (result == SynchronizeResult.FAILURE) return;
				click.manager.showGUI(click.player, new RequestsGUI(this.parent, click.player, this.viewOwnRequests));
			});

			return;
		}

		if(clickLock) {
			Bukkit.getLogger().severe(click.player.getName() + " sending duplicate request fulfillment.");
			return;
		} else
			clickLock = true;

		// ================================== FULFILL THE REQUEST ================================== //

		final Player fulfiller = click.player;
		final OfflinePlayer requestedOwner = Bukkit.getOfflinePlayer(request.getOwner());

		// do they even have enough items
		if (PlayerUtil.getItemCountInPlayerInventory(fulfiller, request.getRequestItem()) < request.getRequestedAmount()) {
			Common.tell(fulfiller, TranslationManager.string(fulfiller, Translations.NOT_ENOUGH_ITEMS));
			clickLock = false;
			return;
		}

		final String currencyPlugin = request.getCurrency().split("/")[0];
		final String currencyName = request.getCurrency().split("/")[1];

		boolean hasEnoughMoney = request.isCurrencyOfItem() ?
				Markets.getBankManager().getEntryCountByPlayer(request.getOwner(), request.getCurrencyItem()) >= (int) request.getPrice() :
				Markets.getCurrencyManager().has(requestedOwner, currencyPlugin, currencyName, request.getPrice());

		if (!hasEnoughMoney) {
			Common.tell(fulfiller, TranslationManager.string(fulfiller, Translations.REQUESTER_CANT_PAY, "requester_name", request.getOwnerName()));
			clickLock = false;
			return;
		}

		if (request.isCurrencyOfItem()) {
			final BankEntry entry = Markets.getBankManager().getEntryByPlayer(request.getOwner(), request.getCurrencyItem());
			final int newTotal = entry.getQuantity() - (int) request.getPrice();

			// remove or update the custom item from the requester bank
			if (newTotal <= 0) {
				entry.unStore(entryResult -> {
					if (entryResult == SynchronizeResult.FAILURE) return;
				});
			} else {
				entry.setQuantity(newTotal);
				entry.sync(entryResult -> {
					if (entryResult == SynchronizeResult.FAILURE) return;
				});
			}

			// give the fulfilling user their items
			Markets.getOfflineItemPaymentManager().create(
					fulfiller.getUniqueId(),
					QuickItem.of(request.getCurrencyItem()).amount(1).make(),
					(int) request.getPrice(),
					TranslationManager.string(Translations.REQUEST_PAYMENT), success -> {
					});

		} else {
			Markets.getCurrencyManager().deposit(fulfiller, currencyPlugin, currencyName, request.getPrice());
			Markets.getCurrencyManager().withdraw(requestedOwner, currencyPlugin, currencyName, request.getPrice());
		}

		// take items from the fulfilling user
		PlayerUtil.removeSpecificItemQuantityFromPlayer(fulfiller, request.getRequestItem(), request.getRequestedAmount());

		// give the requester their items
		Markets.getOfflineItemPaymentManager().create(
				request.getOwner(),
				QuickItem.of(request.getRequestItem()).amount(1).make(),
				request.getRequestedAmount(),
				TranslationManager.string(Translations.REQUEST_PAYMENT), success -> {
				});

		if (requestedOwner.isOnline()) {
			Common.tell(requestedOwner.getPlayer(), TranslationManager.string(requestedOwner.getPlayer(), Translations.REQUEST_FULFILLED, "fulfill_name", fulfiller.getName(), "request_item_name", ItemUtil.getItemName(request.getRequestItem())));
		}

		Common.tell(click.player, TranslationManager.string(Translations.REQUEST_FULFILLED_FILLER,
				"request_item_name", ItemUtil.getItemName(request.getRequestItem()),
				"fulfill_name", requestedOwner.getName()
		));

		// call transaction event
		Bukkit.getServer().getPluginManager().callEvent(new MarketTransactionEvent(
				fulfiller,
				requestedOwner,
				TransactionType.REQUEST_FULFILLMENT,
				QuickItem.of(request.getCurrencyItem()).amount(1).make(),
				request.getCurrencyDisplayName(),
				request.getRequestedAmount(),
				request.isCurrencyOfItem() ? request.getPrice() : (int) request.getPrice()
		));

		request.unStore(result -> {
			if (result == SynchronizeResult.FAILURE) return;
			click.manager.showGUI(click.player, new RequestsGUI(this.parent, click.player, this.viewOwnRequests));
		});
	}

	@Override
	protected List<Integer> fillSlots() {
		return InventoryBorder.getInsideBorders(6);
	}
}
