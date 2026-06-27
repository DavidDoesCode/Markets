package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.model.manager.ShippingManager;
import ca.tweetzy.markets.model.shipping.ShippingWorldConfig;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.experimental.UtilityClass;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@UtilityClass
public final class ShippingAdminCommand {

	public ReturnType execute(CommandSender sender, String... args) {
		if (!sender.hasPermission("markets.admin.shipping")) {
			Common.tell(sender, TranslationManager.string(Translations.NO_PERMISSION));
			return ReturnType.FAIL;
		}

		if (args.length < 2) {
			Common.tell(sender, "&cUsage: /markets admin shipping <enabled|receiver|world> ...");
			return ReturnType.FAIL;
		}

		final ShippingManager manager = Markets.getShippingManager();

		switch (args[1].toLowerCase(Locale.ROOT)) {
			case "enabled":
				return setEnabled(sender, args, manager);
			case "receiver":
				return setReceiver(sender, args, manager);
			case "world":
				return handleWorld(sender, args, manager);
			default:
				Common.tell(sender, TranslationManager.string(Translations.SHIPPING_ADMIN_INVALID));
				return ReturnType.FAIL;
		}
	}

	private ReturnType setEnabled(CommandSender sender, String[] args, ShippingManager manager) {
		if (args.length < 3) return invalid(sender);
		final Boolean enabled = parseBoolean(args[2]);
		if (enabled == null) return invalid(sender);
		manager.setGlobalEnabled(enabled);
		Common.tell(sender, TranslationManager.string(Translations.SHIPPING_ADMIN_UPDATED));
		return ReturnType.SUCCESS;
	}

	private ReturnType setReceiver(CommandSender sender, String[] args, ShippingManager manager) {
		if (args.length < 3) return invalid(sender);
		manager.setReceiver(args[2]);
		Common.tell(sender, TranslationManager.string(Translations.SHIPPING_ADMIN_UPDATED));
		return ReturnType.SUCCESS;
	}

	private ReturnType handleWorld(CommandSender sender, String[] args, ShippingManager manager) {
		if (args.length < 3) return invalid(sender);

		switch (args[2].toLowerCase(Locale.ROOT)) {
			case "list":
				final String worlds = manager.getConfiguredWorlds().stream().sorted().collect(Collectors.joining(", "));
				Common.tell(sender, TranslationManager.string(Translations.SHIPPING_ADMIN_WORLD_LIST, "world_list", worlds.isEmpty() ? "none" : worlds));
				return ReturnType.SUCCESS;
			case "add":
				if (args.length < 4) return invalid(sender);
				if (manager.getWorldConfig(args[3]) != null) {
					Common.tell(sender, TranslationManager.string(Translations.SHIPPING_ADMIN_WORLD_EXISTS));
					return ReturnType.FAIL;
				}
				final ShippingWorldConfig added = ShippingWorldConfig.defaults(args[3]);
				manager.addWorld(added);
				Common.tell(sender, TranslationManager.string(Translations.SHIPPING_ADMIN_WORLD_ADDED, "world_name", args[3]));
				return ReturnType.SUCCESS;
			case "remove":
				if (args.length < 4) return invalid(sender);
				if (manager.getWorldConfig(args[3]) == null) {
					Common.tell(sender, TranslationManager.string(Translations.SHIPPING_ADMIN_WORLD_MISSING));
					return ReturnType.FAIL;
				}
				manager.removeWorld(args[3]);
				Common.tell(sender, TranslationManager.string(Translations.SHIPPING_ADMIN_WORLD_REMOVED, "world_name", args[3]));
				return ReturnType.SUCCESS;
			default:
				return updateWorldSetting(sender, args, manager);
		}
	}

	private ReturnType updateWorldSetting(CommandSender sender, String[] args, ShippingManager manager) {
		if (args.length < 5) return invalid(sender);

		final String worldName = args[2];
		final ShippingWorldConfig config = manager.getWorldConfig(worldName);
		if (config == null) {
			Common.tell(sender, TranslationManager.string(Translations.SHIPPING_ADMIN_WORLD_MISSING));
			return ReturnType.FAIL;
		}

		switch (args[3].toLowerCase(Locale.ROOT)) {
			case "base":
				final Double base = parseDouble(args[4]);
				if (base == null || base < 0) return invalid(sender);
				config.setBaseCharge(base);
				break;
			case "rate":
				final Double rate = parseDouble(args[4]);
				if (rate == null || rate < 0) return invalid(sender);
				config.setRatePerUnit(rate);
				break;
			case "unit":
				final Integer unit = parseInt(args[4]);
				if (unit == null || unit <= 0) return invalid(sender);
				config.setDistanceUnit(unit);
				break;
			case "freedistance":
				final Integer freeDistance = parseInt(args[4]);
				if (freeDistance == null || freeDistance < 0) return invalid(sender);
				config.setFreeDistance(freeDistance);
				break;
			case "region":
				if (args.length < 8) return invalid(sender);
				final Integer minX = parseInt(args[4]);
				final Integer minZ = parseInt(args[5]);
				final Integer maxX = parseInt(args[6]);
				final Integer maxZ = parseInt(args[7]);
				if (minX == null || minZ == null || maxX == null || maxZ == null) return invalid(sender);
				config.setRegionMinX(minX);
				config.setRegionMinZ(minZ);
				config.setRegionMaxX(maxX);
				config.setRegionMaxZ(maxZ);
				break;
			case "origin":
				if (args.length < 6) return invalid(sender);
				final Integer originX = parseInt(args[4]);
				final Integer originZ = parseInt(args[5]);
				if (originX == null || originZ == null) return invalid(sender);
				config.setOriginX(originX);
				config.setOriginZ(originZ);
				break;
			default:
				return invalid(sender);
		}

		manager.updateWorld(config);
		Common.tell(sender, TranslationManager.string(Translations.SHIPPING_ADMIN_UPDATED));
		return ReturnType.SUCCESS;
	}

	private ReturnType invalid(CommandSender sender) {
		Common.tell(sender, TranslationManager.string(Translations.SHIPPING_ADMIN_INVALID));
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

	private Integer parseInt(String value) {
		try {
			return Integer.parseInt(value);
		} catch (final NumberFormatException exception) {
			return null;
		}
	}

	public List<String> tab(String... args) {
		if (args.length == 1)
			return List.of("enabled", "receiver", "world");

		if (args.length == 2)
			return List.of("enabled", "receiver", "world");

		if (args.length == 3 && args[1].equalsIgnoreCase("enabled"))
			return List.of("true", "false");

		if (args.length == 3 && args[1].equalsIgnoreCase("world"))
			return List.of("list", "add", "remove");

		if (args.length >= 3 && args[1].equalsIgnoreCase("world") && !args[2].equalsIgnoreCase("list") && !args[2].equalsIgnoreCase("add") && !args[2].equalsIgnoreCase("remove")) {
			if (args.length == 4)
				return List.of("base", "rate", "unit", "freedistance", "region", "origin");
		}

		if (args.length == 4 && args[1].equalsIgnoreCase("world") && args[2].equalsIgnoreCase("add"))
			return new ArrayList<>();

		if (args.length == 4 && args[1].equalsIgnoreCase("world") && args[2].equalsIgnoreCase("remove"))
			return new ArrayList<>(Markets.getShippingManager().getConfiguredWorlds());

		return null;
	}
}
