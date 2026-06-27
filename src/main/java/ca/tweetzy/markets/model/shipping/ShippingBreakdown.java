package ca.tweetzy.markets.model.shipping;

import lombok.Getter;
import lombok.NonNull;

import java.math.BigDecimal;

@Getter
public final class ShippingBreakdown {

	public enum NoChargeReason {
		NONE,
		DISABLED,
		UNCONFIGURED_WORLD
	}

	private final boolean enabled;
	private final boolean configured;
	private final String worldName;
	private final NoChargeReason noChargeReason;
	private final double distance;
	private final double billableDistance;
	private final boolean insideRegion;
	private final BigDecimal baseCharge;
	private final BigDecimal distanceCharge;
	private final BigDecimal total;
	private final boolean flatExempt;
	private final boolean distanceExempt;

	private ShippingBreakdown(
			final boolean enabled,
			final boolean configured,
			@NonNull final String worldName,
			@NonNull final NoChargeReason noChargeReason,
			final double distance,
			final double billableDistance,
			final boolean insideRegion,
			@NonNull final BigDecimal baseCharge,
			@NonNull final BigDecimal distanceCharge,
			@NonNull final BigDecimal total,
			final boolean flatExempt,
			final boolean distanceExempt
	) {
		this.enabled = enabled;
		this.configured = configured;
		this.worldName = worldName;
		this.noChargeReason = noChargeReason;
		this.distance = distance;
		this.billableDistance = billableDistance;
		this.insideRegion = insideRegion;
		this.baseCharge = baseCharge;
		this.distanceCharge = distanceCharge;
		this.total = total;
		this.flatExempt = flatExempt;
		this.distanceExempt = distanceExempt;
	}

	public static ShippingBreakdown none(@NonNull final String worldName, @NonNull final NoChargeReason reason) {
		return new ShippingBreakdown(
				reason != NoChargeReason.DISABLED,
				reason != NoChargeReason.UNCONFIGURED_WORLD,
				worldName,
				reason,
				0,
				0,
				true,
				BigDecimal.ZERO.setScale(2),
				BigDecimal.ZERO.setScale(2),
				BigDecimal.ZERO.setScale(2),
				false,
				false
		);
	}

	public static ShippingBreakdown of(
			@NonNull final String worldName,
			final double distance,
			final double billableDistance,
			final boolean insideRegion,
			@NonNull final BigDecimal baseCharge,
			@NonNull final BigDecimal distanceCharge,
			final boolean flatExempt,
			final boolean distanceExempt
	) {
		final BigDecimal total = baseCharge.add(distanceCharge);
		return new ShippingBreakdown(
				true,
				true,
				worldName,
				NoChargeReason.NONE,
				distance,
				billableDistance,
				insideRegion,
				baseCharge,
				distanceCharge,
				total,
				flatExempt,
				distanceExempt
		);
	}

	public double getTotalAsDouble() {
		return this.total.doubleValue();
	}

	public boolean appliesCharge() {
		return this.total.compareTo(BigDecimal.ZERO) > 0;
	}
}
