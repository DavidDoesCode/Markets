package ca.tweetzy.markets.util;

import ca.tweetzy.markets.api.market.Transaction;
import lombok.Getter;
import lombok.NonNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MarketSalesPeriodStatsCalculator {

	private MarketSalesPeriodStatsCalculator() {
	}

	public static @NonNull SalesPeriodSnapshot calculate(@NonNull final List<Transaction> transactions) {
		final ZoneId zone = ZoneId.systemDefault();
		final LocalDate today = LocalDate.now(zone);
		final long endExclusive = today.atStartOfDay(zone).toInstant().toEpochMilli();
		final long yesterdayStart = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
		final long sevenDayStart = today.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli();
		final long thirtyDayStart = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli();
		final long priorSevenDayStart = today.minusDays(14).atStartOfDay(zone).toInstant().toEpochMilli();
		final long priorSevenDayEnd = sevenDayStart;

		final Map<UUID, Long> firstPurchaseByBuyer = new HashMap<>();
		for (final Transaction transaction : transactions) {
			firstPurchaseByBuyer.merge(transaction.getBuyer(), transaction.getTimeCreated(), Math::min);
		}

		final WindowAccumulator yesterday = new WindowAccumulator();
		final WindowAccumulator sevenDay = new WindowAccumulator(true);
		final WindowAccumulator thirtyDay = new WindowAccumulator();
		final WindowAccumulator priorSevenDay = new WindowAccumulator();

		for (final Transaction transaction : transactions) {
			final long createdAt = transaction.getTimeCreated();
			if (createdAt >= endExclusive) continue;

			if (createdAt >= yesterdayStart) {
				yesterday.accept(transaction);
			}
			if (createdAt >= sevenDayStart) {
				sevenDay.accept(transaction);
			}
			if (createdAt >= thirtyDayStart) {
				thirtyDay.accept(transaction);
			}
			if (createdAt >= priorSevenDayStart && createdAt < priorSevenDayEnd) {
				priorSevenDay.accept(transaction);
			}
		}

		final int repeatBuyers = (int) sevenDay.getBuyerOrderCounts().values().stream().filter(count -> count > 1).count();
		final int newBuyers = (int) sevenDay.getBuyers().stream()
				.filter(buyer -> {
					final Long firstPurchase = firstPurchaseByBuyer.get(buyer);
					return firstPurchase != null && firstPurchase >= sevenDayStart && firstPurchase < endExclusive;
				})
				.count();

		final double currentRevenue = sevenDay.getRevenue();
		final double previousRevenue = priorSevenDay.getRevenue();
		final double changePercent = previousRevenue > 0
				? ((currentRevenue - previousRevenue) / previousRevenue) * 100.0
				: (currentRevenue > 0 ? 100.0 : 0.0);
		final String changeDirection = currentRevenue > previousRevenue
				? "Up"
				: currentRevenue < previousRevenue ? "Down" : "Flat";

		return new SalesPeriodSnapshot(
				yesterday.toPeriodStats(),
				sevenDay.toPeriodStats(),
				thirtyDay.toPeriodStats(),
				sevenDay.getUniqueBuyers(),
				repeatBuyers,
				newBuyers,
				sevenDay.getAvgOrderValue(),
				sevenDay.getMedianOrderValue(),
				sevenDay.getLargestOrder(),
				currentRevenue,
				previousRevenue,
				changePercent,
				changeDirection
		);
	}

	@Getter
	public static final class PeriodStats {
		private final int orders;
		private final int itemsSold;
		private final double revenue;
		private final int uniqueBuyers;

		public PeriodStats(final int orders, final int itemsSold, final double revenue, final int uniqueBuyers) {
			this.orders = orders;
			this.itemsSold = itemsSold;
			this.revenue = revenue;
			this.uniqueBuyers = uniqueBuyers;
		}

		public double getAvgOrderValue() {
			return orders > 0 ? revenue / orders : 0;
		}
	}

	@Getter
	public static final class SalesPeriodSnapshot {
		private final PeriodStats yesterday;
		private final PeriodStats sevenDay;
		private final PeriodStats thirtyDay;
		private final int sevenDayUniqueBuyers;
		private final int sevenDayRepeatBuyers;
		private final int sevenDayNewBuyers;
		private final double sevenDayAvgOrderValue;
		private final double sevenDayMedianOrderValue;
		private final double sevenDayLargestOrder;
		private final double currentSevenDayRevenue;
		private final double previousSevenDayRevenue;
		private final double changePercent;
		private final String changeDirection;

		public SalesPeriodSnapshot(
				@NonNull final PeriodStats yesterday,
				@NonNull final PeriodStats sevenDay,
				@NonNull final PeriodStats thirtyDay,
				final int sevenDayUniqueBuyers,
				final int sevenDayRepeatBuyers,
				final int sevenDayNewBuyers,
				final double sevenDayAvgOrderValue,
				final double sevenDayMedianOrderValue,
				final double sevenDayLargestOrder,
				final double currentSevenDayRevenue,
				final double previousSevenDayRevenue,
				final double changePercent,
				@NonNull final String changeDirection
		) {
			this.yesterday = yesterday;
			this.sevenDay = sevenDay;
			this.thirtyDay = thirtyDay;
			this.sevenDayUniqueBuyers = sevenDayUniqueBuyers;
			this.sevenDayRepeatBuyers = sevenDayRepeatBuyers;
			this.sevenDayNewBuyers = sevenDayNewBuyers;
			this.sevenDayAvgOrderValue = sevenDayAvgOrderValue;
			this.sevenDayMedianOrderValue = sevenDayMedianOrderValue;
			this.sevenDayLargestOrder = sevenDayLargestOrder;
			this.currentSevenDayRevenue = currentSevenDayRevenue;
			this.previousSevenDayRevenue = previousSevenDayRevenue;
			this.changePercent = changePercent;
			this.changeDirection = changeDirection;
		}
	}

	private static final class WindowAccumulator {
		private final boolean trackOrderPrices;
		private int orders;
		private int itemsSold;
		private double revenue;
		private double largestOrder;
		private final Set<UUID> buyers = new HashSet<>();
		private final Map<UUID, Integer> buyerOrderCounts = new HashMap<>();
		private final List<Double> orderPrices = new ArrayList<>();

		private WindowAccumulator() {
			this(false);
		}

		private WindowAccumulator(final boolean trackOrderPrices) {
			this.trackOrderPrices = trackOrderPrices;
		}

		private void accept(@NonNull final Transaction transaction) {
			this.orders++;
			this.itemsSold += transaction.getQuantity();
			this.revenue += transaction.getPrice();
			this.largestOrder = Math.max(this.largestOrder, transaction.getPrice());
			this.buyers.add(transaction.getBuyer());
			this.buyerOrderCounts.merge(transaction.getBuyer(), 1, Integer::sum);
			if (this.trackOrderPrices) {
				this.orderPrices.add(transaction.getPrice());
			}
		}

		private int getUniqueBuyers() {
			return this.buyers.size();
		}

		private double getAvgOrderValue() {
			return this.orders > 0 ? this.revenue / this.orders : 0;
		}

		private double getMedianOrderValue() {
			if (this.orderPrices.isEmpty()) return 0;
			final List<Double> sorted = new ArrayList<>(this.orderPrices);
			Collections.sort(sorted);
			final int middle = sorted.size() / 2;
			if (sorted.size() % 2 == 0) {
				return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
			}
			return sorted.get(middle);
		}

		private PeriodStats toPeriodStats() {
			return new PeriodStats(this.orders, this.itemsSold, this.revenue, this.buyers.size());
		}
	}
}
