package ca.tweetzy.markets.gui.user;

import ca.tweetzy.flight.comp.enums.CompMaterial;
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
	protected boolean isLoading = false;  // Track loading state

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

			this.viewAll = !this.viewAll;
			draw();
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

			// Cycle through: null (all) -> ITEM_PURCHASE -> REQUEST_FULFILLMENT -> null
			if (this.filterType == PlayerRole.SELLER) {
				this.filterType = PlayerRole.BUYER;
			} else {
				this.filterType = PlayerRole.SELLER;
			}
			draw();
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
