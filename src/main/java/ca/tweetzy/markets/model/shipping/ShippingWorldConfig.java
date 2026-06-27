package ca.tweetzy.markets.model.shipping;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
public final class ShippingWorldConfig {

	private final String worldName;
	private int regionMinX;
	private int regionMinZ;
	private int regionMaxX;
	private int regionMaxZ;
	private int originX;
	private int originZ;
	private double baseCharge;
	private int freeDistance;
	private int distanceUnit;
	private double ratePerUnit;

	public ShippingWorldConfig(@NonNull final String worldName) {
		this.worldName = worldName;
	}

	public static ShippingWorldConfig defaults(@NonNull final String worldName) {
		final ShippingWorldConfig config = new ShippingWorldConfig(worldName);
		config.regionMinX = -50000;
		config.regionMinZ = -15000;
		config.regionMaxX = 50000;
		config.regionMaxZ = 15000;
		config.originX = 50;
		config.originZ = -2450;
		config.baseCharge = 10.0;
		config.freeDistance = 10000;
		config.distanceUnit = 1000;
		config.ratePerUnit = 0.05;
		return config;
	}

	public static ShippingWorldConfig pocketDefaults() {
		final ShippingWorldConfig config = defaults("pocket1");
		config.baseCharge = 5.0;
		config.ratePerUnit = 0.025;
		return config;
	}
}
