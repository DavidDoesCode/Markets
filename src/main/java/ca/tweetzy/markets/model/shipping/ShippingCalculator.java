package ca.tweetzy.markets.model.shipping;

import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.settings.Settings;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

@UtilityClass
public final class ShippingCalculator {

	public static final String PERM_EXEMPT_FLAT = "markets.shipping.exempt.flat";
	public static final String PERM_EXEMPT_DISTANCE = "markets.shipping.exempt.distance";

	public ShippingBreakdown calculate(@NonNull final Player player) {
		final String worldName = player.getWorld().getName();

		if (!Settings.SHIPPING_ENABLED.getBoolean())
			return ShippingBreakdown.none(worldName, ShippingBreakdown.NoChargeReason.DISABLED);

		final ShippingWorldConfig config = Markets.getShippingManager().getWorldConfig(worldName);
		if (config == null)
			return ShippingBreakdown.none(worldName, ShippingBreakdown.NoChargeReason.UNCONFIGURED_WORLD);

		final Location location = player.getLocation();
		final double distance = distanceFromOrigin(location, config);
		final boolean insideRegion = isInsideRegion(location.getX(), location.getZ(), config);
		final double billableDistance = insideRegion
				? 0
				: Math.max(0, distance - config.getFreeDistance());

		final boolean flatExempt = player.hasPermission(PERM_EXEMPT_FLAT);
		final boolean distanceExempt = player.hasPermission(PERM_EXEMPT_DISTANCE);

		double rawBase = insideRegion ? 0 : config.getBaseCharge();
		if (flatExempt)
			rawBase = 0;

		double rawDistance = 0;
		if (!insideRegion && config.getDistanceUnit() > 0 && billableDistance > 0)
			rawDistance = (billableDistance / config.getDistanceUnit()) * config.getRatePerUnit();
		if (distanceExempt)
			rawDistance = 0;

		final BigDecimal baseCharge = ShippingMoney.round(rawBase);
		final BigDecimal distanceCharge = ShippingMoney.round(rawDistance);

		return ShippingBreakdown.of(
				worldName,
				distance,
				billableDistance,
				insideRegion,
				baseCharge,
				distanceCharge,
				flatExempt,
				distanceExempt
		);
	}

	private boolean isInsideRegion(final double x, final double z, @NonNull final ShippingWorldConfig config) {
		final double minX = Math.min(config.getRegionMinX(), config.getRegionMaxX());
		final double maxX = Math.max(config.getRegionMinX(), config.getRegionMaxX());
		final double minZ = Math.min(config.getRegionMinZ(), config.getRegionMaxZ());
		final double maxZ = Math.max(config.getRegionMinZ(), config.getRegionMaxZ());
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}

	private double distanceFromOrigin(@NonNull final Location location, @NonNull final ShippingWorldConfig config) {
		final double dx = location.getX() - config.getOriginX();
		final double dz = location.getZ() - config.getOriginZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
