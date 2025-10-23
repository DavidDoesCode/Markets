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
		if (this.viewAll) {
			this.items = new ArrayList<>(Markets.getTransactionManager().getManagerContent());
		} else {
			// Filter by transaction type if a filter is set
			if (this.filterType == PlayerRole.SELLER) {
				this.items = new ArrayList<>(Markets.getTransactionManager().getSalesTransactionsFor((this.player.getUniqueId())));
			} else {
				this.items = new ArrayList<>(Markets.getTransactionManager().getPurchaseTransactionsFor((this.player.getUniqueId())));
			}
		}

		this.items.sort(Comparator.comparing(Transaction::getTimeCreated).reversed());
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
