package ca.tweetzy.markets.model;

import ca.tweetzy.flight.utils.ItemUtil;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.SynchronizeResult;
import ca.tweetzy.markets.api.market.core.Category;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketItem;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

@UtilityClass
public final class AuditListingRemover {

	public static final int AUDIT_CLEAR_STOCK_SKIP_THRESHOLD = 1000;

	/**
	 * Deletes the listing and returns remaining stock to the owner via offline payments.
	 * If stock is 0, only deletes the listing.
	 */
	public void removeListing(@NonNull final MarketItem marketItem, final String adminName,
							  @NonNull final String reason, @NonNull final Consumer<Boolean> completed) {
		final MarketItem live = Markets.getCategoryItemManager().getByUUID(marketItem.getId());
		if (live == null) {
			completed.accept(false);
			return;
		}

		final Category category = Markets.getCategoryManager().getByUUID(live.getOwningCategory());
		final Market market = category != null ? Markets.getMarketManager().getByUUID(category.getOwningMarket()) : null;
		if (market == null) {
			completed.accept(false);
			return;
		}

		final int stockAmount = live.isInfinite() ? 0 : Math.max(0, live.getStock());
		final ItemStack itemToReturn = live.getItem().clone();
		itemToReturn.setAmount(1);
		final Player actor = adminName != null ? Bukkit.getPlayerExact(adminName) : null;

		live.unStore(actor, result -> {
			if (result != SynchronizeResult.SUCCESS) {
				completed.accept(false);
				return;
			}

			AdminActionLogger.log(
					adminName != null ? adminName : "CONSOLE",
					String.format("Audit-removed %d x %s from market '%s' (Owner: %s) — %s",
							stockAmount,
							ItemUtil.getItemName(itemToReturn),
							market.getDisplayName(),
							market.getOwnerName(),
							reason
					)
			);

			if (stockAmount <= 0 || market.isServerMarket()) {
				completed.accept(true);
				return;
			}

			Markets.getOfflineItemPaymentManager().create(
					market.getOwnerUUID(),
					itemToReturn,
					stockAmount,
					reason,
					created -> {
						if (!created)
							Markets.getInstance().getLogger().warning(String.format(
									"Deleted listing %s but failed to create offline payment for owner %s (%d x %s)",
									live.getId(), market.getOwnerName(), stockAmount, ItemUtil.getItemName(itemToReturn)));
						completed.accept(true);
					}
			);
		});
	}

	public boolean shouldSkipForAuditClear(@NonNull final MarketItem marketItem) {
		if (marketItem.isInfinite())
			return true;
		return marketItem.getStock() > AUDIT_CLEAR_STOCK_SKIP_THRESHOLD;
	}
}
