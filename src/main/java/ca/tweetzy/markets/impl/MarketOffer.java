package ca.tweetzy.markets.impl;

import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.SynchronizeResult;
import ca.tweetzy.markets.api.currency.TransactionResult;
import ca.tweetzy.markets.api.market.BankEntry;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketItem;
import ca.tweetzy.markets.api.market.offer.Offer;
import ca.tweetzy.markets.api.market.offer.OfferRejectReason;
import ca.tweetzy.markets.model.DupeDetector;
import ca.tweetzy.markets.settings.Translations;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@AllArgsConstructor
public final class MarketOffer implements Offer {

	private static final Set<UUID> ACCEPTING = ConcurrentHashMap.newKeySet();

	private final UUID uuid;
	private final UUID sender;
	private final String senderName;
	private final UUID offerTo;
	private final UUID marketItem;
	private int requestAmount;
	private String currency;
	private ItemStack currencyItem;
	private double offeredAmount;
	private final long offeredAt;

	public MarketOffer(@NonNull final Player sender, @NonNull final Market market, @NonNull final MarketItem marketItem) {
		this(
				UUID.randomUUID(),
				sender.getUniqueId(),
				sender.getName(),
				market.getOwnerUUID(),
				marketItem.getId(),
				marketItem.getStock(),
				marketItem.getCurrency(),
				marketItem.getCurrencyItem(),
				marketItem.getPrice() * marketItem.getStock(),
				System.currentTimeMillis()
		);
	}

	@Override

	public @NonNull UUID getId() {
		return this.uuid;
	}

	@Override
	public @NonNull UUID getOfferSender() {
		return this.sender;
	}

	@Override
	public @NonNull String getOfferSenderName() {
		return this.senderName;
	}

	@Override
	public @NonNull UUID getOfferFor() {
		return this.offerTo;
	}

	@Override
	public @NonNull UUID getMarketItem() {
		return this.marketItem;
	}

	@Override
	public int getRequestAmount() {
		return this.requestAmount;
	}

	@Override
	public @NonNull String getCurrency() {
		return this.currency;
	}

	@Override
	public ItemStack getCurrencyItem() {
		return this.currencyItem;
	}

	@Override
	public double getOfferedAmount() {
		return this.offeredAmount;
	}

	@Override
	public void setCurrency(@NonNull final String currency) {
		this.currency = currency;
	}

	@Override
	public void setCurrencyItem(@NonNull ItemStack currencyItem) {
		this.currencyItem = currencyItem;
	}

	@Override
	public void setOfferedAmount(double amount) {
		this.offeredAmount = amount;
	}

	@Override
	public void setRequestAmount(int amount) {
		this.requestAmount = amount;
	}

	@Override
	public long getTimeCreated() {
		return this.offeredAt;
	}

	@Override
	public long getLastUpdated() {
		return this.offeredAt;
	}

	@Override
	public void accept(@NonNull Consumer<TransactionResult> result) {
		if (!ACCEPTING.add(this.uuid)) {
			final MarketItem located = Markets.getCategoryItemManager().getByUUID(this.marketItem);
			DupeDetector.logPreventedAttempt(
					"OFFER_DOUBLE_ACCEPT",
					Bukkit.getPlayer(this.offerTo),
					Bukkit.getPlayer(this.sender),
					located != null ? located.getItem() : this.currencyItem,
					this.requestAmount,
					located != null ? located.getOwningMarket() : null,
					located,
					"offer=" + this.uuid
			);
			return;
		}

		final MarketItem locatedItem = Markets.getCategoryItemManager().getByUUID(this.marketItem);
		if (!(locatedItem instanceof CategoryItem categoryItem)) {
			deleteOffer(TransactionResult.NO_LONGER_AVAILABLE, result);
			return;
		}

		final CategoryItem.StockReserveResult reservation = categoryItem.reserveStock(this.requestAmount);
		if (reservation == CategoryItem.StockReserveResult.BUSY) {
			ACCEPTING.remove(this.uuid);
			result.accept(TransactionResult.ERROR);
			return;
		}
		if (reservation == CategoryItem.StockReserveResult.INSUFFICIENT) {
			deleteOffer(TransactionResult.FAILED_OUT_OF_STOCK, result);
			return;
		}

		final OfflinePlayer offerSender = Bukkit.getOfflinePlayer(this.sender);
		final OfflinePlayer itemOwner = Bukkit.getOfflinePlayer(this.offerTo);
		final String[] parts = this.currency == null ? new String[0] : this.currency.split("/");
		if (parts.length < 2) {
			categoryItem.finishStockReservation(this.requestAmount, true);
			ACCEPTING.remove(this.uuid);
			result.accept(TransactionResult.ERROR);
			return;
		}

		if (parts[1].equalsIgnoreCase("item")) {
			acceptWithItemCurrency(categoryItem, itemOwner, result);
			return;
		}

		acceptWithPluginCurrency(categoryItem, offerSender, itemOwner, parts[0], parts[1], result);
	}

