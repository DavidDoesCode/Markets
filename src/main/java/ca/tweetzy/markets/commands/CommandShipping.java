package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.AllowedExecutor;
import ca.tweetzy.flight.command.Command;
import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.model.shipping.ShippingBreakdown;
import ca.tweetzy.markets.model.shipping.ShippingCalculator;
import ca.tweetzy.markets.model.shipping.ShippingMoney;
import ca.tweetzy.markets.model.shipping.ShippingWorldConfig;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class CommandShipping extends Command {

	public CommandShipping() {
		super(AllowedExecutor.PLAYER, Settings.CMD_ALIAS_SUB_SHIPPING.getStringList().toArray(new String[0]));
	}

	@Override
	protected ReturnType execute(CommandSender sender, String... args) {
		if (!(sender instanceof final Player player))
			return ReturnType.FAIL;

		Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_HEADER));

		if (Settings.SHIPPING_ENABLED.getBoolean())
			Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_ENABLED));
		else
			Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_DISABLED));

		Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_WORLD, "world_name", player.getWorld().getName()));

		final ShippingBreakdown breakdown = ShippingCalculator.calculate(player);

		if (breakdown.getNoChargeReason() == ShippingBreakdown.NoChargeReason.UNCONFIGURED_WORLD) {
			Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_UNCONFIGURED));
			Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_MARKET_WORLDS));
			return ReturnType.SUCCESS;
		}

		if (breakdown.getNoChargeReason() == ShippingBreakdown.NoChargeReason.DISABLED) {
			Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_MARKET_WORLDS));
			return ReturnType.SUCCESS;
		}

		final ShippingWorldConfig config = ca.tweetzy.markets.Markets.getShippingManager().getWorldConfig(player.getWorld().getName());
		if (config == null)
			return ReturnType.SUCCESS;

		Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_CONFIGURED));
		Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_REGION,
				"region_min_x", config.getRegionMinX(),
				"region_min_z", config.getRegionMinZ(),
				"region_max_x", config.getRegionMaxX(),
				"region_max_z", config.getRegionMaxZ()
		));
		Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_ORIGIN,
				"origin_x", config.getOriginX(),
				"origin_z", config.getOriginZ()
		));
		Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_BASE, "base_charge", ShippingMoney.format(config.getBaseCharge())));
		Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_FREE_DISTANCE, "free_distance", config.getFreeDistance()));
		Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_RATE,
				"rate_per_unit", ShippingMoney.format(config.getRatePerUnit()),
				"distance_unit", config.getDistanceUnit()
		));
		Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_YOUR_DISTANCE,
				"distance", String.format("%,.0f", breakdown.getDistance()),
				"billable_distance", String.format("%,.0f", breakdown.getBillableDistance())
		));
		Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_YOUR_COST, "shipping_total", ShippingMoney.format(breakdown.getTotal())));

		if (breakdown.isFlatExempt())
			Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_EXEMPT_FLAT));
		if (breakdown.isDistanceExempt())
			Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_EXEMPT_DISTANCE));

		Common.tell(player, TranslationManager.string(player, Translations.SHIPPING_INFO_MARKET_WORLDS));
		return ReturnType.SUCCESS;
	}

	@Override
	protected List<String> tab(CommandSender sender, String... args) {
		return null;
	}

	@Override
	public String getPermissionNode() {
		return "markets.command.shipping";
	}

	@Override
	public String getSyntax() {
		return "shipping";
	}

	@Override
	public String getDescription() {
		return "View shipping cost details for your location";
	}
}
