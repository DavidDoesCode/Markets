package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.AllowedExecutor;
import ca.tweetzy.flight.command.Command;
import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.gui.shared.view.content.MarketSearchGUI;
import ca.tweetzy.markets.settings.Settings;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class CommandSearch extends Command {

	public CommandSearch() {
		super(AllowedExecutor.PLAYER, Settings.CMD_ALIAS_SUB_SEARCH.getStringList().toArray(new String[0]));
	}

	@Override
	protected ReturnType execute(CommandSender sender, String... args) {
		if (args.length < 1) {
			return ReturnType.FAIL;
		}

		if (sender instanceof final Player player) {
			final StringBuilder builder = new StringBuilder();

			for (int i = 0; i < args.length; i++) {
				builder.append(args[i]).append(" ");
			}

			Markets.getGuiManager().showGUI(player, new MarketSearchGUI(null, player, builder.toString().trim()));
		}
		return ReturnType.SUCCESS;
	}

	@Override
	protected List<String> tab(CommandSender sender, String... args) {
		if (args.length == 0) {
			return null;
		}

		// Get the current search term (last argument)
		String currentSearch = args[args.length - 1].toLowerCase();
		List<String> suggestions = new ArrayList<>();

		// Add all Material names
		for (Material material : Material.values()) {
			if (material.isItem()) {
				String materialName = material.name().toLowerCase().replace("_", " ");
				if (materialName.contains(currentSearch)) {
					suggestions.add(material.name().toLowerCase());
				}
			}
		}

		// Add all enchantment names
		for (Enchantment enchantment : Enchantment.values()) {
			NamespacedKey key = enchantment.getKey();
			String enchantmentName = key.getKey().toLowerCase();
			if (enchantmentName.contains(currentSearch)) {
				suggestions.add(enchantmentName);
			}
		}

		// Sort and limit suggestions
		return suggestions.stream()
				.sorted()
				.limit(50) // Limit to 50 suggestions to avoid overwhelming the player
				.collect(Collectors.toList());
	}

	@Override
	public String getPermissionNode() {
		return "markets.command.search";
	}

	@Override
	public String getSyntax() {
		return "search <keywords>";
	}

	@Override
	public String getDescription() {
		return "Search all open markets for items";
	}
}
