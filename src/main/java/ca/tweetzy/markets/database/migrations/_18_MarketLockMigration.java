package ca.tweetzy.markets.database.migrations;

import ca.tweetzy.flight.database.DataMigration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class _18_MarketLockMigration extends DataMigration {

	public _18_MarketLockMigration() {
		super(18);
	}

	@Override
	public void migrate(Connection connection, String tablePrefix) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE " + tablePrefix + "market_lock (" +
					"market_id VARCHAR(36) PRIMARY KEY, " +
					"locked_by VARCHAR(16) NOT NULL, " +
					"locked_at BigInt NOT NULL" +
					")");
		}
	}
}
