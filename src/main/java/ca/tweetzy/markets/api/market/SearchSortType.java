package ca.tweetzy.markets.api.market;

import ca.tweetzy.markets.api.market.core.MarketItem;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Comparator;

@AllArgsConstructor
@Getter
public enum SearchSortType {

	PRICE_HIGHEST("Highest Price", Comparator.comparingDouble(MarketItem::getPrice).reversed()),
	PRICE_LOWEST("Lowest Price", Comparator.comparingDouble(MarketItem::getPrice)),
	QUANTITY_HIGHEST("Highest Quantity", Comparator.comparingInt(item -> item.getItem().getAmount()).reversed()),
	QUANTITY_LOWEST("Lowest Quantity", Comparator.comparingInt(item -> item.getItem().getAmount())),
	STOCK_HIGHEST("Highest Stock", Comparator.comparingInt(MarketItem::getStock).reversed()),
	STOCK_LOWEST("Lowest Stock", Comparator.comparingInt(MarketItem::getStock));

	private final String displayName;
	private final Comparator<MarketItem> comparator;

	public SearchSortType next() {
		SearchSortType[] values = values();
		int nextIndex = (this.ordinal() + 1) % values.length;
		return values[nextIndex];
	}
}
