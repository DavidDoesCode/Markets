package ca.tweetzy.markets.model;

import ca.tweetzy.flight.database.DatabaseConnector;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Handles automatic daily database backups at a configured time.
 * Supports both MySQL (via mysqldump) and SQLite (via VACUUM INTO) databases.
 */
public final class DatabaseBackupTask {

	private BukkitTask scheduledTask;
	private final Markets plugin;
	private final DatabaseConnector databaseConnector;

	public DatabaseBackupTask(@NonNull final Markets plugin, @NonNull final DatabaseConnector databaseConnector) {
		this.plugin = plugin;
		this.databaseConnector = databaseConnector;
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

				// Create a "latest" copy for easy access
				final Path latestFile = backupDir.resolve("markets-latest.sql.gz");
				try {
					Files.copy(backupFile, latestFile, StandardCopyOption.REPLACE_EXISTING);
					Common.log("&aLatest backup copy updated: " + latestFile.getFileName());
				} catch (final IOException e) {
					Common.log("&cFailed to create latest backup copy: " + e.getMessage());
				}

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
	 * Backs up SQLite database using VACUUM INTO command.
	 * This is safer than file copying as it ensures a consistent snapshot.
	 *
	 * @param backupDir Directory to save backup
	 * @return true if successful
	 */
	private boolean backupSQLite(@NonNull final Path backupDir) {
		final Path backupFile = backupDir.resolve(getBackupFileName("db"));

		try {
			// Use VACUUM INTO to create a safe, consistent backup
			// This is much safer than copying files which can result in corruption
			this.databaseConnector.connect(connection -> {
				try (final Statement statement = connection.createStatement()) {
					// VACUUM INTO creates a complete backup in a single transaction
					final String sql = "VACUUM INTO '" + backupFile.toString().replace("'", "''") + "'";
					statement.execute(sql);
					Common.log("&aSQLite backup saved to: " + backupFile.getFileName());
				} catch (final SQLException e) {
					Common.log("&cSQLite backup error: " + e.getMessage());
					e.printStackTrace();
				}
			});

			// Verify backup was created
			if (Files.exists(backupFile)) {
				// Create a "latest" copy for easy access
				final Path latestFile = backupDir.resolve("markets-latest.db");
				try {
					Files.copy(backupFile, latestFile, StandardCopyOption.REPLACE_EXISTING);
					Common.log("&aLatest backup copy updated: " + latestFile.getFileName());
				} catch (final IOException e) {
					Common.log("&cFailed to create latest backup copy: " + e.getMessage());
				}
				return true;
			} else {
				Common.log("&cSQLite backup file was not created");
				return false;
			}

		} catch (final Exception e) {
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
