package ca.tweetzy.markets.api.currency;

import ca.tweetzy.markets.api.Identifiable;
import ca.tweetzy.markets.api.Storeable;
import ca.tweetzy.markets.api.Synchronize;
import ca.tweetzy.markets.api.Trackable;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface Payment extends Identifiable, Trackable, Synchronize, Storeable<Payment> {

	UUID getFor();

	ItemStack getCurrency();

	double getAmount();

	void setAmount(final double amount);

	String getReason();
}
