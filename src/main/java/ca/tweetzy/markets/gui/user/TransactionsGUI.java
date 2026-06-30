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
import java.util.UUID;

public class TransactionsGUI extends MarketsPagedGUI<Transaction> {

	private final Player player;
	private final UUID targetUuid;
	private final String targetName;
	private boolean viewAll;
	private PlayerRole filterType = PlayerRole.SELLER;
	protected boolean isLoading = false;
	private boolean guiShown = false;
	private int loadRequestId = 0;

	private enum PlayerRole {
		BUYER, SELLER
	}

	public TransactionsGUI(Gui parent, @NonNull final Player player, boolean viewAll) {
		this(parent, player, viewAll, null, null);
	}

	public TransactionsGUI(Gui parent, @NonNull final Player player, boolean viewAll, UUID targetUuid, String targetName) {
		super(parent, player, buildTitle(player, targetName), 6, new ArrayList<>());
		this.player = player;
		this.targetUuid = targetUuid;
		this.targetName = targetName;
		this.viewAll = viewAll;
		setAcceptsItems(true);
		setDefaultItem(QuickItem.bg(Settings.GUI_TRANSACTIONS_BACKGROUND.getItemStack()));
		setOnOpen(open -> draw());

		loadTransactionsAsync();
		this.guiShown = true;
	}

	private static String buildTitle(@NonNull final Player viewer, String targetName) {
		if (targetName != null) {
			return TranslationManager.string(viewer, Translations.GUI_TRANSACTIONS_TITLE) + " - " + targetName;
		}
		return TranslationManager.string(viewer, Translations.GUI_TRANSACTIONS_TITLE);
	}

	@Override
	protected void prePopulate() {
		// Loaded in constructor and when filters change to avoid async draw loops
	}

	private void loadTransactionsAsync() {
		final int requestId = ++this.loadRequestId;
		this.isLoading = true;

		if (this.guiShown) {
			draw();
		}

		final UUID playerUuid = this.targetUuid != null ? this.targetUuid : this.player.getUniqueId();

		if (this.viewAll) {
			Markets.getTransactionManager().getAllTransactionsForAsync(transactions ->
					applyTransactionResult(requestId, transactions));
			return;
		}

		if (this.filterType == PlayerRole.SELLER) {
			Markets.getTransactionManager().getSalesTransactionsForAsync(playerUuid, transactions ->
					applyTransactionResult(requestId, transactions));
			return;
		}

		Markets.getTransactionManager().getPurchaseTransactionsForAsync(playerUuid, transactions ->
				applyTransactionResult(requestId, transactions));
	}

	private void applyTransactionResult(final int requestId, @NonNull final List<Transaction> transactions) {
		if (requestId != this.loadRequestId) return;

		this.items = new ArrayList<>(transactions);
		this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
		this.isLoading = false;

		if (isStillOpen()) {
			draw();
		}
	}

	private boolean isStillOpen() {
		return this.player.isOnline() && this.player.getOpenInventory().getTopInventory().equals(this.inventory);
	}

	@Override
	protected void drawFixed() {
		if (!Settings.USE_ADDITIONAL_CONFIRMS.getBoolean()) {
			setTransactionViewButton();
		} else if (this.player.hasPermission("markets.viewalltransactions")) {
			setTransactionViewButton();
		}

		setTransactionTypeToggle();

		if (this.isLoading) {
			showLoadingIndicator();
		}
	}

	private void showLoadingIndicator() {
		setButton(2, 4, QuickItem
				.of(new ItemStack(Material.HOPPER))
				.name(TranslationManager.string(Translations.GUI_LOADING_INDICATOR_NAME))
				.lore(TranslationManager.list(Translations.GUI_LOADING_INDICATOR_LORE))
				.make(), click -> {});
	}

	private void setTransactionViewButton() {
		setButton(getRows() - 1, 8, QuickItem
				.of(Settings.GUI_TRANSACTIONS_VIEW_ALL_ITEM.getItemStack())
				.name(TranslationManager.string(Translations.GUI_TRANSACTIONS_ITEMS_VIEW_ALL_NAME))
				.lore(TranslationManager.list(Translations.GUI_TRANSACTIONS_ITEMS_VIEW_ALL_LORE, "is_true", TranslationManager.string(this.viewAll ? Translations.TRUE : Translations.FALSE), "left_click", TranslationManager.string(Translations.MOUSE_LEFT_CLICK)))
				.make(), click -> {
			if (this.isLoading) return;

			this.viewAll = !this.viewAll;
			this.page = 1;
			loadTransactionsAsync();
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
			if (this.isLoading) return;

			if (this.filterType == PlayerRole.SELLER) {
				this.filterType = PlayerRole.BUYER;
			} else {
				this.filterType = PlayerRole.SELLER;
			}
			this.page = 1;
			loadTransactionsAsync();
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
