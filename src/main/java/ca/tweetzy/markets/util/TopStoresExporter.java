package ca.tweetzy.markets.util;

import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.StoreSortType;
import ca.tweetzy.markets.util.StoreLevelCalculator.StoreLevelSnapshot;
import ca.tweetzy.markets.util.StoreLevelCalculator.StoreTier;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class TopStoresExporter {

	private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
	private static final SimpleDateFormat HEADER_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private TopStoresExporter() {
	}

	public static void exportAsync(
			@NonNull final Player exporter,
			@NonNull final List<StoreLevelSnapshot> stores,
			@NonNull final StoreSortType sortType,
			@NonNull final Consumer<String> onSuccess,
			@NonNull final Consumer<String> onFailure
	) {
		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			try {
				final String fileName = "top-stores_" + FILE_DATE_FORMAT.format(new Date()) + ".txt";
				final File exportsDir = new File(Markets.getInstance().getDataFolder(), "exports");
				if (!exportsDir.exists() && !exportsDir.mkdirs()) {
					throw new IOException("Failed to create exports directory");
				}

				final File file = new File(exportsDir, fileName);
				writeExport(file, exporter, stores, sortType);

				Bukkit.getScheduler().runTask(Markets.getInstance(), () -> onSuccess.accept(fileName));
			} catch (final IOException exception) {
				Markets.getInstance().getLogger().severe("Failed to export top stores: " + exception.getMessage());
				Bukkit.getScheduler().runTask(Markets.getInstance(), () -> onFailure.accept(exception.getMessage()));
			}
		});
	}

	private static void writeExport(
			@NonNull final File file,
			@NonNull final Player exporter,
			@NonNull final List<StoreLevelSnapshot> stores,
			@NonNull final StoreSortType sortType
	) throws IOException {
		try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
			writer.println("Markets Top Stores Export");
			writer.println("Generated: " + HEADER_DATE_FORMAT.format(new Date()));
			writer.println("Exported by: " + exporter.getName());
			writer.println("Total stores: " + stores.size());
			writer.println("Sort: " + sortType.name());
			writer.println();

			writer.println("=== Tier Summary ===");
			final Map<StoreTier, Integer> tierCounts = StoreLevelCalculator.countByTier(stores);
			for (final Map.Entry<StoreTier, Integer> entry : tierCounts.entrySet()) {
				writer.println(entry.getKey().getPlainName() + ": " + entry.getValue());
			}
			writer.println();

			writer.println("=== All Stores ===");
			for (int i = 0; i < stores.size(); i++) {
				final StoreLevelSnapshot snapshot = stores.get(i);
				final StoreTier tier = StoreTier.fromLevel(snapshot.level());

				writer.printf(
						"#%d | %s | Owner: %s | Level: %d | Tier: %s | Sales: %d | Listings: %d | Avg Rating: %.1f | Customers: %d | Reviews: %d%n",
						i + 1,
						snapshot.market().getDisplayName(),
						snapshot.market().getOwnerName(),
						snapshot.level(),
						tier.getPlainName(),
						snapshot.totalSales(),
						snapshot.totalListings(),
						snapshot.avgRating(),
						snapshot.totalCustomers(),
						snapshot.totalReviews()
				);
			}
		}
	}

}