	private void acceptWithPluginCurrency(@NonNull final CategoryItem categoryItem, @NonNull final OfflinePlayer offerSender, @NonNull final OfflinePlayer itemOwner, @NonNull final String currencyPlugin, @NonNull final String currencyName, @NonNull final Consumer<TransactionResult> result) {
		if (!Markets.getCurrencyManager().has(offerSender, currencyPlugin, currencyName, this.offeredAmount)) {
			categoryItem.finishStockReservation(this.requestAmount, true);
			deleteOffer(TransactionResult.FAILED_NO_MONEY, result);
			return;
		}

		if (!Markets.getCurrencyManager().withdraw(offerSender, currencyPlugin, currencyName, this.offeredAmount)) {
			categoryItem.finishStockReservation(this.requestAmount, true);
			ACCEPTING.remove(this.uuid);
			result.accept(TransactionResult.FAILED_NO_MONEY);
			return;
		}

		unStore(deleteResult -> {
			if (deleteResult != SynchronizeResult.SUCCESS) {
				Markets.getCurrencyManager().deposit(offerSender, currencyPlugin, currencyName, this.offeredAmount);
				categoryItem.finishStockReservation(this.requestAmount, true);
				ACCEPTING.remove(this.uuid);
				result.accept(TransactionResult.ERROR);
				return;
			}

			persistStockThenPay(categoryItem, () -> Markets.getCurrencyManager().deposit(offerSender, currencyPlugin, currencyName, this.offeredAmount), result, () -> {
				Markets.getCurrencyManager().deposit(itemOwner, currencyPlugin, currencyName, this.offeredAmount);
				giveBuyerItems(categoryItem);
			});
		});
	}

	private void acceptWithItemCurrency(@NonNull final CategoryItem categoryItem, @NonNull final OfflinePlayer itemOwner, @NonNull final Consumer<TransactionResult> result) {
		final int charge = (int) this.offeredAmount;
		if (charge <= 0) {
			unStore(deleteResult -> {
				if (deleteResult != SynchronizeResult.SUCCESS) {
					categoryItem.finishStockReservation(this.requestAmount, true);
					ACCEPTING.remove(this.uuid);
					result.accept(TransactionResult.ERROR);
					return;
				}

				persistStockThenPay(categoryItem, () -> {
				}, result, () -> giveBuyerItems(categoryItem));
			});
			return;
		}

		final BankEntry entry = Markets.getBankManager().getEntryByPlayer(this.sender, this.currencyItem);
		if (entry == null || entry.getQuantity() < charge) {
			categoryItem.finishStockReservation(this.requestAmount, true);
			deleteOffer(TransactionResult.FAILED_NO_MONEY, result);
			return;
		}

		final int previous = entry.getQuantity();
		entry.setQuantity(previous - charge);
		entry.sync(bankResult -> {
			if (bankResult != SynchronizeResult.SUCCESS) {
				entry.setQuantity(previous);
				categoryItem.finishStockReservation(this.requestAmount, true);
				ACCEPTING.remove(this.uuid);
				result.accept(TransactionResult.ERROR);
				return;
			}

			unStore(deleteResult -> {
				if (deleteResult != SynchronizeResult.SUCCESS) {
					entry.setQuantity(previous);
					entry.sync(null);
					categoryItem.finishStockReservation(this.requestAmount, true);
					ACCEPTING.remove(this.uuid);
					result.accept(TransactionResult.ERROR);
					return;
				}

				persistStockThenPay(categoryItem, () -> {
					entry.setQuantity(previous);
					entry.sync(null);
				}, result, () -> {
					if (entry.getQuantity() <= 0)
						entry.unStore(null);
					giveSellerItemsOrMakePayment(itemOwner);
					giveBuyerItems(categoryItem);
				});
			});
		});
	}

