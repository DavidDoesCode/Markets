package ca.tweetzy.markets.gui.user;

import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.gui.helper.InventoryBorder;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.flight.utils.TimeUtil;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.SynchronizeResult;
import ca.tweetzy.markets.api.currency.Payment;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.model.WorthPriceLimiter;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public final class OfflinePaymentsGUI extends MarketsPagedGUI<Payment> {

	private final Player player;
	private final UUID targetUuid;
	private final String targetName;

	public OfflinePaymentsGUI(Gui parent, @NonNull final Player player) {
		this(parent, player, player.getUniqueId(), null);
	}

	public OfflinePaymentsGUI(Gui parent, @NonNull final Player player, @NonNull final UUID targetUuid, final String targetName) {
		super(parent, player, buildTitle(player, targetName), 6, Markets.getOfflineItemPaymentManager().getPaymentsFor(targetUuid));
		this.player = player;
		this.targetUuid = targetUuid;
		this.targetName = targetName;
		setDefaultItem(QuickItem.bg(Settings.GUI_OFFLINE_PAYMENTS_BACKGROUND.getItemStack()));
		draw();
	}

	private static String buildTitle(@NonNull final Player viewer, final String targetName) {
		if (targetName != null) {
			return TranslationManager.string(viewer, Translations.GUI_OFFLINE_PAYMENTS_TITLE) + " - " + targetName;
		}
		return TranslationManager.string(viewer, Translations.GUI_OFFLINE_PAYMENTS_TITLE);
	}

	@Override
	protected ItemStack makeDisplayItem(Payment payment) {
		return QuickItem
				.of(payment.getCurrency())
				.lore(TranslationManager.list(
						this.player,
						Translations.GUI_OFFLINE_PAYMENTS_ITEMS_PROFILE_LORE,
						"payment_total", payment.getAmount(),
						"payment_reason", payment.getReason(),
						"payment_date", TimeUtil.convertToReadableDate(payment.getTimeCreated(), Settings.DATETIME_FORMAT.getString()),
						"left_click", TranslationManager.string(Translations.MOUSE_LEFT_CLICK)
				))
				.make();
	}

	@Override
	protected void onClick(Payment payment, GuiClickEvent click) {
		final int total = (int) payment.getAmount();
		final int maxCollect = WorthPriceLimiter.getMaxPurchaseQuantity(payment.getCurrency());
		final int collectQty = Math.min(total, maxCollect);
		final boolean partial = collectQty < total;

		if (partial) {
			final double previousAmount = payment.getAmount();
			payment.setAmount(total - collectQty);
			payment.sync(result -> {
				if (result == SynchronizeResult.FAILURE) {
					payment.setAmount(previousAmount);
					return;
				}
				Markets.getCurrencyManager().deposit(click.player, payment.getCurrency(), collectQty);
				Common.tell(click.player, TranslationManager.string(click.player, Translations.PAYMENT_COLLECTED_PARTIAL,
						"collected_amount", collectQty,
						"remaining_amount", total - collectQty));
				click.gui.exit();
			});
			return;
		}

		payment.unStore(result -> {
			if (result == SynchronizeResult.FAILURE) return;
			Markets.getCurrencyManager().deposit(click.player, payment.getCurrency(), collectQty);
			click.manager.showGUI(click.player, new OfflinePaymentsGUI(this.parent, click.player, this.targetUuid, this.targetName));
		});
	}

	@Override
	protected List<Integer> fillSlots() {
		return InventoryBorder.getInsideBorders(5);
	}
}
