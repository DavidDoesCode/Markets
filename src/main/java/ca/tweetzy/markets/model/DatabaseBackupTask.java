package ca.tweetzy.markets.model;

import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.settings.Settings;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Handles automatic daily database backups at a configured time.
 * Supports both MySQL (via mysqldump) and SQLite (file copy) databases.
 */
public final class DatabaseBackupTask {

	private BukkitTask scheduledTask;
	private final Markets plugin;

	public DatabaseBackupTask(@NonNull final Markets plugin) {
		this.plugin = plugin;
	}

	/**
	 * Starts the backup task scheduler.
	 * Calculates time until next backup and schedules accordingly.
	 */
	public void start() {
		if (!Settings.BACKUP_ENABLED.getBoolean()) {
			Common.log("&eDatabase backups are disabled in config");
			return;
		}

		// Calculate initial delay until target time
		final long delayTicks = calculateDelayUntilNextBackup();

		// Schedule first backup and subsequent daily backups
		// 20 ticks per second * 60 seconds * 60 minutes * 24 hours = 1728000 ticks per day
		final long dailyTicks = 20L * 60 * 60 * 24;

		this.scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
				this.plugin,
				this::performBackup,
				delayTicks,
				dailyTicks
		);

		final int targetHour = Settings.BACKUP_TIME_HOUR.getInt();
		Common.log("&aDatabase backup scheduled for &e" + targetHour + ":00&a daily");
		Common.log("&aNext backup in &e" + (delayTicks / 20 / 60) + "&a minutes");
	}

	/**
	 * Stops the backup task scheduler.
	 */
	public void stop() {
		if (this.scheduledTask != null) {
			this.scheduledTask.cancel();
			this.scheduledTask = null;
		}
	}

	/**
	 * Calculates the delay in ticks until the next scheduled backup time.
	 *
	 * @return Delay in ticks (20 ticks = 1 second)
	 */
	private long calculateDelayUntilNextBackup() {
		final int targetHour = Settings.BACKUP_TIME_HOUR.getInt();
		final LocalDateTime now = LocalDateTime.now();
		LocalDateTime nextRun = now.withHour(targetHour).withMinute(0).withSecond(0).withNano(0);

		// If target time has passed today, schedule for tomorrow
		if (now.isAfter(nextRun)) {
			nextRun = nextRun.plusDays(1);
		}

		final long secondsUntilRun = ChronoUnit.SECONDS.between(now, nextRun);
		return secondsUntilRun * 20L; // Convert to ticks
	}

	/**
	 * Performs the actual backup operation.
	 * Delegates to MySQL or SQLite backup based on configuration.
	 */
	private void performBackup() {
		try {
			Common.log("&eStarting database backup...");

			// Create backup directory
			final Path backupDir = getBackupDirectory();
			if (!Files.exists(backupDir)) {
				Files.createDirectories(backupDir);
			}

			// Perform backup based on database type
			final boolean success = Settings.DATABASE_USE.getBoolean()
					? backupMySQL(backupDir)
					: backupSQLite(backupDir);

			if (success) {
				Common.log("&aDatabase backup completed successfully");
				cleanOldBackups(backupDir);
			} else {
				Common.log("&cDatabase backup failed - check console for errors");
			}

		} catch (final Exception e) {
			Common.log("&cError during database backup: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Gets the backup directory path from config or creates default.
	 *
	 * @return Path to backup directory
	 */
	private Path getBackupDirectory() {
		final String folderPath = Settings.BACKUP_FOLDER_PATH.getString();
		final Path path = Paths.get(folderPath);

		// If relative path, resolve relative to plugin data folder
		if (!path.isAbsolute()) {
			return this.plugin.getDataFolder().toPath().resolve(folderPath);
		}

		return path;
	}

	/**
	 * Creates a timestamped filename for the backup.
	 *
	 * @param extension File extension (e.g., "sql.gz" or "db")
	 * @return Timestamped filename
	 */
	private String getBackupFileName(@NonNull final String extension) {
		final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
		return "markets_backup_" + dateFormat.format(new Date()) + "." + extension;
	}

	/**
	 * Backs up MySQL database using mysqldump command.
	 *
	 * @param backupDir Directory to save backup
	 * @return true if successful
	 */
	private boolean backupMySQL(@NonNull final Path backupDir) {
		final String host = Settings.DATABASE_HOST.getString();
		final int port = Settings.DATABASE_PORT.getInt();
		final String database = Settings.DATABASE_NAME.getString();
		final String username = Settings.DATABASE_USERNAME.getString();
		final String password = Settings.DATABASE_PASSWORD.getString();
		final String tablePrefix = Settings.DATABASE_TABLE_PREFIX.getString();

		final Path backupFile = backupDir.resolve(getBackupFileName("sql.gz"));

		try {
			// Build mysqldump command
			// Only backup tables with our prefix
			final ProcessBuilder processBuilder = new ProcessBuilder(
					"mysqldump",
					"-h", host,
					"-P", String.valueOf(port),
					"-u", username,
					"-p" + password,
					"--single-transaction",
					"--routines",
					"--triggers",
					database,
					"--tables"
			);

			// Add all tables with prefix (we'll do a simple export of entire DB for now)
			// Note: For production, you might want to filter tables by prefix
			processBuilder.redirectErrorStream(true);

			final Process process = processBuilder.start();

			// Compress output with GZIP
			try (final InputStream inputStream = process.getInputStream();
			     final FileOutputStream fileOut = new FileOutputStream(backupFile.toFile());
			     final GZIPOutputStream gzipOut = new GZIPOutputStream(fileOut)) {

				final byte[] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					gzipOut.write(buffer, 0, bytesRead);
				}
			}

			final int exitCode = process.waitFor();
			if (exitCode == 0) {
				Common.log("&aMySQL backup saved to: " + backupFile.getFileName());
				return true;
			} else {
				// Read error output
				try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
					String line;
					Common.log("&cMySQL backup failed with exit code: " + exitCode);
					while ((line = reader.readLine()) != null) {
						Common.log("&c" + line);
					}
				}
				return false;
			}

		} catch (final IOException e) {
			Common.log("&cMySQL backup error: " + e.getMessage());
			Common.log("&cEnsure 'mysqldump' is installed and accessible from PATH");
			e.printStackTrace();
			return false;
		} catch (final InterruptedException e) {
			Common.log("&cMySQL backup interrupted");
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Backs up SQLite database by copying the file.
	 *
	 * @param backupDir Directory to save backup
	 * @return true if successful
	 */
	private boolean backupSQLite(@NonNull final Path backupDir) {
		try {
			// SQLite database is stored in plugin data folder
			final Path sourceDb = this.plugin.getDataFolder().toPath().resolve("markets.db");

			if (!Files.exists(sourceDb)) {
				Common.log("&cSQLite database file not found: " + sourceDb);
				return false;
			}

			final Path backupFile = backupDir.resolve(getBackupFileName("db"));

			// Copy database file
			Files.copy(sourceDb, backupFile, StandardCopyOption.REPLACE_EXISTING);

			// Also backup WAL and SHM files if they exist (SQLite journal files)
			final Path sourceWal = this.plugin.getDataFolder().toPath().resolve("markets.db-wal");
			final Path sourceSHM = this.plugin.getDataFolder().toPath().resolve("markets.db-shm");

			if (Files.exists(sourceWal)) {
				Files.copy(sourceWal, backupDir.resolve(getBackupFileName("db-wal")), StandardCopyOption.REPLACE_EXISTING);
			}

			if (Files.exists(sourceSHM)) {
				Files.copy(sourceSHM, backupDir.resolve(getBackupFileName("db-shm")), StandardCopyOption.REPLACE_EXISTING);
			}

			Common.log("&aSQLite backup saved to: " + backupFile.getFileName());
			return true;

		} catch (final IOException e) {
			Common.log("&cSQLite backup error: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Deletes backups older than configured retention period.
	 *
	 * @param backupDir Directory containing backups
	 */
	private void cleanOldBackups(@NonNull final Path backupDir) {
		final int retentionDays = Settings.BACKUP_RETENTION_DAYS.getInt();

		if (retentionDays <= 0) {
			return; // Keep all backups
		}

		try {
			final long cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);

			try (final Stream<Path> files = Files.list(backupDir)) {
				files.filter(path -> path.getFileName().toString().startsWith("markets_backup_"))
						.filter(path -> {
							try {
								return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
							} catch (final IOException e) {
								return false;
							}
						})
						.forEach(path -> {
							try {
								Files.delete(path);
								Common.log("&eDeleted old backup: " + path.getFileName());
							} catch (final IOException e) {
								Common.log("&cFailed to delete old backup: " + path.getFileName());
							}
						});
			}

		} catch (final IOException e) {
			Common.log("&cError cleaning old backups: " + e.getMessage());
		}
	}

	/**
	 * Manually triggers a backup (useful for commands or reload).
	 */
	public void triggerManualBackup() {
		Bukkit.getScheduler().runTaskAsynchronously(this.plugin, this::performBackup);
	}
}
