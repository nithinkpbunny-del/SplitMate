package com.splitmate.settlement;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.splitmate.expense.Expense;
import com.splitmate.payment.Payment;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.FacetOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.facet;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;

/**
 * Live balance figures computed inside MongoDB with aggregation pipelines, used by the dashboard and
 * split headers. They follow exactly the formula of {@link SettlementCalculator}, which remains the source
 * of truth for full calculations (the integration tests check both agree).
 */
@Component
public class BalanceQueries {

	private final MongoTemplate mongo;

	public BalanceQueries(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	/**
	 * For each split: total spend, number of expenses, and what {@code userId} paid, consumed, sent and
	 * received. One {@code $facet} pipeline over expenses plus one {@code $group} over payments.
	 */
	public Map<ObjectId, SplitFigures> figuresFor(Collection<ObjectId> splitIds, ObjectId userId) {
		Map<ObjectId, long[]> acc = new HashMap<>();
		if (splitIds.isEmpty()) {
			return Map.of();
		}

		FacetOperation facets = facet(
				group("splitId").sum("amountMinor").as("total").count().as("count")
					.sum(ConditionalOperators.when(Criteria.where("paidBy").is(userId)).thenValueOf("amountMinor").otherwise(0))
					.as("paid"))
			.as("totals")
			.and(unwind("shares"), match(Criteria.where("shares.userId").is(userId)),
					group("splitId").sum("shares.amountMinor").as("share"))
			.as("myShares");

		Aggregation expenses = Aggregation.newAggregation(
				match(Criteria.where("splitId").in(splitIds).and("deleted").is(false)), facets);
		Document result = mongo.aggregate(expenses, mongo.getCollectionName(Expense.class), Document.class)
			.getUniqueMappedResult();
		if (result != null) {
			for (Document row : result.getList("totals", Document.class)) {
				long[] f = acc.computeIfAbsent(row.getObjectId("_id"), k -> new long[6]);
				f[0] = number(row, "total");
				f[1] = number(row, "count");
				f[2] = number(row, "paid");
			}
			for (Document row : result.getList("myShares", Document.class)) {
				acc.computeIfAbsent(row.getObjectId("_id"), k -> new long[6])[3] = number(row, "share");
			}
		}

		Aggregation payments = Aggregation.newAggregation(
				match(Criteria.where("splitId").in(splitIds)
					.orOperator(Criteria.where("fromUserId").is(userId), Criteria.where("toUserId").is(userId))),
				group("splitId")
					.sum(ConditionalOperators.when(Criteria.where("fromUserId").is(userId)).thenValueOf("amountMinor").otherwise(0))
					.as("sent")
					.sum(ConditionalOperators.when(Criteria.where("toUserId").is(userId)).thenValueOf("amountMinor").otherwise(0))
					.as("received"));
		List<Document> paymentRows = mongo.aggregate(payments, mongo.getCollectionName(Payment.class), Document.class)
			.getMappedResults();
		for (Document row : paymentRows) {
			long[] f = acc.computeIfAbsent(row.getObjectId("_id"), k -> new long[6]);
			f[4] = number(row, "sent");
			f[5] = number(row, "received");
		}

		Map<ObjectId, SplitFigures> figures = new HashMap<>();
		acc.forEach((id, f) -> figures.put(id, new SplitFigures(f[0], f[1], f[2], f[3], f[4], f[5])));
		return figures;
	}

	public SplitFigures figuresFor(ObjectId splitId, ObjectId userId) {
		return figuresFor(List.of(splitId), userId).getOrDefault(splitId, SplitFigures.EMPTY);
	}

	private static long number(Document row, String key) {
		Object value = row.get(key);
		return value instanceof Number n ? n.longValue() : 0L;
	}

}
