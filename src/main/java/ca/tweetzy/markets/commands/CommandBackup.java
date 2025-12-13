package ca.tweetzy.markets.commands;

import ca.tweetzy.flight.command.AllowedExecutor;
import ca.tweetzy.flight.command.Command;
import ca.tweetzy.flight.command.ReturnType;
import ca.tweetzy.markets.Markets;
import ca.tweetzy.markets.settings.Settings;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class CommandBackup extends Command {

	public CommandBackup() {
		super(AllowedExecutor.BOTH, Settings.CMD_ALIAS_SUB_BACKUP.getStringList().toArray(new String[0]));
	}

	@Override
	protected ReturnType execute(CommandSender sender, String... args) {
		if (Markets.getDatabaseBackupTask() == null) {
			tell(sender, "&cDatabase backup system is not initialized");
			return ReturnType.FAIL;
		}

		tell(sender, "&eStarting manual database backup...");
		Markets.getDatabaseBackupTask().triggerManualBackup();
		tell(sender, "&aBackup started! Check console for completion status.");
		return ReturnType.SUCCESS;
	}

	@Override
	protected List<String> tab(CommandSender sender, String... args) {
		return null;
	}

	@Override
	public String getPermissionNode() {
		return "markets.command.backup";
	}

	@Override
	public String getSyntax() {
		return "backup";
	}

	@Override
	public String getDescription() {
		return "Manually trigger a database backup";
	}
}
