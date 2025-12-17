package ca.tweetzy.markets.gui.shared.view.ratings;

import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.gui.helper.InventoryBorder;
import ca.tweetzy.flight.settings.TranslationEntry;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.flight.utils.TimeUtil;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.SynchronizeResult;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.Rating;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.gui.shared.selector.ConfirmGUI;
import ca.tweetzy.markets.model.AdminActionLogger;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class MarketRatingsViewGUI extends MarketsPagedGUI<Rating> {

	private final Market market;

	public MarketRatingsViewGUI(Gui parent, @NonNull final Player player, @NonNull final Market market) {
		super(parent, player, TranslationManager.string(player, Translations.GUI_RATINGS_TITLE, "market_display_name", market.getDisplayName()), 6, market.getRatings());
		this.market = market;
		setDefaultItem(QuickItem.bg(Settings.GUI_RATINGS_BACKGROUND.getItemStack()));
		draw();
	}

	@Override
	protected ItemStack makeDisplayItem(Rating rating) {
		// Determine which lore to use based on admin permission
		final boolean hasAdminPermission = this.player.hasPermission("markets.admin.removerating");
		final TranslationEntry loreEntry = hasAdminPermission
			? Translations.GUI_RATINGS_ITEMS_RATING_LORE_ADMIN
			: Translations.GUI_RATINGS_ITEMS_RATING_LORE;

		return QuickItem
				.of(Bukkit.getOfflinePlayer(rating.getRaterUUID()))
				.name(TranslationManager.string(player, Translations.GUI_RATINGS_ITEMS_RATING_NAME, "rater_name", rating.getRaterName()))
				.lore(TranslationManager.list(player, loreEntry,
						"rating_stars", StringUtils.repeat("★", rating.getStars()),
						"rating_date", TimeUtil.convertToReadableDate(rating.getTimeCreated(), Settings.DATETIME_FORMAT.getString()),
						"rating_feedback", rating.getFeedback(),
						"drop_key", TranslationManager.string(player, Translations.DROP_KEY)
				))
				.make();
	}

	@Override
	protected void onClick(Rating rating, GuiClickEvent click) {
		// Handle admin deletion with Q key (DROP click type)
		if (click.clickType == ClickType.DROP) {
			if (click.player.hasPermission("markets.admin.removerating")) {
				click.manager.showGUI(click.player, new ConfirmGUI(this, click.player, confirmed -> {
					if (confirmed) {
						// Log admin action
						AdminActionLogger.log(click.player.getName(), "Removed rating" +
							"Market: " + this.market.getDisplayName() + " (" + this.market.getId() + ")" +
							"Rater: " + rating.getRaterName() + " (" + rating.getRaterUUID() + ")" +
							"Stars: " + rating.getStars() +
							"Feedback: " + rating.getFeedback()
						);

						// Delete the rating
						rating.unStore(result -> {
							if (result == SynchronizeResult.FAILURE) {
								Common.tell(click.player, "&cSomething went wrong trying to delete the rating from " + rating.getRaterName());
								return;
							}

							// Remove from market's rating list
							Markets.getRatingManager().getManagerContent().remove(rating.getId());

							// Refresh GUI
							click.manager.showGUI(click.player, new MarketRatingsViewGUI(this.parent, click.player, this.market));
							Common.tell(click.player, TranslationManager.string(click.player, Translations.ADMIN_REMOVED_RATING, "rater_name", rating.getRaterName()));
						});
					} else {
						// User cancelled - return to this GUI
						click.manager.showGUI(click.player, this);
					}
				}));
			}
			return;
		}
	}

	@Override
	protected List<Integer> fillSlots() {
		return InventoryBorder.getInsideBorders(6);
	}
}
