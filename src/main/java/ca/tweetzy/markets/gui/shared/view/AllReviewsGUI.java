package ca.tweetzy.markets.gui.shared.view;

import ca.tweetzy.flight.comp.enums.CompMaterial;
import ca.tweetzy.flight.gui.Gui;
import ca.tweetzy.flight.gui.events.GuiClickEvent;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.QuickItem;
import ca.tweetzy.flight.utils.TimeUtil;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.api.market.ReviewSortType;
import ca.tweetzy.markets.api.market.core.Rating;
import ca.tweetzy.markets.gui.MarketsPagedGUI;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.settings.Translations;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AllReviewsGUI extends MarketsPagedGUI<Rating> {

	private ReviewSortType sortType;

	public AllReviewsGUI(Gui parent, @NonNull Player player) {
		this(parent, player, ReviewSortType.RECENT);
	}

	public AllReviewsGUI(Gui parent, @NonNull Player player, ReviewSortType sortType) {
		super(parent, player, TranslationManager.string(player, Translations.GUI_ALL_REVIEWS_TITLE), 6, new ArrayList<>());
		this.sortType = sortType;
		setAsync(true);
		setDefaultItem(QuickItem.bg(Settings.GUI_ALL_REVIEWS_BACKGROUND.getItemStack()));
		draw();
	}

	@Override
	protected void onPopulateComplete() {
		// This is called after the async population is complete
		// Now we can safely load player heads asynchronously
		loadPlayerHeadsAsync();
	}

	@Override
	protected void prePopulate() {
		// Get all ratings from all markets
		this.items = new ArrayList<>();
		Markets.getMarketManager().getValues().forEach(market -> {
			this.items.addAll(market.getRatings());
		});

		// Sort based on the selected sort type
		if (this.sortType == ReviewSortType.RECENT) {
			this.items.sort(Comparator.comparing(Rating::getTimeCreated).reversed());
		} else if (this.sortType == ReviewSortType.TOP_RATED) {
			this.items.sort(Comparator.comparing(Rating::getStars).reversed());
		} else if (this.sortType == ReviewSortType.LOWEST_RATED) {
			this.items.sort(Comparator.comparing(Rating::getStars));
		}
	}

	@Override
	protected ItemStack makeDisplayItem(Rating rating) {
		// Return a placeholder head immediately
		// The actual player head will be loaded asynchronously
		return QuickItem
				.of(CompMaterial.PLAYER_HEAD)
				.name(TranslationManager.string(this.player, Translations.GUI_ALL_REVIEWS_ITEMS_REVIEW_NAME,
						"rater_name", rating.getRaterName()))
				.lore(TranslationManager.list(this.player, Translations.GUI_ALL_REVIEWS_ITEMS_REVIEW_LORE,
						"rating_stars", StringUtils.repeat("★", rating.getStars()),
						"rating_date", TimeUtil.convertToReadableDate(rating.getTimeCreated(), Settings.DATETIME_FORMAT.getString()),
						"rating_feedback", rating.getFeedback()
				))
				.make();
	}

	@Override
	protected void drawFixed() {
		// Top row: dark stained glass pane
		for (int i = 0; i < 9; i++) {
			setItem(i, QuickItem.of(CompMaterial.BLACK_STAINED_GLASS_PANE).name(" ").make());
		}

		// Bottom row: filter button at slot 53 (row 5, column 8)
		setButton(5, 8, QuickItem
				.of(Settings.GUI_ALL_REVIEWS_ITEMS_FILTER_ITEM.getItemStack())
				.name(TranslationManager.string(this.player, Translations.GUI_ALL_REVIEWS_ITEMS_FILTER_NAME))
				.lore(TranslationManager.list(this.player, Translations.GUI_ALL_REVIEWS_ITEMS_FILTER_LORE,
						"review_sort_type", this.sortType.getTranslatedName()))
				.make(), click -> {
			this.sortType = this.sortType.next();
			draw();
		});
	}

	private void loadPlayerHeadsAsync() {
		// Get the current page items
		final List<Rating> itemsToDisplay = this.items.stream()
				.skip((page - 1) * (long) fillSlots().size())
				.limit(fillSlots().size())
				.toList();

		// Load player heads asynchronously
		for (int i = 0; i < itemsToDisplay.size(); i++) {
			final Rating rating = itemsToDisplay.get(i);
			final int slotIndex = fillSlots().get(i);

			// Load the player head asynchronously
			final OfflinePlayer rater = Bukkit.getOfflinePlayer(rating.getRaterUUID());
			QuickItem.asyncPlayerHead(rater).thenAccept(skull -> {
				// Build the final item with the loaded skull
				ItemStack finalItem = QuickItem.of(skull)
						.name(TranslationManager.string(this.player, Translations.GUI_ALL_REVIEWS_ITEMS_REVIEW_NAME,
								"rater_name", rating.getRaterName()))
						.lore(TranslationManager.list(this.player, Translations.GUI_ALL_REVIEWS_ITEMS_REVIEW_LORE,
								"rating_stars", StringUtils.repeat("★", rating.getStars()),
								"rating_date", TimeUtil.convertToReadableDate(rating.getTimeCreated(), Settings.DATETIME_FORMAT.getString()),
								"rating_feedback", rating.getFeedback()
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
		// For now, clicking a review doesn't do anything
		// Could potentially navigate to the market the review belongs to
	}

	@Override
	protected List<Integer> fillSlots() {
		// Custom slots: 4 rows, 7 items per row (centered in each row)
		// Row 1: slots 10-16
		// Row 2: slots 19-25
		// Row 3: slots 28-34
		// Row 4: slots 37-43
		List<Integer> slots = new ArrayList<>();
		for (int row = 1; row <= 4; row++) {
			for (int col = 1; col <= 7; col++) {
				slots.add(row * 9 + col);
			}
		}
		return slots;
	}
}
