package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.SearchResultOrder;
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
public final class SearchOrderAdminCommand {

	private static final String PATH_ORDER = "settings.search result order";

	public ReturnType execute(CommandSender sender, String... args) {
		if (!sender.hasPermission("markets.command.admin") && !sender.isOp()) {
			Common.tell(sender, TranslationManager.string(Translations.NO_PERMISSION));
			return ReturnType.FAIL;
		}

		if (args.length < 2) {
			Common.tell(sender, TranslationManager.string(Translations.SEARCH_ORDER_ADMIN_INFO,
					"search_order", SearchResultOrder.fromConfig().name()));
			return ReturnType.SUCCESS;
		}

		final String requested = args[1].trim().toLowerCase(Locale.ROOT);
		if (requested.equals("info")) {
			Common.tell(sender, TranslationManager.string(Translations.SEARCH_ORDER_ADMIN_INFO,
					"search_order", SearchResultOrder.fromConfig().name()));
			return ReturnType.SUCCESS;
		}

		final SearchResultOrder parsed = parseMode(args[1]);
		if (parsed == null) {
			Common.tell(sender, TranslationManager.string(Translations.SEARCH_ORDER_ADMIN_INVALID));
			Common.tell(sender, TranslationManager.string(Translations.SEARCH_ORDER_ADMIN_USAGE));
			return ReturnType.FAIL;
		}

		persist(PATH_ORDER, parsed.name());
		Markets.getMarketManager().rebuildSearchMarketOrder();
		Common.tell(sender, TranslationManager.string(Translations.SEARCH_ORDER_ADMIN_UPDATED,
				"search_order", parsed.name()));
		return ReturnType.SUCCESS;
	}

	private SearchResultOrder parseMode(final String value) {
		if (value == null || value.isBlank())
			return null;

		final String normalized = value.trim().toUpperCase(Locale.ROOT)
				.replace('-', '_')
				.replace(' ', '_');

		return switch (normalized) {
			case "DEFAULT" -> SearchResultOrder.DEFAULT;
			case "SHUFFLE" -> SearchResultOrder.SHUFFLE;
			case "SHUFFLE_ROUND_ROBIN", "SHUFFLEROUNDROBIN" -> SearchResultOrder.SHUFFLE_ROUND_ROBIN;
			default -> null;
		};
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
			Markets.getInstance().getLogger().log(Level.SEVERE, "Failed to save search result order config", exception);
		}
	}

	public List<String> tab(String... args) {
		if (args.length == 1 || args.length == 2)
			return List.of("info", "default", "shuffle", "shuffle_round_robin");

		return null;
	}
}
