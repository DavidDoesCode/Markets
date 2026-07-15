package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.flight.settings.TranslationManager;
import ca.tweetzy.flight.utils.Common;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.model.AuditEntry;
import ca.tweetzy.markets.model.AuditListingRemover;
import ca.tweetzy.markets.model.AuditScanner;
import ca.tweetzy.markets.model.EssentialsWorthHook;
import ca.tweetzy.markets.settings.Translations;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@UtilityClass
public final class AuditClearAdminCommand {

	public ReturnType execute(CommandSender sender, String... args) {
		if (!sender.hasPermission("markets.admin.auditclear")) {
			Common.tell(sender, TranslationManager.string(Translations.NO_PERMISSION));
			return ReturnType.FAIL;
		}

		if (!EssentialsWorthHook.isAvailable()) {
			Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_UNAVAILABLE));
			return ReturnType.FAIL;
		}

		if (args.length < 2) {
			Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_CLEAR_INVALID));
			return ReturnType.FAIL;
		}

		final Double percent = parseDouble(args[1]);
		if (percent == null || percent <= 0) {
			Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_CLEAR_INVALID));
			return ReturnType.FAIL;
		}

		Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_CLEAR_STARTED,
				"percent", String.format("%,.0f", percent)));

		final String adminName = sender instanceof Player player ? player.getName() : "CONSOLE";

		Bukkit.getScheduler().runTaskAsynchronously(Markets.getInstance(), () -> {
			final List<AuditEntry> matches = AuditScanner.findMatches(percent);

			Bukkit.getScheduler().runTask(Markets.getInstance(), () -> {
				if (matches.isEmpty()) {
					Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_NONE));
					return;
				}

				final List<AuditEntry> toRemove = new ArrayList<>();
				int skipped = 0;

				for (final AuditEntry entry : matches) {
					if (AuditListingRemover.shouldSkipForAuditClear(entry.getMarketItem())) {
						skipped++;
						continue;
					}
					toRemove.add(entry);
				}

				if (toRemove.isEmpty()) {
					Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_CLEAR_COMPLETE,
							"deleted", "0",
							"skipped", String.valueOf(skipped),
							"failed", "0"));
					return;
				}

				final AtomicInteger deleted = new AtomicInteger();
				final AtomicInteger failed = new AtomicInteger();
				final AtomicInteger remaining = new AtomicInteger(toRemove.size());
				final int skippedFinal = skipped;

				for (final AuditEntry entry : toRemove) {
					AuditListingRemover.removeListing(
							entry.getMarketItem(),
							adminName,
							"Audit clear removed listing",
							success -> {
								if (success)
									deleted.incrementAndGet();
								else
									failed.incrementAndGet();

								if (remaining.decrementAndGet() == 0) {
									Common.tell(sender, TranslationManager.string(Translations.WORTH_AUDIT_CLEAR_COMPLETE,
											"deleted", String.valueOf(deleted.get()),
											"skipped", String.valueOf(skippedFinal),
											"failed", String.valueOf(failed.get())));
								}
							}
					);
				}
			});
		});

		return ReturnType.SUCCESS;
	}

	private Double parseDouble(String value) {
		try {
			return Double.parseDouble(value);
		} catch (final NumberFormatException exception) {
			return null;
		}
	}

	public List<String> tab(String... args) {
		if (args.length == 1 || args.length == 2)
			return List.of("50", "100", "500", "1000", "2500");
		return null;
	}
}
