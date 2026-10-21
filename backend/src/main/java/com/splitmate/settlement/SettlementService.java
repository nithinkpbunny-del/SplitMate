package com.splitmate.settlement;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.types.ObjectId;

import com.splitmate.activity.ActivityService;
import com.splitmate.activity.ActivityType;
import com.splitmate.common.ApiException;
import com.splitmate.common.Ids;
import com.splitmate.common.Money;
import com.splitmate.settlement.engine.MemberBalance;
import com.splitmate.settlement.engine.SettlementResult;
import com.splitmate.settlement.engine.Transfer;
import com.splitmate.split.Member;
import com.splitmate.split.Split;
import com.splitmate.split.SplitAccess;
import com.splitmate.split.SplitDeletedEvent;
import com.splitmate.split.SplitRepository;
import com.splitmate.split.SplitService;
import com.splitmate.split.SplitStatus;

import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * "Calculate split": runs the engine on current data and stores a versioned snapshot.
 */
@Service
public class SettlementService {

	private final SettlementRepository settlements;

	private final LedgerService ledger;

	private final SplitAccess access;

	private final SplitService splits;

	private final SplitRepository splitRepository;

	private final MongoTemplate mongo;

	private final ActivityService activity;

	public SettlementService(SettlementRepository settlements, LedgerService ledger, SplitAccess access,
			SplitService splits, SplitRepository splitRepository, MongoTemplate mongo, ActivityService activity) {
		this.settlements = settlements;
		this.ledger = ledger;
		this.access = access;
		this.splits = splits;
		this.splitRepository = splitRepository;
		this.mongo = mongo;
		this.activity = activity;
	}

	public SettlementResponse calculate(String splitId, ObjectId userId) {
		Split split = access.requireMember(splitId, userId);
		// Taken before reading the data: any change after this instant makes the snapshot stale.
		Instant calculatedAt = Instant.now();
		SettlementResult result = ledger.calculateLive(split);
		Map<String, Member> members = LedgerService.membersById(split);

		Settlement snapshot = new Settlement();
		snapshot.setSplitId(split.getId());
		snapshot.setVersion(splits.nextSettlementVersion(split.getId()));
		snapshot.setCalculatedBy(userId);
		snapshot.setCalculatedAt(calculatedAt);
		snapshot.setCurrency(split.getCurrency());
		snapshot.setTotalSpendMinor(result.totalSpendMinor());
		snapshot.setExpenseCount(result.includedExpenseIds().size());
		snapshot.setExpenseIds(result.includedExpenseIds().stream().map(ObjectId::new).toList());
		snapshot.setMemberSummaries(result.balances().stream().map(b -> summary(b, members)).toList());
		snapshot.setTransfers(result.transfers().stream().map(t -> transfer(t, members)).toList());
		snapshot = settlements.save(snapshot);

		mongo.updateFirst(query(where("_id").is(split.getId())), new Update().set("lastCalculatedAt", calculatedAt),
				Split.class);

		String actor = access.actorName(split, userId);
		int n = snapshot.getTransfers().size();
		String outcome = n == 0 ? "everyone is square" : n == 1 ? "1 transfer settles everyone"
				: n + " transfers settle everyone";
		activity.log(split.getId(), userId, actor, ActivityType.SPLIT_CALCULATED,
				actor + " calculated the split (" + Money.format(snapshot.getTotalSpendMinor(), split.getCurrency())
						+ "): " + outcome,
				Map.of("settlementId", snapshot.getId().toHexString(), "version", snapshot.getVersion()));
		return SettlementResponse.from(snapshot, actor);
	}

	public Optional<SettlementResponse> latest(String splitId, ObjectId userId) {
		Split split = access.requireMember(splitId, userId);
		return settlements.findFirstBySplitIdOrderByVersionDesc(split.getId())
			.map(s -> SettlementResponse.from(s, nameOf(split, s.getCalculatedBy())));
	}

	public SettlementResponse get(String splitId, String settlementId, ObjectId userId) {
		Split split = access.requireMember(splitId, userId);
		Settlement s = settlements.findByIdAndSplitId(Ids.parse(settlementId, "Settlement"), split.getId())
			.orElseThrow(() -> ApiException.notFound("Calculation not found"));
		return SettlementResponse.from(s, nameOf(split, s.getCalculatedBy()));
	}

	public List<SettlementHistoryItem> history(String splitId, ObjectId userId) {
		Split split = access.requireMember(splitId, userId);
		return settlements.findBySplitIdOrderByVersionDesc(split.getId())
			.stream()
			.map(s -> new SettlementHistoryItem(s.getId().toHexString(), s.getVersion(), s.getCalculatedAt(),
					nameOf(split, s.getCalculatedBy()), s.getTotalSpendMinor(), s.getExpenseCount(),
					s.getTransfers().size()))
			.toList();
	}

	public Optional<Settlement> latestSnapshot(ObjectId splitId) {
		return settlements.findFirstBySplitIdOrderByVersionDesc(splitId);
	}

	/**
	 * Marks the split SETTLED. Allowed when the latest calculation is up to date and every balance, recomputed
	 * from live data, is exactly zero.
	 */
	public void markSettled(String splitId, ObjectId userId) {
		Split split = access.requireMember(splitId, userId);
		access.requireOpen(split);
		if (split.getLastCalculatedAt() == null || split.isCalculationStale()) {
			throw ApiException.conflict("Calculate the split first, so everyone sees the final numbers.");
		}
		if (!ledger.calculateLive(split).isAllSettled()) {
			throw ApiException.conflict("Some balances are not zero yet. Record the remaining payments first.");
		}
		split.setStatus(SplitStatus.SETTLED);
		splitRepository.save(split);
		String actor = access.actorName(split, userId);
		activity.log(split.getId(), userId, actor, ActivityType.SPLIT_SETTLED,
				actor + " marked the split as settled. Everyone is square!");
	}

	@EventListener
	void onSplitDeleted(SplitDeletedEvent event) {
		settlements.deleteBySplitId(event.splitId());
	}

	private static String nameOf(Split split, ObjectId userId) {
		return split.member(userId).map(Member::getName).orElse("Someone");
	}

	private static Settlement.MemberSummary summary(MemberBalance b, Map<String, Member> members) {
		Member m = members.get(b.userId());
		return new Settlement.MemberSummary(new ObjectId(b.userId()), m == null ? "Former member" : m.getName(),
				m == null ? "#94A3B8" : m.getAvatarColor(), b.paidMinor(), b.shareMinor(), b.paymentsSentMinor(),
				b.paymentsReceivedMinor(), b.netMinor());
	}

	private static Settlement.TransferRecord transfer(Transfer t, Map<String, Member> members) {
		Member from = members.get(t.fromUserId());
		Member to = members.get(t.toUserId());
		return new Settlement.TransferRecord(new ObjectId(t.fromUserId()), from == null ? "Former member" : from.getName(),
				new ObjectId(t.toUserId()), to == null ? "Former member" : to.getName(), t.amountMinor());
	}

}
