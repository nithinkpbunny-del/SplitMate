package com.splitmate.split;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.splitmate.activity.ActivityService;
import com.splitmate.activity.ActivityType;
import com.splitmate.common.ApiException;
import com.splitmate.common.Ids;
import com.splitmate.common.Money;
import com.splitmate.config.AppProperties;
import com.splitmate.expense.ExpenseRepository;
import com.splitmate.settlement.BalanceQueries;
import com.splitmate.settlement.SplitFigures;
import com.splitmate.user.User;
import com.splitmate.user.UserProfileChangedEvent;
import com.splitmate.user.UserService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Splits, membership and invite links. Every write is a single-document operation (no transactions are
 * needed): joins and leaves use conditional updates on the embedded members array.
 */
@Service
public class SplitService {

	private static final Logger log = LoggerFactory.getLogger(SplitService.class);

	private static final String DEFAULT_CURRENCY = "EUR";

	private static final long MAX_PERIOD_DAYS = 366;

	private final SplitRepository splits;

	private final SplitAccess access;

	private final MongoTemplate mongo;

	private final UserService users;

	private final InviteCodeGenerator inviteCodes;

	private final ActivityService activity;

	private final BalanceQueries balances;

	private final ExpenseRepository expenses;

	private final ApplicationEventPublisher events;

	private final AppProperties properties;

	public SplitService(SplitRepository splits, SplitAccess access, MongoTemplate mongo, UserService users,
			InviteCodeGenerator inviteCodes, ActivityService activity, BalanceQueries balances,
			ExpenseRepository expenses, ApplicationEventPublisher events, AppProperties properties) {
		this.splits = splits;
		this.access = access;
		this.mongo = mongo;
		this.users = users;
		this.inviteCodes = inviteCodes;
		this.activity = activity;
		this.balances = balances;
		this.expenses = expenses;
		this.events = events;
		this.properties = properties;
	}

	// ----------------------------------------------------------------- create & read

	public SplitResponse create(ObjectId userId, CreateSplitRequest request) {
		User owner = users.require(userId);
		Split split = new Split();
		split.setName(request.name());
		split.setDescription(request.description());
		split.setEmoji(request.emoji());
		split.setCurrency(normalizeCurrency(request.currency() == null ? DEFAULT_CURRENCY : request.currency()));
		split.setPeriod(request.period() == null ? null : validPeriod(request.period()));
		split.setOwnerId(owner.getId());
		split.setMembers(new ArrayList<>(List.of(new Member(owner.getId(), owner.getName(), owner.getEmail(),
				owner.getAvatarColor(), MemberRole.OWNER, Instant.now()))));
		split.setInviteEnabled(true);
		split.setStatus(SplitStatus.OPEN);
		split = saveWithFreshInviteCode(split);
		activity.log(split.getId(), owner.getId(), owner.getName(), ActivityType.SPLIT_CREATED,
				owner.getName() + " created " + split.getName());
		log.info("Split {} created by {}", split.getId(), userId);
		return toResponse(split, userId);
	}

	public List<SplitSummaryResponse> listMine(ObjectId userId) {
		List<Split> mine = splits.findActiveByMember(userId);
		List<ObjectId> ids = mine.stream().map(Split::getId).toList();
		Map<ObjectId, SplitFigures> figures = balances.figuresFor(ids, userId);
		Map<ObjectId, Instant> lastActivity = activity.latestActivityBySplit(ids);
		return mine.stream().map(split -> {
			SplitFigures f = figures.getOrDefault(split.getId(), SplitFigures.EMPTY);
			List<MemberResponse> active = split.activeMembers().stream().map(MemberResponse::from).toList();
			return new SplitSummaryResponse(split.getId().toHexString(), split.getName(), split.getDescription(),
					split.getEmoji(), split.getCurrency(), split.getStatus(), split.getPeriod(),
					roleOf(split, userId), active, active.size(), f.totalSpendMinor(), f.expenseCount(),
					f.myNetMinor(), split.isCalculationStale(),
					lastActivity.getOrDefault(split.getId(), split.getCreatedAt()), split.getCreatedAt());
		}).toList();
	}

	public SplitResponse get(String splitId, ObjectId userId) {
		return toResponse(access.requireMember(splitId, userId), userId);
	}

