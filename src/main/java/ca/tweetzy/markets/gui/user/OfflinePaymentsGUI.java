package ca.tweetzy.markets.gui.user;

import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.gui.helper.InventoryBorder;
import ca.tweetzy.flight.settings.TranslationEntry;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.ItemUtil;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.flight.utils.TimeUtil;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.SynchronizeResult;
import ca.tweetzy.markets.api.currency.Payment;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.gui.shared.selector.ConfirmGUI;
import ca.tweetzy.markets.model.AdminActionLogger;
import ca.tweetzy.markets.model.FloodGateCheck;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public final class OfflinePaymentsGUI extends MarketsPagedGUI<Payment> {

	private final Player player;
	private final UUID targetUuid;
	private final String targetName;
	private boolean collecting;

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

	private boolean isAdminViewingOther() {
		return this.targetName != null
				&& !this.targetUuid.equals(this.player.getUniqueId())
				&& (this.player.isOp() || this.player.hasPermission("markets.admin.payments"));
	}

	@Override
	protected ItemStack makeDisplayItem(Payment payment) {
		final TranslationEntry lore = isAdminViewingOther()
				? Translations.GUI_OFFLINE_PAYMENTS_ITEMS_PROFILE_LORE_ADMIN
				: Translations.GUI_OFFLINE_PAYMENTS_ITEMS_PROFILE_LORE;

		return QuickItem
				.of(payment.getCurrency())
				.lore(TranslationManager.list(
						this.player,
						lore,
						"payment_total", payment.getAmount(),
						"payment_reason", payment.getReason(),
						"payment_date", TimeUtil.convertToReadableDate(payment.getTimeCreated(), Settings.DATETIME_FORMAT.getString()),
						"left_click", TranslationManager.string(this.player, Translations.MOUSE_LEFT_CLICK),
						"drop_key", TranslationManager.string(this.player, Translations.DROP_KEY)
				))
				.make();
	}

	@Override
	protected void onClick(Payment payment, GuiClickEvent click) {
		if (this.collecting)
			return;

		if (click.clickType == ClickType.DROP || click.clickType == ClickType.CONTROL_DROP) {
			if (isAdminViewingOther())
				confirmDelete(payment, click);
			return;
		}

		if (click.clickType != ClickType.LEFT && !FloodGateCheck.isBedrock(click.player))
			return;

		collect(payment, click);
	}

	private void collect(Payment payment, GuiClickEvent click) {
		final int total = (int) payment.getAmount();
		if (total <= 0)
			return;

		final int collectQty = Math.min(total, freeSpace(click.player, payment.getCurrency()));
		if (collectQty <= 0) {
			Common.tell(click.player, TranslationManager.string(click.player, Translations.PAYMENT_COLLECT_INVENTORY_FULL));
			return;
		}

		this.collecting = true;
		final boolean partial = collectQty < total;

		if (partial) {
			final double previousAmount = payment.getAmount();
			payment.setAmount(total - collectQty);
			payment.sync(result -> Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
				this.collecting = false;
				if (result == SynchronizeResult.FAILURE) {
					payment.setAmount(previousAmount);
					return;
				}

				if (!click.player.isOnline())
					return;

				Markets.getCurrencyManager().deposit(click.player, payment.getCurrency(), collectQty);
				Common.tell(click.player, TranslationManager.string(click.player, Translations.PAYMENT_COLLECTED_PARTIAL,
						"collected_amount", collectQty,
						"remaining_amount", total - collectQty));
				click.manager.showGUI(click.player, reopen());
			}));
			return;
		}

		payment.unStore(result -> Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
			this.collecting = false;
			if (result == SynchronizeResult.FAILURE || !click.player.isOnline())
				return;

			Markets.getCurrencyManager().deposit(click.player, payment.getCurrency(), collectQty);
			click.manager.showGUI(click.player, reopen());
		}));
	}

	private void confirmDelete(Payment payment, GuiClickEvent click) {
		final int total = (int) payment.getAmount();
		click.manager.showGUI(click.player, new ConfirmGUI(this, click.player, confirmed -> {
			if (!confirmed) {
				click.manager.showGUI(click.player, this);
				return;
			}

			payment.unStore(result -> Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
				if (!click.player.isOnline())
					return;

				if (result == SynchronizeResult.FAILURE) {
					Common.tell(click.player, TranslationManager.string(click.player, Translations.PAYMENT_DELETE_FAILED));
					click.manager.showGUI(click.player, reopen());
					return;
				}

				AdminActionLogger.log(click.player.getName(), String.format(
						"Deleted offline payment for %s (%s): x%d %s — %s",
						this.targetName,
						this.targetUuid,
						total,
						ItemUtil.getItemName(payment.getCurrency()),
						payment.getReason()
				));
				Common.tell(click.player, TranslationManager.string(click.player, Translations.PAYMENT_DELETED,
						"payment_total", total,
						"target_name", this.targetName));
				click.manager.showGUI(click.player, reopen());
			}));
		}));
	}

	private OfflinePaymentsGUI reopen() {
		return new OfflinePaymentsGUI(this.parent, this.player, this.targetUuid, this.targetName);
	}

	private static int freeSpace(@NonNull final Player player, @NonNull final ItemStack template) {
		final int maxStack = Math.max(1, template.getMaxStackSize());
		int space = 0;

		for (ItemStack stack : player.getInventory().getStorageContents()) {
			if (stack == null || stack.getType().isAir())
				space += maxStack;
			else if (stack.isSimilar(template))
				space += Math.max(0, maxStack - stack.getAmount());
		}

		return space;
	}

	@Override
	protected List<Integer> fillSlots() {
		return InventoryBorder.getInsideBorders(5);
	}
}
