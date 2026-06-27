package ca.tweetzy.markets.model.manager;

import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.manager.KeyValueManager;
import ca.tweetzy.markets.model.shipping.ShippingWorldConfig;
import ca.tweetzy.markets.settings.Settings;
import lombok.NonNull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class ShippingManager extends KeyValueManager<String, ShippingWorldConfig> {

	private static final String WORLDS_PATH = "settings.shipping.worlds";

	public ShippingManager() {
		super("Shipping");
	}

	@Override
	public void load() {
		clear();

		FileConfiguration config = getConfigFile();
		ConfigurationSection worlds = config.getConfigurationSection(WORLDS_PATH);

		if (worlds == null || worlds.getKeys(false).isEmpty()) {
			seedDefaults();
			config = getConfigFile();
			worlds = config.getConfigurationSection(WORLDS_PATH);
		}

		if (worlds == null)
			return;

		for (final String worldName : worlds.getKeys(false)) {
			final ShippingWorldConfig worldConfig = readWorldConfig(worldName, worlds.getConfigurationSection(worldName));
			if (worldConfig != null)
				add(worldName.toLowerCase(), worldConfig);
		}
	}

	public ShippingWorldConfig getWorldConfig(@NonNull final String worldName) {
		return get(worldName.toLowerCase());
	}

	public List<String> getConfiguredWorlds() {
		return new ArrayList<>(getManagerContent().keySet());
	}

	public void addWorld(@NonNull final ShippingWorldConfig config) {
		add(config.getWorldName().toLowerCase(), config);
		saveWorldConfig(config);
	}

	public void removeWorld(@NonNull final String worldName) {
		remove(worldName.toLowerCase());
		final FileConfiguration config = getConfigFile();
		config.set(WORLDS_PATH + "." + worldName, null);
		saveConfig(config);
	}

	public void updateWorld(@NonNull final ShippingWorldConfig config) {
		add(config.getWorldName().toLowerCase(), config);
		saveWorldConfig(config);
	}

	public void setGlobalEnabled(final boolean enabled) {
		final FileConfiguration config = getConfigFile();
		config.set("settings.shipping.enabled", enabled);
		saveConfig(config);
		Settings.init();
	}

	public void setReceiver(@NonNull final String receiver) {
		final FileConfiguration config = getConfigFile();
		config.set("settings.shipping.receiver", receiver);
		saveConfig(config);
		Settings.init();
	}

	private void seedDefaults() {
		saveWorldConfig(ShippingWorldConfig.defaults("world"));
		saveWorldConfig(ShippingWorldConfig.pocketDefaults());
	}

	private void saveWorldConfig(@NonNull final ShippingWorldConfig worldConfig) {
		final FileConfiguration config = getConfigFile();
		final String base = WORLDS_PATH + "." + worldConfig.getWorldName();
		config.set(base + ".region min x", worldConfig.getRegionMinX());
		config.set(base + ".region min z", worldConfig.getRegionMinZ());
		config.set(base + ".region max x", worldConfig.getRegionMaxX());
		config.set(base + ".region max z", worldConfig.getRegionMaxZ());
		config.set(base + ".origin x", worldConfig.getOriginX());
		config.set(base + ".origin z", worldConfig.getOriginZ());
		config.set(base + ".base charge", worldConfig.getBaseCharge());
		config.set(base + ".free distance", worldConfig.getFreeDistance());
		config.set(base + ".distance unit", worldConfig.getDistanceUnit());
		config.set(base + ".rate per unit", worldConfig.getRatePerUnit());
		saveConfig(config);
	}

	private ShippingWorldConfig readWorldConfig(@NonNull final String worldName, final ConfigurationSection section) {
		if (section == null)
			return null;

		final ShippingWorldConfig config = new ShippingWorldConfig(worldName);
		config.setRegionMinX(section.getInt("region min x", -50000));
		config.setRegionMinZ(section.getInt("region min z", -15000));
		config.setRegionMaxX(section.getInt("region max x", 50000));
		config.setRegionMaxZ(section.getInt("region max z", 15000));
		config.setOriginX(section.getInt("origin x", 50));
		config.setOriginZ(section.getInt("origin z", -2450));
		config.setBaseCharge(section.getDouble("base charge", 10.0));
		config.setFreeDistance(section.getInt("free distance", 10000));
		config.setDistanceUnit(section.getInt("distance unit", 1000));
		config.setRatePerUnit(section.getDouble("rate per unit", 0.05));
		return config;
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
			Markets.getInstance().getLogger().log(Level.SEVERE, "Failed to save shipping config", exception);
		}
	}
}
