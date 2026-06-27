package ca.tweetzy.markets.model.shipping;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

@UtilityClass
public final class ShippingMoney {

	public BigDecimal round(final double amount) {
		return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
	}

	public String format(@NonNull final BigDecimal amount) {
		return String.format("%,.2f", amount);
	}

	public String format(final double amount) {
		return format(round(amount));
	}
}