	public SplitResponse toResponse(Split split, ObjectId userId) {
		SplitFigures f = balances.figuresFor(split.getId(), userId);
		return new SplitResponse(split.getId().toHexString(), split.getName(), split.getDescription(),
				split.getEmoji(), split.getCurrency(), split.getStatus(), split.getOwnerId().toHexString(),
				roleOf(split, userId), split.getMembers().stream().map(MemberResponse::from).toList(),
				split.getInviteCode(), inviteUrl(split), split.isInviteEnabled(), split.getPeriod(),
				f.totalSpendMinor(), f.expenseCount(), f.myNetMinor(), f.expenseCount() > 0,
				split.getLastCalculatedAt(), split.isCalculationStale(), split.getSettlementVersion(),
				split.getCreatedAt(), split.getUpdatedAt());
	}

	public String inviteUrl(Split split) {
		return properties.normalizedBaseUrl() + "/join/" + split.getInviteCode();
	}

	// ----------------------------------------------------------------- update & delete

	public SplitResponse update(String splitId, ObjectId userId, UpdateSplitRequest request) {
		Split split = access.requireOwner(splitId, userId);
		String actor = access.actorName(split, userId);
		List<String> changed = new ArrayList<>();

		if (request.name() != null && !request.name().equals(split.getName())) {
			split.setName(request.name());
			changed.add("name");
		}
		if (request.description() != null && !Objects.equals(emptyToNull(request.description()), split.getDescription())) {
			split.setDescription(emptyToNull(request.description()));
			changed.add("description");
		}
		if (request.emoji() != null && !Objects.equals(emptyToNull(request.emoji()), split.getEmoji())) {
			split.setEmoji(emptyToNull(request.emoji()));
			changed.add("emoji");
		}
		if (request.currency() != null) {
			String currency = normalizeCurrency(request.currency());
			if (!currency.equals(split.getCurrency())) {
				if (expenses.countBySplitIdAndDeletedFalse(split.getId()) > 0) {
					throw ApiException.conflict("The currency can't change once the split has expenses");
				}
				split.setCurrency(currency);
				changed.add("currency");
			}
		}
		if (Boolean.TRUE.equals(request.clearPeriod()) && split.getPeriod() != null) {
			split.setPeriod(null);
			changed.add("period");
		}
		else if (request.period() != null) {
			SplitPeriod period = validPeriod(request.period());
			if (!period.equals(split.getPeriod())) {
				split.setPeriod(period);
				changed.add("period");
			}
		}

		ActivityType statusChange = null;
		if (request.status() != null && request.status() != split.getStatus()) {
			statusChange = switch (request.status()) {
				case ARCHIVED -> ActivityType.SPLIT_ARCHIVED;
				case OPEN -> ActivityType.SPLIT_REOPENED;
				case SETTLED -> throw ApiException.badRequest("Use 'Mark as settled' to settle a split");
			};
			split.setStatus(request.status());
		}

		split = splits.save(split);
		if (!changed.isEmpty()) {
			activity.log(split.getId(), userId, actor, ActivityType.SPLIT_UPDATED,
					actor + " updated the split's " + String.join(", ", changed), Map.of("fields", changed));
		}
		if (statusChange == ActivityType.SPLIT_ARCHIVED) {
			activity.log(split.getId(), userId, actor, statusChange, actor + " archived the split");
		}
		else if (statusChange == ActivityType.SPLIT_REOPENED) {
			activity.log(split.getId(), userId, actor, statusChange, actor + " reopened the split");
		}
		return toResponse(split, userId);
	}

	/** Deletes an empty split; a split with expenses is archived instead so its history is kept. */
	public DeleteSplitResponse delete(String splitId, ObjectId userId) {
		Split split = access.requireOwner(splitId, userId);
		if (expenses.countBySplitIdAndDeletedFalse(split.getId()) > 0) {
			if (split.getStatus() != SplitStatus.ARCHIVED) {
				split.setStatus(SplitStatus.ARCHIVED);
				splits.save(split);
				String actor = access.actorName(split, userId);
				activity.log(split.getId(), userId, actor, ActivityType.SPLIT_ARCHIVED, actor + " archived the split");
			}
			return new DeleteSplitResponse(false, true,
					"This split has expenses, so it was archived instead of deleted. Its history is kept.");
		}
		splits.delete(split);
		events.publishEvent(new SplitDeletedEvent(split.getId()));
		log.info("Split {} deleted by {}", split.getId(), userId);
		return new DeleteSplitResponse(true, false, "Split deleted");
	}

	// ----------------------------------------------------------------- members

