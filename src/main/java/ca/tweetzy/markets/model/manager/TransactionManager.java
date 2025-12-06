package ca.tweetzy.markets.model.manager;

import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.manager.ListManager;
import ca.tweetzy.markets.api.market.Transaction;
import ca.tweetzy.markets.api.market.core.MarketUser;
import lombok.NonNull;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class TransactionManager extends ListManager<Transaction> {

	public TransactionManager() {
		super("Transaction");
	}

	// ==================== ASYNC METHODS (RECOMMENDED) ====================

	/**
	 * Async: Get sales transactions (player is seller)
	 * Filtering happens on async thread, result delivered via callback
	 *
	 * @param sellerUUID The seller's UUID
	 * @param callback Consumer that receives the filtered list
	 */
	public void getSalesTransactionsForAsync(@NonNull final UUID sellerUUID, @NonNull final Consumer<List<Transaction>> callback) {
		// Run filtering on async thread
		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			// Perform filtering off main thread
			final List<Transaction> transactions = getManagerContent().stream()
					.filter(transaction -> transaction.getSeller().equals(sellerUUID))
					.collect(Collectors.toList());

			// Deliver result back on main thread (required for GUI updates)
			Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(transactions));
		});
	}

	/**
	 * Async: Get purchase transactions (player is buyer)
	 * Filtering happens on async thread, result delivered via callback
	 *
	 * @param buyerUUID The buyer's UUID
	 * @param callback Consumer that receives the filtered list
	 */
	public void getPurchaseTransactionsForAsync(@NonNull final UUID buyerUUID, @NonNull final Consumer<List<Transaction>> callback) {
		// Run filtering on async thread
		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			// Perform filtering off main thread
			final List<Transaction> transactions = getManagerContent().stream()
					.filter(transaction -> transaction.getBuyer().equals(buyerUUID))
					.collect(Collectors.toList());

			// Deliver result back on main thread (required for GUI updates)
			Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(transactions));
		});
	}

	/**
	 * Async: Get all transactions for player (buyer OR seller)
	 * Filtering happens on async thread, result delivered via callback
	 *
	 * @param playerUUID The player's UUID
	 * @param callback Consumer that receives the filtered list
	 */
	public void getTransactionsForAsync(@NonNull final UUID playerUUID, @NonNull final Consumer<List<Transaction>> callback) {
		// Run filtering on async thread
		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			// Perform filtering off main thread
			final List<Transaction> transactions = getManagerContent().stream()
					.filter(transaction -> transaction.getSeller().equals(playerUUID) || transaction.getBuyer().equals(playerUUID))
					.collect(Collectors.toList());

			// Deliver result back on main thread (required for GUI updates)
			Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(transactions));
		});
	}

	/**
	 * Async: Get offline transactions (since last seen)
	 * Filtering happens on async thread, result delivered via callback
	 *
	 * @param sellerUUID The seller's UUID
	 * @param callback Consumer that receives the filtered list
	 */
	public void getOfflineTransactionsForAsync(@NonNull final UUID sellerUUID, @NonNull final Consumer<List<Transaction>> callback) {
		final MarketUser user = Markets.getPlayerManager().get(sellerUUID);
		if (user == null) {
			callback.accept(List.of());
			return;
		}

		// Run filtering on async thread
		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			// Perform filtering off main thread
			final List<Transaction> transactions = getManagerContent().stream()
					.filter(transaction -> transaction.getSeller().equals(sellerUUID) && transaction.getTimeCreated() >= user.getLastSeenAt())
					.collect(Collectors.toList());

			// Deliver result back on main thread (required for GUI updates)
			Bukkit.getScheduler().runTask(Markets.getInstance(), () -> callback.accept(transactions));
		});
	}

	// ==================== SYNC METHODS (DEPRECATED, KEPT FOR COMPATIBILITY) ====================

	public int getTransactionsMadeToMarket(@NonNull final UUID sellerUUID, @NonNull final UUID buyerUUID) {
		return (int) this.managerContent.stream().filter(transaction -> transaction.getBuyer().equals(buyerUUID) && transaction.getSeller().equals(sellerUUID)).count();
	}

	/**
	 * @deprecated Use {@link #getOfflineTransactionsForAsync} instead
	 * Synchronous filtering blocks the calling thread
	 */
	@Deprecated
	public List<Transaction> getOfflineTransactionsFor(@NonNull final UUID sellerUUID) {
		final MarketUser user = Markets.getPlayerManager().get(sellerUUID);

		return getManagerContent().stream().filter(transaction -> transaction.getSeller().equals(sellerUUID) && transaction.getTimeCreated() >= user.getLastSeenAt()).collect(Collectors.toList());
	}

	/**
	 * @deprecated Use {@link #getTransactionsForAsync} instead
	 * Synchronous filtering blocks the calling thread
	 */
	@Deprecated
	public List<Transaction> getTransactionsFor(@NonNull final UUID playerUUID) {
		return getManagerContent().stream().filter(transaction ->
				transaction.getSeller().equals(playerUUID) ||
						transaction.getBuyer().equals(playerUUID)).collect(Collectors.toList());
	}

	/**
	 * @deprecated Use {@link #getSalesTransactionsForAsync} instead
	 * Synchronous filtering blocks the calling thread
	 */
	@Deprecated
	public List<Transaction> getSalesTransactionsFor(@NonNull final UUID sellerUUID) {
		return getManagerContent().stream().filter(transaction -> transaction.getSeller().equals(sellerUUID)).collect(Collectors.toList());
	}

	/**
	 * @deprecated Use {@link #getPurchaseTransactionsForAsync} instead
	 * Synchronous filtering blocks the calling thread
	 */
	@Deprecated
	public List<Transaction> getPurchaseTransactionsFor(@NonNull final UUID buyerUUID) {
		return getManagerContent().stream().filter(transaction -> transaction.getBuyer().equals(buyerUUID)).collect(Collectors.toList());
	}

	@Override
	public void load() {
		clear();

		Markets.getDataManager().getTransactions((error, found) -> {
			if (error != null) return;
			found.forEach(this::add);
		});
	}
}
