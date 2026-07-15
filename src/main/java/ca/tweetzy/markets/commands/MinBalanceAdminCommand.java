package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

@UtilityClass
public final class MinBalanceAdminCommand {

	private static final String PATH_ENABLED = "settings.min balance.enabled";
	private static final String PATH_AMOUNT = "settings.min balance.amount";

	public ReturnType execute(CommandSender sender, String... args) {
		if (!sender.hasPermission("markets.admin.minbalance")) {
			Common.tell(sender, TranslationManager.string(Translations.NO_PERMISSION));
			return ReturnType.FAIL;
		}

		if (args.length < 2) {
			Common.tell(sender, TranslationManager.string(Translations.MIN_BALANCE_ADMIN_USAGE));
			return ReturnType.FAIL;
		}

		switch (args[1].toLowerCase(Locale.ROOT)) {
			case "info":
				return showInfo(sender);
			case "enabled":
				return setEnabled(sender, args);
			case "amount":
				return setAmount(sender, args);
			default:
				Common.tell(sender, TranslationManager.string(Translations.MIN_BALANCE_ADMIN_INVALID));
				return ReturnType.FAIL;
		}
	}

	private ReturnType showInfo(CommandSender sender) {
		Common.tell(sender, TranslationManager.list(Translations.MIN_BALANCE_ADMIN_INFO,
				"enabled", String.valueOf(Settings.MIN_BALANCE_ENABLED.getBoolean()),
				"amount", String.format("%,.0f", Settings.MIN_BALANCE_AMOUNT.getDouble())));
		return ReturnType.SUCCESS;
	}

	private ReturnType setEnabled(CommandSender sender, String[] args) {
		if (args.length < 3)
			return invalid(sender);

		final Boolean enabled = parseBoolean(args[2]);
		if (enabled == null)
			return invalid(sender);

		persist(PATH_ENABLED, enabled);
		Common.tell(sender, TranslationManager.string(Translations.MIN_BALANCE_ADMIN_UPDATED));
		return ReturnType.SUCCESS;
	}

	private ReturnType setAmount(CommandSender sender, String[] args) {
		if (args.length < 3)
			return invalid(sender);

		final Double value = parseDouble(args[2]);
		if (value == null || value < 0)
			return invalid(sender);

		persist(PATH_AMOUNT, value);
		Common.tell(sender, TranslationManager.string(Translations.MIN_BALANCE_ADMIN_UPDATED));
		return ReturnType.SUCCESS;
	}

	private void persist(@NonNull final String path, @NonNull final Object value) {
		final FileConfiguration config = getConfigFile();
		config.set(path, value);
		saveConfig(config);
		Settings.init();
	}

	private FileConfiguration getConfigFile() {
		final File file = new File(Markets.getInstance().getDataFolder(), "config.yml");
		if (!file.exists())
			Markets.getInstance().saveDefaultConfig();
		return YamlConfiguration.loadConfiguration(file);
	}

	private void saveConfig(@NonNull final FileConfiguration config) {
		try {
			config.save(new File(Markets.getInstance().getDataFolder(), "config.yml"));
		} catch (final IOException exception) {
			Markets.getInstance().getLogger().log(Level.SEVERE, "Failed to save min balance config", exception);
		}
	}

	private ReturnType invalid(CommandSender sender) {
		Common.tell(sender, TranslationManager.string(Translations.MIN_BALANCE_ADMIN_INVALID));
		return ReturnType.FAIL;
	}

	private Boolean parseBoolean(String value) {
		if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("yes"))
			return true;
		if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off") || value.equalsIgnoreCase("no"))
			return false;
		return null;
	}

	private Double parseDouble(String value) {
		try {
			return Double.parseDouble(value);
		} catch (final NumberFormatException exception) {
			return null;
		}
	}

	public List<String> tab(String... args) {
		if (args.length == 1 || args.length == 2)
			return List.of("info", "enabled", "amount");

		if (args.length == 3 && args[1].equalsIgnoreCase("enabled"))
			return List.of("true", "false");

		if (args.length == 3 && args[1].equalsIgnoreCase("amount"))
			return List.of("0", "100", "500", "1000");

		return null;
	}
}
