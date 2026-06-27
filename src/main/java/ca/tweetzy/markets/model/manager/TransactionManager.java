package ca.tweetzy.markets.model.manager;

import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.manager.ListManager;
import ca.tweetzy.markets.api.market.Transaction;
import ca.tweetzy.markets.api.market.core.MarketUser;
import lombok.NonNull;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class TransactionManager extends ListManager<Transaction> {

	private final Map<UUID, List<Transaction>> salesBySeller = new HashMap<>();
	private final Map<UUID, List<Transaction>> purchasesByBuyer = new HashMap<>();

	public TransactionManager() {
		super("Transaction");
	}

	@Override
	public void add(@NonNull final Transaction transaction) {
		synchronized (this.managerContent) {
			if (this.managerContent.contains(transaction)) return;
			this.managerContent.add(transaction);
			indexTransaction(transaction);
		}
	}

	@Override
	public void clear() {
		synchronized (this.managerContent) {
			this.managerContent.clear();
			this.salesBySeller.clear();
			this.purchasesByBuyer.clear();
		}
	}

	// ==================== ASYNC METHODS (RECOMMENDED) ====================

	public void getSalesTransactionsForAsync(@NonNull final UUID sellerUUID, @NonNull final Consumer<List<Transaction>> callback) {
		runFilterAsync(callback, () -> copySalesFor(sellerUUID), "getSalesTransactionsForAsync", sellerUUID);
	}

	public void getPurchaseTransactionsForAsync(@NonNull final UUID buyerUUID, @NonNull final Consumer<List<Transaction>> callback) {
		runFilterAsync(callback, () -> copyPurchasesFor(buyerUUID), "getPurchaseTransactionsForAsync", buyerUUID);
	}

	public void getTransactionsForAsync(@NonNull final UUID playerUUID, @NonNull final Consumer<List<Transaction>> callback) {
		runFilterAsync(callback, () -> copyTransactionsFor(playerUUID), "getTransactionsForAsync", playerUUID);
	}

	public void getAllTransactionsForAsync(@NonNull final Consumer<List<Transaction>> callback) {
		runFilterAsync(callback, this::copyAllTransactions, "getAllTransactionsForAsync", null);
	}

	public void getOfflineTransactionsForAsync(@NonNull final UUID sellerUUID, @NonNull final Consumer<List<Transaction>> callback) {
		final MarketUser user = Markets.getPlayerManager().get(sellerUUID);
		if (user == null) {
			runFilterAsync(callback, List::of, "getOfflineTransactionsForAsync", sellerUUID);
			return;
		}

		final long lastSeenAt = user.getLastSeenAt();
		runFilterAsync(callback, () -> copyOfflineSalesFor(sellerUUID, lastSeenAt), "getOfflineTransactionsForAsync", sellerUUID);
	}

	// ==================== SYNC METHODS (DEPRECATED, KEPT FOR COMPATIBILITY) ====================

	public int getTransactionsMadeToMarket(@NonNull final UUID sellerUUID, @NonNull final UUID buyerUUID) {
		synchronized (this.managerContent) {
			final List<Transaction> sales = this.salesBySeller.get(sellerUUID);
			if (sales == null || sales.isEmpty()) return 0;
			return (int) sales.stream().filter(transaction -> transaction.getBuyer().equals(buyerUUID)).count();
		}
	}

	@Deprecated
	public List<Transaction> getOfflineTransactionsFor(@NonNull final UUID sellerUUID) {
		final MarketUser user = Markets.getPlayerManager().get(sellerUUID);
		if (user == null) return List.of();

		return copyOfflineSalesFor(sellerUUID, user.getLastSeenAt());
	}

	@Deprecated
	public List<Transaction> getTransactionsFor(@NonNull final UUID playerUUID) {
		return copyTransactionsFor(playerUUID);
	}

	@Deprecated
	public List<Transaction> getSalesTransactionsFor(@NonNull final UUID sellerUUID) {
		return copySalesFor(sellerUUID);
	}

	@Deprecated
	public List<Transaction> getPurchaseTransactionsFor(@NonNull final UUID buyerUUID) {
		return copyPurchasesFor(buyerUUID);
	}

	@Override
	public void load() {
		Markets.getDataManager().getTransactions((error, found) -> {
			if (error != null) {
				Common.log("&cFailed to load transactions: " + error.getMessage());
				return;
			}

			synchronized (this.managerContent) {
				this.managerContent.clear();
				this.salesBySeller.clear();
				this.purchasesByBuyer.clear();
				found.forEach(transaction -> {
					this.managerContent.add(transaction);
					indexTransaction(transaction);
				});
			}

			Common.log("&aLoaded " + found.size() + " transactions");
		});
	}

	private void indexTransaction(@NonNull final Transaction transaction) {
		this.salesBySeller.computeIfAbsent(transaction.getSeller(), ignored -> new ArrayList<>()).add(transaction);
		this.purchasesByBuyer.computeIfAbsent(transaction.getBuyer(), ignored -> new ArrayList<>()).add(transaction);
	}

	private List<Transaction> copySalesFor(@NonNull final UUID sellerUUID) {
		synchronized (this.managerContent) {
			final List<Transaction> sales = this.salesBySeller.get(sellerUUID);
			if (sales == null || sales.isEmpty()) return List.of();
			return List.copyOf(sales);
		}
	}

	private List<Transaction> copyPurchasesFor(@NonNull final UUID buyerUUID) {
		synchronized (this.managerContent) {
			final List<Transaction> purchases = this.purchasesByBuyer.get(buyerUUID);
			if (purchases == null || purchases.isEmpty()) return List.of();
			return List.copyOf(purchases);
		}
	}

	private List<Transaction> copyTransactionsFor(@NonNull final UUID playerUUID) {
		synchronized (this.managerContent) {
			final List<Transaction> sales = this.salesBySeller.get(playerUUID);
			final List<Transaction> purchases = this.purchasesByBuyer.get(playerUUID);
			if ((sales == null || sales.isEmpty()) && (purchases == null || purchases.isEmpty())) return List.of();
			if (purchases == null || purchases.isEmpty()) return List.copyOf(sales);
			if (sales == null || sales.isEmpty()) return List.copyOf(purchases);

			final Map<UUID, Transaction> combined = new LinkedHashMap<>();
			for (final Transaction transaction : sales) combined.put(transaction.getId(), transaction);
			for (final Transaction transaction : purchases) combined.put(transaction.getId(), transaction);
			return List.copyOf(combined.values());
		}
	}

	private List<Transaction> copyAllTransactions() {
		synchronized (this.managerContent) {
			if (this.managerContent.isEmpty()) return List.of();
			return List.copyOf(this.managerContent);
		}
	}

	private List<Transaction> copyOfflineSalesFor(@NonNull final UUID sellerUUID, final long lastSeenAt) {
		synchronized (this.managerContent) {
			final List<Transaction> sales = this.salesBySeller.get(sellerUUID);
			if (sales == null || sales.isEmpty()) return List.of();
			return sales.stream()
					.filter(transaction -> transaction.getTimeCreated() >= lastSeenAt)
					.collect(Collectors.toList());
		}
	}

	private void runFilterAsync(@NonNull final Consumer<List<Transaction>> callback, @NonNull final Supplier<List<Transaction>> filter, @NonNull final String context, UUID playerUUID) {
		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			List<Transaction> transactions;
			try {
				transactions = filter.get();
			} catch (final Exception exception) {
				Common.log("&cFailed to filter transactions (" + context + "): " + exception.getMessage());
				exception.printStackTrace();
				transactions = List.of();
			}

			logUnexpectedEmptyResult(context, playerUUID, transactions.size());

			final List<Transaction> result = transactions;
			Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(result));
		});
	}

	private void runFilterAsync(@NonNull final Consumer<List<Transaction>> callback, @NonNull final List<Transaction> transactions, @NonNull final String context, UUID playerUUID) {
		runFilterAsync(callback, () -> transactions, context, playerUUID);
	}

	private void logUnexpectedEmptyResult(@NonNull final String context, UUID playerUUID, final int resultSize) {
		if (resultSize > 0) return;

		synchronized (this.managerContent) {
			if (this.managerContent.isEmpty()) return;
			final String playerInfo = playerUUID != null ? playerUUID.toString() : "all";
			Common.log("&e[Markets] " + context + " returned empty for " + playerInfo + " while cache contains " + this.managerContent.size() + " transactions");
		}
	}
}
