package ca.tweetzy.markets.impl.currency;

import ca.tweetzy.flight.utils.PlayerUtil;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.currency.AbstractCurrency;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ItemCurrency extends AbstractCurrency {

	public ItemCurrency() {
		super("Markets", "Item", "&bCustom Item", false);
	}

	public boolean has(OfflinePlayer player, double amount, ItemStack item) {
		if (player == null || player.getPlayer() == null || !player.isOnline()) return false;
		return PlayerUtil.getItemCountInPlayerInventory(player.getPlayer(), item) >= amount;
	}

	public boolean withdraw(OfflinePlayer player, double amount, ItemStack item) {
		if (player == null || player.getPlayer() == null || !player.isOnline()) return false;
		PlayerUtil.removeSpecificItemQuantityFromPlayer(player.getPlayer(), item, (int) amount);
		return true;
	}

	public boolean deposit(OfflinePlayer player, double amount, ItemStack item) {
		if (player == null || player.getPlayer() == null || !player.isOnline()) return false;

		final Player online = player.getPlayer();
		final ItemStack template = item.clone();
		final int qty = (int) amount;

		Markets.newChain().sync(() -> {
			int remaining = qty;
			final int maxStack = Math.max(1, template.getMaxStackSize());

			while (remaining > 0) {
				final int stackSize = Math.min(remaining, maxStack);
				final ItemStack stack = template.clone();
				stack.setAmount(stackSize);
				PlayerUtil.giveItem(online, stack);
				remaining -= stackSize;
			}
		}).execute();

		return true;
	}

	@Override
	public boolean has(OfflinePlayer player, double amount) {
		return false;
	}

	@Override
	public boolean withdraw(OfflinePlayer player, double amount) {
		return false;
	}

	@Override
	public boolean deposit(OfflinePlayer player, double amount) {
		return false;
	}
}
