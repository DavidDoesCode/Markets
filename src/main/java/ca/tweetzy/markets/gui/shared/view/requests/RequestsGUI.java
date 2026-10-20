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
import ca.tweetzy.markets.model.DupeDetector;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RequestsGUI extends MarketsPagedGUI<Request> {

	private static final Set<UUID> FULFILLING = ConcurrentHashMap.newKeySet();

	private final boolean viewOwnRequests;
	private boolean clickLock = false;

	public RequestsGUI(Gui parent, @NonNull Player player, final boolean viewOwnRequests) {
		super(parent, player, TranslationManager.string(player, viewOwnRequests ? Translations.GUI_REQUEST_TITLE_YOURS : Translations.GUI_REQUEST_TITLE_ALL), 6, viewOwnRequests ? Markets.getRequestManager().getRequestsBy(player) : Markets.getRequestManager().getRequestsExclude(player));
		this.viewOwnRequests = viewOwnRequests;
		setDefaultItem(QuickItem.bg(Settings.GUI_REQUEST_BACKGROUND.getItemStack()));
		draw();
	}

	@Override
	protected void prePopulate() {
		// Data is loaded in constructor, don't reload here
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
						"request_price", String.format("%,.2f", request.getPrice()),
						"request_currency", request.getCurrencyDisplayName(),
						"request_amount", request.getRequestedAmount(),
						"left_click", TranslationManager.string(player, Translations.MOUSE_LEFT_CLICK),
						"drop_key", TranslationManager.string(player, Translations.DROP_KEY)
				))
				.make();
	}

	@Override
	protected void onClick(Request request, GuiClickEvent click) {
		// Handle admin deletion with Q key (DROP click type)
		if (click.clickType == ClickType.DROP) {
			if (click.player.isOp() || click.player.hasPermission("markets.admin.requests")) {
				if (FULFILLING.contains(request.getId())) {
					Common.tell(click.player, TranslationManager.string(click.player, Translations.REQUEST_IN_PROGRESS));
					return;
				}

				click.manager.showGUI(click.player, new ConfirmGUI(this, click.player, confirmed -> {
					if (confirmed) {
						if (FULFILLING.contains(request.getId())) {
							Common.tell(click.player, TranslationManager.string(click.player, Translations.REQUEST_IN_PROGRESS));
							click.manager.showGUI(click.player, new RequestsGUI(this.parent, click.player, this.viewOwnRequests));
							return;
						}

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
			if (FULFILLING.contains(request.getId())) {
				Common.tell(click.player, TranslationManager.string(click.player, Translations.REQUEST_IN_PROGRESS));
				return;
			}

			request.unStore(result -> {
				if (result == SynchronizeResult.FAILURE) return;
				click.manager.showGUI(click.player, new RequestsGUI(this.parent, click.player, this.viewOwnRequests));
			});

			return;
		}

		if (clickLock || !FULFILLING.add(request.getId())) {
			DupeDetector.logPreventedAttempt(
					"REQUEST_DOUBLE_FULFILL",
					click.player,
					Bukkit.getPlayer(request.getOwner()),
					request.getRequestItem(),
					request.getRequestedAmount(),
					null,
					null,
					"request=" + request.getId()
			);
			return;
		}
		clickLock = true;

		final Player fulfiller = click.player;
		if (!hasRequestItems(fulfiller, request)) {
			Common.tell(fulfiller, TranslationManager.string(fulfiller, Translations.NOT_ENOUGH_ITEMS));
			releaseFulfill(request);
			return;
		}

		final String[] parts = request.getCurrency().split("/");
		if (parts.length < 2) {
			releaseFulfill(request);
			return;
		}

		final String currencyPlugin = parts[0];
		final String currencyName = parts[1];

		// Load OfflinePlayer asynchronously to avoid blocking main thread. Balance is re-checked on the main thread.
		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			final OfflinePlayer requestedOwner = Bukkit.getOfflinePlayer(request.getOwner());
			Bukkit.getScheduler().runTask(Markets.getInstance(), () ->
					fulfillRequestMainThread(click, request, fulfiller, requestedOwner, currencyPlugin, currencyName));
		});
	}

	private void fulfillRequestMainThread(GuiClickEvent click, Request request, Player fulfiller, OfflinePlayer requestedOwner, String currencyPlugin, String currencyName) {
		if (!fulfiller.isOnline() || !hasRequestItems(fulfiller, request)) {
			if (fulfiller.isOnline())
				Common.tell(fulfiller, TranslationManager.string(fulfiller, Translations.NOT_ENOUGH_ITEMS));
			releaseFulfill(request);
			return;
		}

		if (request.isCurrencyOfItem()) {
			final int charge = (int) request.getPrice();
			if (charge <= 0) {
				if (!removeRequestItems(fulfiller, request)) {
					Common.tell(fulfiller, TranslationManager.string(fulfiller, Translations.NOT_ENOUGH_ITEMS));
					releaseFulfill(request);
					return;
				}
				deleteRequestThenPay(click, request, fulfiller, requestedOwner, () -> {
				}, () -> {
				});
				return;
			}

			final BankEntry entry = Markets.getBankManager().getEntryByPlayer(request.getOwner(), request.getCurrencyItem());
			if (entry == null || entry.getQuantity() < charge) {
				Common.tell(fulfiller, TranslationManager.string(fulfiller, Translations.REQUESTER_CANT_PAY, "requester_name", request.getOwnerName()));
				releaseFulfill(request);
				return;
			}

			final int previous = entry.getQuantity();
			entry.setQuantity(previous - charge);
			entry.sync(bankResult -> Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
				if (bankResult != SynchronizeResult.SUCCESS || !fulfiller.isOnline()) {
					entry.setQuantity(previous);
					entry.sync(null);
					releaseFulfill(request);
					return;
				}

				if (!removeRequestItems(fulfiller, request)) {
					entry.setQuantity(previous);
					entry.sync(null);
					Common.tell(fulfiller, TranslationManager.string(fulfiller, Translations.NOT_ENOUGH_ITEMS));
					releaseFulfill(request);
					return;
				}

				deleteRequestThenPay(click, request, fulfiller, requestedOwner, () -> {
					entry.setQuantity(previous);
					entry.sync(null);
				}, () -> {
					if (entry.getQuantity() <= 0)
						entry.unStore(null);

					Markets.getOfflineItemPaymentManager().create(
							fulfiller.getUniqueId(),
							QuickItem.of(request.getCurrencyItem()).amount(1).make(),
							charge,
							TranslationManager.string(Translations.REQUEST_PAYMENT), success -> {
							});
				});
			}));
			return;
		}

		if (!Markets.getCurrencyManager().has(requestedOwner, currencyPlugin, currencyName, request.getPrice())) {
			Common.tell(fulfiller, TranslationManager.string(fulfiller, Translations.REQUESTER_CANT_PAY, "requester_name", request.getOwnerName()));
			releaseFulfill(request);
			return;
		}

		if (!Markets.getCurrencyManager().withdraw(requestedOwner, currencyPlugin, currencyName, request.getPrice())) {
			Common.tell(fulfiller, TranslationManager.string(fulfiller, Translations.REQUESTER_CANT_PAY, "requester_name", request.getOwnerName()));
			releaseFulfill(request);
			return;
		}

		if (!removeRequestItems(fulfiller, request)) {
			Markets.getCurrencyManager().deposit(requestedOwner, currencyPlugin, currencyName, request.getPrice());
			Common.tell(fulfiller, TranslationManager.string(fulfiller, Translations.NOT_ENOUGH_ITEMS));
			releaseFulfill(request);
			return;
		}

		deleteRequestThenPay(click, request, fulfiller, requestedOwner, () ->
				Markets.getCurrencyManager().deposit(requestedOwner, currencyPlugin, currencyName, request.getPrice()), () ->
				Markets.getCurrencyManager().deposit(fulfiller, currencyPlugin, currencyName, request.getPrice()));
	}

	private void deleteRequestThenPay(GuiClickEvent click, Request request, Player fulfiller, OfflinePlayer requestedOwner, Runnable refund, Runnable payFulfiller) {
		request.unStore(deleteResult -> Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
			if (deleteResult != SynchronizeResult.SUCCESS) {
				giveItems(fulfiller, request.getRequestItem(), request.getRequestedAmount());
				refund.run();
				releaseFulfill(request);
				return;
			}

			payFulfiller.run();
			Markets.getOfflineItemPaymentManager().create(
					request.getOwner(),
					QuickItem.of(request.getRequestItem()).amount(1).make(),
					request.getRequestedAmount(),
					TranslationManager.string(Translations.REQUEST_PAYMENT), success -> {
					});

			final Player requestOwnerPlayer = Bukkit.getPlayer(request.getOwner());
			if (requestOwnerPlayer != null) {
				Common.tell(requestOwnerPlayer, TranslationManager.string(requestOwnerPlayer, Translations.REQUEST_FULFILLED, "fulfill_name", fulfiller.getName(), "request_item_name", ItemUtil.getItemName(request.getRequestItem())));
			}

			Common.tell(click.player, TranslationManager.string(Translations.REQUEST_FULFILLED_FILLER,
					"request_item_name", ItemUtil.getItemName(request.getRequestItem()),
					"fulfill_name", request.getOwnerName()
			));

			Bukkit.getServer().getPluginManager().callEvent(new MarketTransactionEvent(
					fulfiller,
					requestedOwner,
					TransactionType.REQUEST_FULFILLMENT,
					QuickItem.of(request.getCurrencyItem()).amount(1).make(),
					request.getCurrencyDisplayName(),
					request.getRequestedAmount(),
					request.isCurrencyOfItem() ? request.getPrice() : (int) request.getPrice()
			));

			FULFILLING.remove(request.getId());
			clickLock = false;
			if (click.player.isOnline())
				click.manager.showGUI(click.player, new RequestsGUI(this.parent, click.player, this.viewOwnRequests));
		}));
	}

	private void releaseFulfill(@NonNull final Request request) {
		FULFILLING.remove(request.getId());
		this.clickLock = false;
	}

	private boolean hasRequestItems(@NonNull final Player player, @NonNull final Request request) {
		return player.isOnline() && PlayerUtil.getItemCountInPlayerInventory(player, request.getRequestItem()) >= request.getRequestedAmount();
	}

	private boolean removeRequestItems(@NonNull final Player player, @NonNull final Request request) {
		final ItemStack item = request.getRequestItem();
		final int amount = request.getRequestedAmount();
		final int before = PlayerUtil.getItemCountInPlayerInventory(player, item);
		if (before < amount)
			return false;

		PlayerUtil.removeSpecificItemQuantityFromPlayer(player, item, amount);
		final int removed = before - PlayerUtil.getItemCountInPlayerInventory(player, item);
		if (removed < amount) {
			if (removed > 0)
				giveItems(player, item, removed);
			return false;
		}
		return true;
	}

	private void giveItems(@NonNull final Player player, @NonNull final ItemStack template, final int qty) {
		if (!player.isOnline() || qty <= 0)
			return;

		int remaining = qty;
		final int maxStack = Math.max(1, template.getMaxStackSize());
		while (remaining > 0) {
			final int stackSize = Math.min(remaining, maxStack);
			final ItemStack stack = template.clone();
			stack.setAmount(stackSize);
			PlayerUtil.giveItem(player, stack);
			remaining -= stackSize;
		}
	}

	@Override
	protected List<Integer> fillSlots() {
		return InventoryBorder.getInsideBorders(6);
	}
}