	/**
	 * The owner removes a member, or a member removes themself (leave). Blocked while the member takes
	 * part in any live expense or still has a non-zero balance, because removing them would break the maths.
	 */
	public void removeMember(String splitId, String targetUserId, ObjectId actorId) {
		Split split = access.requireMember(splitId, actorId);
		ObjectId target = Ids.parse(targetUserId, "Member");
		boolean leaving = target.equals(actorId);
		if (!leaving) {
			access.requireOwner(split, actorId);
		}
		Member member = split.member(target)
			.filter(Member::isActive)
			.orElseThrow(() -> ApiException.notFound("Member not found"));
		if (split.isOwner(target)) {
			throw ApiException.conflict("The owner can't leave their own split. Archive it instead.");
		}
		String who = leaving ? "You" : member.getName();
		if (expenses.existsLiveInvolving(split.getId(), target)) {
			throw ApiException.conflict(who + (leaving ? " are" : " is")
					+ " part of expenses in this split. Delete or edit those expenses first, so nobody's share changes.");
		}
		long net = balances.figuresFor(split.getId(), target).myNetMinor();
		if (net != 0) {
			String amount = Money.format(Math.abs(net), split.getCurrency());
			String state = net < 0 ? (leaving ? "still owe " : "still owes ") + amount
					: (leaving ? "are still owed " : "is still owed ") + amount;
			throw ApiException.conflict(who + " " + state + ". Settle up before removing anyone.");
		}

		mongo.updateFirst(query(where("_id").is(split.getId()).and("members.userId").is(target)),
				new Update().set("members.$.active", false), Split.class);

		String actorName = access.actorName(split, actorId);
		if (leaving) {
			activity.log(split.getId(), actorId, actorName, ActivityType.MEMBER_LEFT, actorName + " left the split");
		}
		else {
			activity.log(split.getId(), actorId, actorName, ActivityType.MEMBER_REMOVED,
					actorName + " removed " + member.getName(), Map.of("userId", target.toHexString()));
		}
	}

	// ----------------------------------------------------------------- invites

	public SplitResponse regenerateInvite(String splitId, ObjectId userId) {
		Split split = access.requireOwner(splitId, userId);
		split.setInviteRegeneratedAt(Instant.now());
		split = saveWithFreshInviteCode(split);
		String actor = access.actorName(split, userId);
		activity.log(split.getId(), userId, actor, ActivityType.INVITE_REGENERATED,
				actor + " created a new invite link. The old link no longer works.");
		return toResponse(split, userId);
	}

	public SplitResponse setInviteEnabled(String splitId, ObjectId userId, boolean enabled) {
		Split split = access.requireOwner(splitId, userId);
		if (split.isInviteEnabled() != enabled) {
			split.setInviteEnabled(enabled);
			split = splits.save(split);
			String actor = access.actorName(split, userId);
			activity.log(split.getId(), userId, actor,
					enabled ? ActivityType.INVITE_ENABLED : ActivityType.INVITE_DISABLED,
					actor + (enabled ? " turned the invite link on" : " turned the invite link off"));
		}
		return toResponse(split, userId);
	}

	public InvitePreviewResponse preview(String code) {
		Split split = findInvitable(code);
		String ownerName = split.member(split.getOwnerId()).map(Member::getName).orElse("Someone");
		List<Member> active = split.activeMembers();
		return new InvitePreviewResponse(split.getInviteCode(), split.getName(), split.getEmoji(),
				split.getDescription(), ownerName, active.size(),
				active.stream()
					.map(m -> new InvitePreviewResponse.PreviewMember(firstName(m.getName()), m.getAvatarColor()))
					.toList(),
				split.getStatus(), split.getStatus() == SplitStatus.OPEN);
	}

	/** Adds the caller as a member. Joining twice is a no-op that returns the split. */
	public SplitResponse join(String code, ObjectId userId) {
		User user = users.require(userId);
		Split split = findInvitable(code);
		if (split.isActiveMember(userId)) {
			return toResponse(split, userId);
		}
		if (split.getStatus() != SplitStatus.OPEN) {
			throw ApiException.conflict("This split is " + split.getStatus().name().toLowerCase(Locale.ROOT)
					+ " and is not taking new members.");
		}

		Criteria invitable = where("_id").is(split.getId()).and("inviteCode").is(code).and("inviteEnabled").is(true);
		boolean rejoin = split.member(userId).isPresent();
		long modified;
		if (rejoin) {
			// A former member comes back: reactivate their existing entry.
			modified = mongo.updateFirst(
					new Query(invitable.and("members").elemMatch(where("userId").is(userId).and("active").is(false))),
					new Update().set("members.$.active", true)
						.set("members.$.joinedAt", Instant.now())
						.set("members.$.name", user.getName())
						.set("members.$.avatarColor", user.getAvatarColor()),
					Split.class)
				.getModifiedCount();
		}
		else {
			// $push guarded by "userId not already in members", so concurrent joins cannot add duplicates.
			Member member = new Member(user.getId(), user.getName(), user.getEmail(), user.getAvatarColor(),
					MemberRole.MEMBER, Instant.now());
			modified = mongo.updateFirst(new Query(invitable.and("members.userId").ne(userId)),
					new Update().push("members", member), Split.class)
				.getModifiedCount();
		}

		Split updated = splits.findById(split.getId()).orElseThrow(() -> ApiException.notFound(SplitAccess.NOT_FOUND));
		if (modified == 1) {
			activity.log(split.getId(), userId, user.getName(), ActivityType.MEMBER_JOINED,
					user.getName() + (rejoin ? " rejoined the split" : " joined the split"));
		}
		else if (!updated.isActiveMember(userId)) {
			throw ApiException.notFound("This invite link is invalid or has been turned off");
		}
		return toResponse(updated, userId);
	}

