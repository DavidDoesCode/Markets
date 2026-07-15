package ca.tweetzy.markets.model;

import com.earth2me.essentials.Essentials;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;

@UtilityClass
public final class EssentialsWorthHook {

	public boolean isAvailable() {
		final Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin("Essentials");
		return plugin != null && plugin.isEnabled() && plugin instanceof Essentials;
	}

	/**
	 * @return per-unit Essentials worth, or null if unavailable / undefined
	 */
	public Double getUnitWorth(@NonNull final ItemStack itemStack) {
		if (!isAvailable())
			return null;

		final Essentials essentials = (Essentials) Bukkit.getServer().getPluginManager().getPlugin("Essentials");
		if (essentials == null)
			return null;

		final ItemStack lookup = itemStack.clone();
		lookup.setAmount(1);

		final BigDecimal price = essentials.getWorth().getPrice(essentials, lookup);
		if (price == null)
			return null;

		return price.doubleValue();
	}
}
