package ca.tweetzy.markets.model;

import ca.tweetzy.markets.Markets;
import lombok.NonNull;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class AdminActionLogger {

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final File LOG_FILE = new File(Markets.getInstance().getDataFolder(), "admin-actions.txt");

	public static void log(@NonNull final String adminName, @NonNull final String action) {
		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			try {
				// Create parent directory if it doesn't exist
				if (!LOG_FILE.getParentFile().exists()) {
					LOG_FILE.getParentFile().mkdirs();
				}

				// Create file if it doesn't exist
				if (!LOG_FILE.exists()) {
					LOG_FILE.createNewFile();
				}

				// Append to file
				try (FileWriter fw = new FileWriter(LOG_FILE, true);
				     PrintWriter pw = new PrintWriter(fw)) {

					final String timestamp = DATE_FORMAT.format(new Date());
					final String logEntry = String.format("[%s] %s: %s", timestamp, adminName, action);

					pw.println(logEntry);
				}
			} catch (IOException e) {
				Bukkit.getLogger().severe("Failed to write to admin-actions.txt: " + e.getMessage());
				e.printStackTrace();
			}
		});
	}
}