	// ----------------------------------------------------------------- bookkeeping used by other modules

	public void markExpensesChanged(ObjectId splitId) {
		mongo.updateFirst(query(where("_id").is(splitId)), new Update().set("lastExpenseChangeAt", Instant.now()),
				Split.class);
	}

	public void markPaymentsChanged(ObjectId splitId) {
		mongo.updateFirst(query(where("_id").is(splitId)), new Update().set("lastPaymentChangeAt", Instant.now()),
				Split.class);
	}

	/** Atomically increments and returns the next settlement version for the split (findAndModify + $inc). */
	public int nextSettlementVersion(ObjectId splitId) {
		Split updated = mongo.findAndModify(query(where("_id").is(splitId)), new Update().inc("settlementVersion", 1),
				FindAndModifyOptions.options().returnNew(true), Split.class);
		if (updated == null) {
			throw ApiException.notFound(SplitAccess.NOT_FOUND);
		}
		return updated.getSettlementVersion();
	}

	/** Copies a user's new name/colour into every split they belong to (filtered positional update). */
	@EventListener
	public void onProfileChanged(UserProfileChangedEvent event) {
		mongo.updateMulti(query(where("members.userId").is(event.userId())),
				new Update().set("members.$[m].name", event.name())
					.set("members.$[m].avatarColor", event.avatarColor())
					.filterArray(where("m.userId").is(event.userId())),
				Split.class);
	}

	public List<Split> splitsOf(ObjectId userId) {
		return splits.findActiveByMember(userId);
	}

	public Collection<ObjectId> splitIdsOf(ObjectId userId) {
		return splitsOf(userId).stream().map(Split::getId).toList();
	}

	// ----------------------------------------------------------------- helpers

	private Split findInvitable(String code) {
		if (code == null || code.length() != InviteCodeGenerator.LENGTH || !code.chars().allMatch(Character::isLetterOrDigit)) {
			throw ApiException.notFound("This invite link is invalid or has been turned off");
		}
		return splits.findByInviteCode(code)
			.filter(Split::isInviteEnabled)
			.orElseThrow(() -> ApiException.notFound("This invite link is invalid or has been turned off"));
	}

	private Split saveWithFreshInviteCode(Split split) {
		for (int attempt = 0; attempt < 5; attempt++) {
			split.setInviteCode(inviteCodes.next());
			try {
				return splits.save(split);
			}
			catch (DuplicateKeyException ex) {
				log.warn("Invite code collision, retrying");
			}
		}
		throw new IllegalStateException("Could not generate a unique invite code");
	}

	private static MemberRole roleOf(Split split, ObjectId userId) {
		return split.member(userId).map(Member::getRole).orElse(null);
	}

	private static String normalizeCurrency(String code) {
		String upper = code.trim().toUpperCase(Locale.ROOT);
		try {
			Currency.getInstance(upper);
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.badRequest("'" + code + "' is not a known currency code");
		}
		return upper;
	}

	private static SplitPeriod validPeriod(PeriodRequest period) {
		if (period.end().isBefore(period.start())) {
			throw ApiException.badRequest("The period must end on or after its start date");
		}
		if (Duration.between(period.start().atStartOfDay(), period.end().atStartOfDay()).toDays() > MAX_PERIOD_DAYS) {
			throw ApiException.badRequest("A period can be at most one year long");
		}
		return period.toPeriod();
	}

	private static String emptyToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String firstName(String name) {
		String trimmed = name.trim();
		int space = trimmed.indexOf(' ');
		return space < 0 ? trimmed : trimmed.substring(0, space);
	}

}
