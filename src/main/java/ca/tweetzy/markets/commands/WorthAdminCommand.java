package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.stream.Collectors;

@UtilityClass
public final class WorthAdminCommand {

	private static final String PATH_ENABLED = "settings.worth.price limit.enabled";
	private static final String PATH_MAX_PERCENT = "settings.worth.price limit.max percent";
	private static final String PATH_ABSOLUTE_MAX = "settings.worth.price limit.absolute max";
	private static final String PATH_EXCLUDED = "settings.worth.price limit.excluded materials";

	public ReturnType execute(CommandSender sender, String... args) {
		if (!sender.hasPermission("markets.admin.worth")) {
			Common.tell(sender, TranslationManager.string(Translations.NO_PERMISSION));
			return ReturnType.FAIL;
		}

		if (args.length < 2) {
			Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_USAGE));
			return ReturnType.FAIL;
		}

		switch (args[1].toLowerCase(Locale.ROOT)) {
			case "info":
				return showInfo(sender);
			case "enabled":
				return setEnabled(sender, args);
			case "maxpercent":
				return setMaxPercent(sender, args);
			case "absolutemax":
				return setAbsoluteMax(sender, args);
			case "exclude":
				return handleExclude(sender, args);
			default:
				Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_INVALID));
				return ReturnType.FAIL;
		}
	}

	private ReturnType showInfo(CommandSender sender) {
		final List<String> excluded = getExcludedMaterials();
		final String excludedDisplay = excluded.isEmpty()
				? "none"
				: String.join(", ", excluded);

		Common.tell(sender, TranslationManager.list(Translations.WORTH_ADMIN_INFO,
				"enabled", String.valueOf(Settings.WORTH_PRICE_LIMIT_ENABLED.getBoolean()),
				"max_percent", String.format("%,.0f", Settings.WORTH_PRICE_LIMIT_MAX_PERCENT.getDouble()),
				"absolute_max", String.format("%,.2f", Settings.WORTH_PRICE_LIMIT_ABSOLUTE_MAX.getDouble()),
				"excluded_materials", excludedDisplay));
		return ReturnType.SUCCESS;
	}

	private ReturnType setEnabled(CommandSender sender, String[] args) {
		if (args.length < 3)
			return invalid(sender);

		final Boolean enabled = parseBoolean(args[2]);
		if (enabled == null)
			return invalid(sender);

		persist(PATH_ENABLED, enabled);
		Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_UPDATED));
		return ReturnType.SUCCESS;
	}

	private ReturnType setMaxPercent(CommandSender sender, String[] args) {
		if (args.length < 3)
			return invalid(sender);

		final Double value = parseDouble(args[2]);
		if (value == null || value <= 0)
			return invalid(sender);

		persist(PATH_MAX_PERCENT, value);
		Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_UPDATED));
		return ReturnType.SUCCESS;
	}

	private ReturnType setAbsoluteMax(CommandSender sender, String[] args) {
		if (args.length < 3)
			return invalid(sender);

		final Double value = parseDouble(args[2]);
		if (value == null || value <= 0)
			return invalid(sender);

		persist(PATH_ABSOLUTE_MAX, value);
		Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_UPDATED));
		return ReturnType.SUCCESS;
	}

	private ReturnType handleExclude(CommandSender sender, String[] args) {
		if (args.length < 3) {
			Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_EXCLUDE_USAGE));
			return ReturnType.FAIL;
		}

		switch (args[2].toLowerCase(Locale.ROOT)) {
			case "list":
				return listExcluded(sender);
			case "add":
				return addExcluded(sender, args);
			case "remove":
				return removeExcluded(sender, args);
			default:
				Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_EXCLUDE_USAGE));
				return ReturnType.FAIL;
		}
	}

	private List<String> getExcludedMaterials() {
		final List<String> excluded = Settings.WORTH_PRICE_LIMIT_EXCLUDED_MATERIALS.getStringList();
		return excluded == null ? new ArrayList<>() : new ArrayList<>(excluded);
	}

	private ReturnType listExcluded(CommandSender sender) {
		final List<String> excluded = getExcludedMaterials();
		final String excludedDisplay = excluded.isEmpty() ? "none" : String.join(", ", excluded);
		Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_EXCLUDE_LIST,
				"excluded_materials", excludedDisplay));
		return ReturnType.SUCCESS;
	}

	private ReturnType addExcluded(CommandSender sender, String[] args) {
		if (args.length < 4)
			return invalid(sender);

		final Material material = Material.matchMaterial(args[3]);
		if (material == null || !material.isItem())
			return invalid(sender);

		final String materialName = material.name();
		final List<String> excluded = getExcludedMaterials();

		for (final String entry : excluded) {
			if (entry.equalsIgnoreCase(materialName)) {
				Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_EXCLUDE_EXISTS,
						"material", materialName));
				return ReturnType.FAIL;
			}
		}

		excluded.add(materialName);
		persist(PATH_EXCLUDED, excluded);
		Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_EXCLUDE_ADDED,
				"material", materialName));
		return ReturnType.SUCCESS;
	}

	private ReturnType removeExcluded(CommandSender sender, String[] args) {
		if (args.length < 4)
			return invalid(sender);

		final List<String> excluded = getExcludedMaterials();
		final String requested = args[3];

		final boolean removed = excluded.removeIf(entry -> entry.equalsIgnoreCase(requested));
		if (!removed) {
			Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_EXCLUDE_MISSING,
					"material", requested.toUpperCase(Locale.ROOT)));
			return ReturnType.FAIL;
		}

		persist(PATH_EXCLUDED, excluded);
		Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_EXCLUDE_REMOVED,
				"material", requested.toUpperCase(Locale.ROOT)));
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
			Markets.getInstance().getLogger().log(Level.SEVERE, "Failed to save worth price limit config", exception);
		}
	}

	private ReturnType invalid(CommandSender sender) {
		Common.tell(sender, TranslationManager.string(Translations.WORTH_ADMIN_INVALID));
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
			return List.of("info", "enabled", "maxpercent", "absolutemax", "exclude");

		if (args.length == 3 && args[1].equalsIgnoreCase("enabled"))
			return List.of("true", "false");

		if (args.length == 3 && args[1].equalsIgnoreCase("maxpercent"))
			return List.of("1000", "2500", "5000");

		if (args.length == 3 && args[1].equalsIgnoreCase("absolutemax"))
			return List.of("10000", "100000", "1000000");

		if (args.length == 3 && args[1].equalsIgnoreCase("exclude"))
			return List.of("list", "add", "remove");

		if (args.length == 4 && args[1].equalsIgnoreCase("exclude") && args[2].equalsIgnoreCase("add")) {
			return Arrays.stream(Material.values())
					.filter(Material::isItem)
					.map(Material::name)
					.collect(Collectors.toList());
		}

		if (args.length == 4 && args[1].equalsIgnoreCase("exclude") && args[2].equalsIgnoreCase("remove")) {
			return getExcludedMaterials();
		}

		return null;
	}
}
