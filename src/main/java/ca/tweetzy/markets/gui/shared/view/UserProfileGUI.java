package ca.tweetzy.markets.gui.shared.view;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.settings.TranslationEntry;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.flight.utils.TimeUtil;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.SynchronizeResult;
import ca.tweetzy.markets.api.market.core.Market;
import ca.tweetzy.markets.api.market.core.MarketUser;
import ca.tweetzy.markets.api.market.core.Rating;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.gui.shared.selector.ConfirmGUI;
import ca.tweetzy.markets.model.AdminActionLogger;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public final class UserProfileGUI extends MarketsPagedGUI<Rating> {

	private final OfflinePlayer profileUser;
	private final String profileUserName;
	private boolean serverProfile = false;

	public UserProfileGUI(Gui parent, @NonNull Player player, @NonNull final OfflinePlayer profileUser) {
		super(parent, player, TranslationManager.string(player, Translations.GUI_USER_PROFILE_TITLE,
				"player_name", profileUser.getUniqueId().equals(UUID.fromString(Settings.SERVER_MARKET_UUID.getString()))
					? TranslationManager.string(Translations.SERVER_MARKET_NAME)
					: Markets.getPlayerManager().get(profileUser.getUniqueId()).getLastKnownName()
		), 6, Markets.getRatingManager().getRatingsByOrFor(profileUser));
		this.profileUser = profileUser;
		this.profileUserName = Markets.getPlayerManager().get(profileUser.getUniqueId()).getLastKnownName();
		this.serverProfile = profileUser.getUniqueId().equals(UUID.fromString(Settings.SERVER_MARKET_UUID.getString()));
		setAsync(true);
		setDefaultItem(QuickItem.bg(Settings.GUI_USER_PROFILE_BACKGROUND.getItemStack()));
		draw();
	}

	@Override
	protected void drawFixed() {
		final MarketUser user = Markets.getPlayerManager().get(this.profileUser.getUniqueId());

		// For server market, show texture immediately
		if (user.isServerMarket()) {
			setItem(1, 4, QuickItem
					.of(Settings.SERVER_MARKET_TEXTURE.getString())
					.name(TranslationManager.string(this.player, Translations.GUI_USER_PROFILE_ITEMS_USER_NAME, "player_name", TranslationManager.string(Translations.SERVER_MARKET_NAME)))
					.lore(TranslationManager.list(this.player, Translations.GUI_USER_PROFILE_ITEMS_USER_LORE,
							"user_last_seen", TimeUtil.convertToReadableDate(System.currentTimeMillis(), Settings.DATETIME_FORMAT.getString()),
							"true", TranslationManager.string(this.player, Translations.TRUE)
					))
					.make()
			);
		} else {
			// For player profile, show placeholder first, then load async
			setItem(1, 4, QuickItem
					.of(CompMaterial.PLAYER_HEAD)
					.name(TranslationManager.string(this.player, Translations.GUI_USER_PROFILE_ITEMS_USER_NAME, "player_name", this.profileUserName))
					.lore(TranslationManager.list(this.player, Translations.GUI_USER_PROFILE_ITEMS_USER_LORE,
							"user_last_seen", TimeUtil.convertToReadableDate(user.getLastSeenAt(), Settings.DATETIME_FORMAT.getString()),
							"true", TranslationManager.string(this.player, this.profileUser.isOnline() ? Translations.TRUE : Translations.FALSE)
					))
					.make()
			);

			// Load player head asynchronously
			QuickItem.asyncPlayerHead(this.profileUser).thenAccept(skull -> {
				ItemStack finalItem = QuickItem.of(skull)
						.name(TranslationManager.string(this.player, Translations.GUI_USER_PROFILE_ITEMS_USER_NAME, "player_name", this.profileUserName))
						.lore(TranslationManager.list(this.player, Translations.GUI_USER_PROFILE_ITEMS_USER_LORE,
								"user_last_seen", TimeUtil.convertToReadableDate(user.getLastSeenAt(), Settings.DATETIME_FORMAT.getString()),
								"true", TranslationManager.string(this.player, this.profileUser.isOnline() ? Translations.TRUE : Translations.FALSE)
						))
						.make();

				Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
					setItem(1, 4, finalItem);
				});
			});
		}

		applyBackExit();
	}

	@Override
	protected void onPopulateComplete() {
		// Load rating player heads asynchronously after the GUI is populated
		loadRatingHeadsAsync();
	}

	@Override
	protected ItemStack makeDisplayItem(Rating rating) {
		// Determine which lore to use based on admin permission
		final boolean hasAdminPermission = this.player.hasPermission("markets.admin.removerating") || this.player.isOp();
		final TranslationEntry loreEntry = hasAdminPermission
			? Translations.GUI_USER_PROFILE_ITEMS_RATING_LORE_ADMIN
			: Translations.GUI_USER_PROFILE_ITEMS_RATING_LORE;

		// Return placeholder head immediately
		// The actual player head will be loaded asynchronously in onPopulateComplete()
		return QuickItem
				.of(CompMaterial.PLAYER_HEAD)
				.name(TranslationManager.string(player, Translations.GUI_USER_PROFILE_ITEMS_RATING_NAME, "rater_name", rating.getRaterName()))
				.lore(TranslationManager.list(player, loreEntry,
						"rating_stars", StringUtils.repeat("★", rating.getStars()),
						"rating_date", TimeUtil.convertToReadableDate(rating.getTimeCreated(), Settings.DATETIME_FORMAT.getString()),
						"rating_feedback", rating.getFeedback(),
						"drop_key", TranslationManager.string(player, Translations.DROP_KEY)
				))
				.make();
	}

	private void loadRatingHeadsAsync() {
		// Determine which lore to use based on admin permission
		final boolean hasAdminPermission = this.player.hasPermission("markets.admin.removerating") || this.player.isOp();
		final TranslationEntry loreEntry = hasAdminPermission
			? Translations.GUI_USER_PROFILE_ITEMS_RATING_LORE_ADMIN
			: Translations.GUI_USER_PROFILE_ITEMS_RATING_LORE;

		// Get the current page items
		final List<Rating> itemsToDisplay = this.items.stream()
				.skip((page - 1) * (long) fillSlots().size())
				.limit(fillSlots().size())
				.toList();

		// Load player heads asynchronously for each rating
		for (int i = 0; i < itemsToDisplay.size(); i++) {
			final Rating rating = itemsToDisplay.get(i);
			final int slotIndex = fillSlots().get(i);

			// Load the player head asynchronously
			final OfflinePlayer rater = Bukkit.getOfflinePlayer(rating.getRaterUUID());
			QuickItem.asyncPlayerHead(rater).thenAccept(skull -> {
				// Build the final item with the loaded skull
				ItemStack finalItem = QuickItem.of(skull)
						.name(TranslationManager.string(player, Translations.GUI_USER_PROFILE_ITEMS_RATING_NAME, "rater_name", rating.getRaterName()))
						.lore(TranslationManager.list(player, loreEntry,
								"rating_stars", StringUtils.repeat("★", rating.getStars()),
								"rating_date", TimeUtil.convertToReadableDate(rating.getTimeCreated(), Settings.DATETIME_FORMAT.getString()),
								"rating_feedback", rating.getFeedback(),
								"drop_key", TranslationManager.string(player, Translations.DROP_KEY)
						))
						.make();

				// Update the slot on the main thread
				Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
					setItem(slotIndex, finalItem);
				});
			});
		}
	}

	@Override
	protected void onClick(Rating rating, GuiClickEvent click) {
		// Handle admin deletion with Q key (DROP click type)
		if (click.clickType == ClickType.DROP) {
			if (click.player.hasPermission("markets.admin.removerating")) {
				click.manager.showGUI(click.player, new ConfirmGUI(this, click.player, confirmed -> {
					if (confirmed) {
						// Find the market this rating belongs to
						final Market market = Markets.getMarketManager().getByUUID(rating.getMarketID());

						// Log admin action
						AdminActionLogger.log(click.player.getName(), "Removed rating from user profile" +
							"Profile User: " + this.profileUserName + " (" + this.profileUser.getUniqueId() + ")" +
							"Market: " + (market != null ? market.getDisplayName() : "Unknown") + " (" + rating.getMarketID() + ")" +
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

							// Remove from market's rating list if market exists
							if (market != null) {
								market.getRatings().remove(rating);
							}

							// Refresh GUI with updated ratings list
							click.manager.showGUI(click.player, new UserProfileGUI(this.parent, click.player, this.profileUser));
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
		return List.of(28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
	}
}
