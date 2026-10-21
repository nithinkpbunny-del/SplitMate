package com.splitmate.expense;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.splitmate.activity.ActivityService;
import com.splitmate.activity.ActivityType;
import com.splitmate.category.CategoryService;
import com.splitmate.common.ApiException;
import com.splitmate.common.Ids;
import com.splitmate.common.Money;
import com.splitmate.settlement.engine.EqualSplit;
import com.splitmate.split.Member;
import com.splitmate.split.Split;
import com.splitmate.split.SplitAccess;
import com.splitmate.split.SplitService;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Expenses: validation, server-side share computation, filters, soft delete and permissions.
 */
@Service
public class ExpenseService {

	private static final int DEFAULT_PAGE_SIZE = 50;

	private static final int MAX_PAGE_SIZE = 200;

	/** The earliest date accepted, to catch typos like 0026. */
	private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);

	/** "Today" for the future-date check; Europe/Berlin is the home of the target users. */
	private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

	private final ExpenseRepository expenses;

	private final MongoTemplate mongo;

	private final SplitAccess access;

	private final SplitService splits;

	private final CategoryService categories;

	private final ActivityService activity;

	public ExpenseService(ExpenseRepository expenses, MongoTemplate mongo, SplitAccess access, SplitService splits,
			CategoryService categories, ActivityService activity) {
		this.expenses = expenses;
		this.mongo = mongo;
		this.access = access;
		this.splits = splits;
		this.categories = categories;
		this.activity = activity;
	}

	// ----------------------------------------------------------------- read

	public ExpensePage list(String splitId, ObjectId userId, ExpenseFilter filter) {
		Split split = access.requireMember(splitId, userId);
		Criteria criteria = filterCriteria(split, filter);

		int size = filter.size() == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(filter.size(), MAX_PAGE_SIZE));
		int page = filter.page() == null ? 0 : Math.max(0, filter.page());
		Query query = new Query(criteria).with(sort(filter.sort())).skip((long) page * size).limit(size);

		List<Expense> items = mongo.find(query, Expense.class);
		long total = mongo.count(new Query(criteria), Expense.class);
		long totalAmount = sumAmount(criteria);
		return new ExpensePage(items.stream().map(e -> ExpenseResponse.from(e, canEdit(split, e, userId))).toList(), page,
				size, total, (int) Math.ceil(total / (double) size), totalAmount);
	}

	public ExpenseResponse get(String splitId, String expenseId, ObjectId userId) {
		Split split = access.requireMember(splitId, userId);
		Expense expense = requireLive(split, expenseId);
		return ExpenseResponse.from(expense, canEdit(split, expense, userId));
	}

	/** All live expenses of a split, oldest first (CSV export, calculations). */
	public List<Expense> liveExpenses(ObjectId splitId) {
		return mongo.find(Query.query(Criteria.where("splitId").is(splitId).and("deleted").is(false))
			.with(Sort.by("date", "createdAt")), Expense.class);
	}

	// ----------------------------------------------------------------- write

	public ExpenseResponse create(String splitId, ObjectId userId, ExpenseRequest request) {
		Split split = access.requireMember(splitId, userId);
		access.requireOpen(split);
		Expense expense = new Expense();
		expense.setSplitId(split.getId());
		expense.setCreatedBy(userId);
		apply(expense, split, userId, request);
		expense = expenses.save(expense);
		splits.markExpensesChanged(split.getId());

		String actor = access.actorName(split, userId);
		activity.log(split.getId(), userId, actor, ActivityType.EXPENSE_ADDED,
				actor + " added " + expense.getDescription() + " (" + Money.format(expense.getAmountMinor(), split.getCurrency()) + ")",
				meta(expense));
		return ExpenseResponse.from(expense, true);
	}

	public ExpenseResponse update(String splitId, String expenseId, ObjectId userId, ExpenseRequest request) {
		Split split = access.requireMember(splitId, userId);
		Expense expense = requireLive(split, expenseId);
		requireCanEdit(split, expense, userId);
		access.requireOpen(split);
		long before = expense.getAmountMinor();
		apply(expense, split, userId, request);
		expense = expenses.save(expense);
		splits.markExpensesChanged(split.getId());

		String actor = access.actorName(split, userId);
		String change = before == expense.getAmountMinor() ? ""
				: " (" + Money.format(before, split.getCurrency()) + " → "
						+ Money.format(expense.getAmountMinor(), split.getCurrency()) + ")";
		activity.log(split.getId(), userId, actor, ActivityType.EXPENSE_UPDATED,
				actor + " edited " + expense.getDescription() + change, meta(expense));
		return ExpenseResponse.from(expense, true);
	}

	/** Soft delete: the document stays (visible in the database) but is excluded from all maths. */
	public void delete(String splitId, String expenseId, ObjectId userId) {
		Split split = access.requireMember(splitId, userId);
		Expense expense = requireLive(split, expenseId);
		requireCanEdit(split, expense, userId);
		access.requireOpen(split);
		expense.setDeleted(true);
		expenses.save(expense);
		splits.markExpensesChanged(split.getId());

		String actor = access.actorName(split, userId);
		activity.log(split.getId(), userId, actor, ActivityType.EXPENSE_DELETED,
				actor + " deleted " + expense.getDescription() + " ("
						+ Money.format(expense.getAmountMinor(), split.getCurrency()) + ")",
				meta(expense));
	}

	// ----------------------------------------------------------------- rules

	/** The creator, the payer and the split owner may change an expense. */
	static boolean canEdit(Split split, Expense expense, ObjectId userId) {
		return expense.getCreatedBy().equals(userId) || expense.getPaidBy().equals(userId) || split.isOwner(userId);
	}

	private static void requireCanEdit(Split split, Expense expense, ObjectId userId) {
		if (!canEdit(split, expense, userId)) {
			throw ApiException.forbidden("Only the person who added it, the payer or the split owner can change this expense");
		}
	}

	private Expense requireLive(Split split, String expenseId) {
		return expenses.findByIdAndSplitIdAndDeletedFalse(Ids.parse(expenseId, "Expense"), split.getId())
			.orElseThrow(() -> ApiException.notFound("Expense not found"));
	}

	/** Validates the request against the split and fills the expense, computing shares on the server. */
	private void apply(Expense expense, Split split, ObjectId userId, ExpenseRequest request) {
		long amount = Money.parse(request.amount());
		ObjectId categoryId = categories.requireUsable(Ids.parseField(request.categoryId(), "categoryId"), split.getId())
			.getId();

		Set<ObjectId> activeIds = new LinkedHashSet<>(split.activeMembers().stream().map(Member::getUserId).toList());
		ObjectId paidBy = request.paidBy() == null || request.paidBy().isBlank() ? userId
				: Ids.parseField(request.paidBy(), "paidBy");
		if (!activeIds.contains(paidBy)) {
			throw ApiException.badRequest("The payer must be a member of this split");
		}

		List<ObjectId> participants;
		if (request.participants() == null || request.participants().isEmpty()) {
			participants = new ArrayList<>(activeIds);
		}
		else {
			Set<ObjectId> chosen = new LinkedHashSet<>();
			for (String id : request.participants()) {
				ObjectId participant = Ids.parseField(id, "participants");
				if (!activeIds.contains(participant)) {
					throw ApiException.badRequest("Everyone sharing an expense must be a member of this split");
				}
				chosen.add(participant);
			}
			participants = new ArrayList<>(chosen);
		}

		LocalDate today = LocalDate.now(ZONE);
		if (request.date().isAfter(today.plusDays(1))) {
			throw ApiException.badRequest("The date can't be more than a day in the future");
		}
		if (request.date().isBefore(MIN_DATE)) {
			throw ApiException.badRequest("That date looks wrong");
		}

		Map<String, Long> shares = EqualSplit.shares(amount, participants.stream().map(ObjectId::toHexString).toList());
		participants.sort(null);

		expense.setDescription(request.description());
		expense.setAmountMinor(amount);
		expense.setCurrency(split.getCurrency());
		expense.setCategoryId(categoryId);
		expense.setPaidBy(paidBy);
		expense.setSplitType(SplitType.EQUAL);
		expense.setParticipants(participants);
		expense.setShares(shares.entrySet().stream().map(e -> new Share(new ObjectId(e.getKey()), e.getValue())).toList());
		expense.setDate(request.date());
		expense.setNotes(request.notes());
	}

	private Criteria filterCriteria(Split split, ExpenseFilter filter) {
		Criteria criteria = Criteria.where("splitId").is(split.getId()).and("deleted").is(false);
		if (has(filter.category())) {
			criteria = criteria.and("categoryId").is(Ids.parseField(filter.category(), "category"));
		}
		if (has(filter.paidBy())) {
			criteria = criteria.and("paidBy").is(Ids.parseField(filter.paidBy(), "paidBy"));
		}
		if (has(filter.participant())) {
			criteria = criteria.and("participants").is(Ids.parseField(filter.participant(), "participant"));
		}
		if (filter.from() != null || filter.to() != null) {
			Criteria date = criteria.and("date");
			if (filter.from() != null) {
				date = date.gte(filter.from());
			}
			if (filter.to() != null) {
				date = date.lte(filter.to());
			}
			criteria = date;
		}
		if (has(filter.q())) {
			String q = filter.q().trim();
			if (q.length() > 80) {
				q = q.substring(0, 80);
			}
			criteria = criteria.and("description").regex(Pattern.quote(q), "i");
		}
		return criteria;
	}

	private long sumAmount(Criteria criteria) {
		Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
				Aggregation.group().sum("amountMinor").as("total"));
		Document row = mongo.aggregate(aggregation, Expense.class, Document.class).getUniqueMappedResult();
		return row == null ? 0 : ((Number) row.get("total")).longValue();
	}

	private static Sort sort(String sort) {
		return switch (sort == null ? "date_desc" : sort) {
			case "date_asc" -> Sort.by(Sort.Order.asc("date"), Sort.Order.asc("createdAt"));
			case "amount_desc" -> Sort.by(Sort.Order.desc("amountMinor"), Sort.Order.desc("date"));
			case "amount_asc" -> Sort.by(Sort.Order.asc("amountMinor"), Sort.Order.desc("date"));
			default -> Sort.by(Sort.Order.desc("date"), Sort.Order.desc("createdAt"));
		};
	}

	private static Map<String, Object> meta(Expense expense) {
		return Map.of("expenseId", expense.getId().toHexString(), "amountMinor", expense.getAmountMinor());
	}

	private static boolean has(String value) {
		return value != null && !value.isBlank();
	}

}
