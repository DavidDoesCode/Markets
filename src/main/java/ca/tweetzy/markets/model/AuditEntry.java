package ca.tweetzy.markets.model;

import ca.tweetzy.markets.api.market.core.MarketItem;
import lombok.Getter;
import lombok.NonNull;

import java.util.UUID;

@Getter
public final class AuditEntry {

	private final MarketItem marketItem;
	private final UUID ownerUUID;
	private final String ownerName;
	private final String itemName;
	private final double unitPrice;
	private final double worth;
	private final double ratioPercent;

	public AuditEntry(@NonNull final MarketItem marketItem, @NonNull final UUID ownerUUID, @NonNull final String ownerName,
					 @NonNull final String itemName, final double unitPrice, final double worth, final double ratioPercent) {
		this.marketItem = marketItem;
		this.ownerUUID = ownerUUID;
		this.ownerName = ownerName;
		this.itemName = itemName;
		this.unitPrice = unitPrice;
		this.worth = worth;
		this.ratioPercent = ratioPercent;
	}

	public String getItemId() {
		return this.marketItem.getId().toString();
	}
}