	private void persistStockThenPay(@NonNull final CategoryItem categoryItem, @NonNull final Runnable refund, @NonNull final Consumer<TransactionResult> result, @NonNull final Runnable pay) {
		categoryItem.sync(syncResult -> {
			if (syncResult != SynchronizeResult.SUCCESS) {
				categoryItem.finishStockReservation(this.requestAmount, true);
				refund.run();
				ACCEPTING.remove(this.uuid);
				result.accept(TransactionResult.ERROR);
				return;
			}

			pay.run();
			categoryItem.finishStockReservation(this.requestAmount, false);
			result.accept(TransactionResult.SUCCESS);
		});
	}

	private void deleteOffer(@NonNull final TransactionResult status, @NonNull final Consumer<TransactionResult> result) {
		unStore(deleteResult -> {
			if (deleteResult != SynchronizeResult.SUCCESS)
				ACCEPTING.remove(this.uuid);
			result.accept(status);
		});
	}

	private void giveBuyerItems(@NonNull final MarketItem marketItem) {
		Markets.getOfflineItemPaymentManager().create(
				this.sender,
				QuickItem.of(marketItem.getItem().clone()).amount(1).make(),
				this.requestAmount,
				TranslationManager.string(Translations.OFFER_ACCEPTED_PAYMENT), success -> {
				});
	}

	private void giveSellerItemsOrMakePayment(@NonNull final OfflinePlayer itemOwner) {
		Markets.getOfflineItemPaymentManager().create(
				itemOwner.getUniqueId(),
				QuickItem.of(this.currencyItem).amount(1).make(),
				(int) this.offeredAmount,
				TranslationManager.string(Translations.OFFER_ACCEPTED_PAYMENT), success -> {
				});
	}

	@Override
	public void reject(@NonNull BiConsumer<TransactionResult, OfferRejectReason> result) {
		final MarketItem locatedItem = Markets.getCategoryItemManager().getByUUID(this.marketItem);

		unStore(deleteResult -> {
			if (deleteResult == SynchronizeResult.SUCCESS) {
				result.accept(
						TransactionResult.SUCCESS,
						locatedItem == null ? OfferRejectReason.ITEM_NO_LONGER_AVAILABLE : locatedItem.getStock() < requestAmount ? OfferRejectReason.INSUFFICIENT_STOCK : OfferRejectReason.NOT_ACCEPTED
				);
			}
		});
	}

	@Override
	public void store(@NonNull Consumer<Offer> stored) {
		Markets.getDataManager().createOffer(this, (error, created) -> {
			if (error == null) {
				stored.accept(created);
			}
		});
	}

	@Override
	public void unStore(@Nullable Consumer<SynchronizeResult> syncResult) {
		Markets.getDataManager().deleteOffer(this, (error, updateStatus) -> {
			if (updateStatus) {
				Markets.getOfferManager().remove(this);
			}

			if (syncResult != null)
				syncResult.accept(error == null ? updateStatus ? SynchronizeResult.SUCCESS : SynchronizeResult.FAILURE : SynchronizeResult.FAILURE);
		});
	}
}
