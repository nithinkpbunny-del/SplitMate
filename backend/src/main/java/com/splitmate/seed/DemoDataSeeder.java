package com.splitmate.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.splitmate.activity.ActivityLog;
import com.splitmate.activity.ActivityType;
import com.splitmate.category.CategoryRepository;
import com.splitmate.common.Money;
import com.splitmate.expense.Expense;
import com.splitmate.expense.ExpenseRequest;
import com.splitmate.expense.ExpenseService;
import com.splitmate.payment.PaymentRequest;
import com.splitmate.payment.PaymentService;
import com.splitmate.settlement.SettlementResponse;
import com.splitmate.settlement.SettlementService;
import com.splitmate.split.CreateSplitRequest;
import com.splitmate.split.PeriodRequest;
import com.splitmate.split.SplitResponse;
import com.splitmate.split.SplitService;
import com.splitmate.user.User;
import com.splitmate.user.UserRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Demo data for the live demo, only with {@code --spring.profiles.active=demo}.
 *
 * <p>Idempotent: if {@code aarav@demo.com} already exists it does nothing. Everything is created through the
 * same services the API uses, so documents, activity logs and balances are exactly what a real session would
 * produce. Afterwards, expense dates and the early timeline entries are backdated so the history looks lived in.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@Order(10)
public class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	static final String PASSWORD = "Demo@1234";

	private record Person(String key, String name, String email, String color) {
	}

	private static final List<Person> PEOPLE = List.of(
			new Person("aarav", "Aarav Mehta", "aarav@demo.com", "#F59E0B"),
			new Person("isha", "Isha Rao", "isha@demo.com", "#F472B6"),
			new Person("rohan", "Rohan Nair", "rohan@demo.com", "#60A5FA"),
			new Person("sneha", "Sneha Iyer", "sneha@demo.com", "#34D399"));

	/** One expense of a demo split. {@code with} is "all" or a comma separated list of person keys. */
	private record Spec(String date, String description, String amount, String category, String payer, String with,
			String notes) {
	}

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final SplitService splits;

	private final ExpenseService expenses;

	private final SettlementService settlements;

	private final PaymentService payments;

	private final CategoryRepository categories;

	private final MongoTemplate mongo;

	private final DemoReports reports;

	public DemoDataSeeder(UserRepository users, PasswordEncoder passwordEncoder, SplitService splits,
			ExpenseService expenses, SettlementService settlements, PaymentService payments,
			CategoryRepository categories, MongoTemplate mongo, DemoReports reports) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.splits = splits;
		this.expenses = expenses;
		this.settlements = settlements;
		this.payments = payments;
		this.categories = categories;
		this.mongo = mongo;
		this.reports = reports;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (users.existsByEmail(PEOPLE.getFirst().email())) {
			log.info("Demo data already present, skipping the seeder");
			return;
		}
		log.info("Seeding demo data ...");
		Map<String, User> byKey = new LinkedHashMap<>();
		String hash = passwordEncoder.encode(PASSWORD);
		for (Person p : PEOPLE) {
			byKey.put(p.key(), users.save(new User(p.name(), p.email(), hash, p.color())));
		}

		// September first, so October (created later) is the newest split on the dashboard.
		SplitResponse september = seedSeptember(byKey);
		SplitResponse october = seedOctober(byKey);
		reports.generateFor(october.id(), byKey.get("aarav").getId());

		log.info("""

				Demo data ready. Log in with any of these (password {}):
				  aarav@demo.com  (owner of both splits)
				  isha@demo.com
				  rohan@demo.com
				  sneha@demo.com
				Splits: "{}" (open, has a calculation, a payment and a report) and "{}" (settled).
				""", PASSWORD, october.name(), september.name());
	}

	// ----------------------------------------------------------------- October (the live demo split)

	private SplitResponse seedOctober(Map<String, User> u) {
		SplitResponse split = createSplit(u, "October month split", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
		List<Spec> specs = List.of(
				new Spec("2026-10-01", "Rent October", "1600.00", "rent", "aarav", "all", "Bank transfer to the landlord"),
				new Spec("2026-10-02", "Vodafone internet", "39.99", "utilities", "isha", "all", null),
				new Spec("2026-10-03", "Vattenfall electricity", "82.40", "utilities", "aarav", "all", null),
				new Spec("2026-10-03", "Rewe groceries", "54.30", "groceries", "rohan", "all", null),
				new Spec("2026-10-05", "Spotify Family", "17.99", "subscriptions", "aarav", "all", null),
				new Spec("2026-10-05", "Netflix", "13.99", "subscriptions", "sneha", "sneha,isha", "Sneha and Isha share the account"),
				new Spec("2026-10-06", "Lidl weekly groceries", "86.40", "groceries", "isha", "all", null),
				new Spec("2026-10-07", "Deutschlandticket Rohan + Sneha", "116.00", "transport", "rohan", "rohan,sneha", null),
				new Spec("2026-10-07", "BVG single trips", "12.40", "transport", "aarav", "aarav,isha", null),
				new Spec("2026-10-08", "dm drugstore", "23.75", "household", "sneha", "all", "Toilet paper, soap, sponges"),
				new Spec("2026-10-09", "Pizza night", "37.50", "dining", "rohan", "rohan,sneha,isha", null),
				new Spec("2026-10-10", "Aldi groceries", "47.85", "groceries", "sneha", "all", null),
				new Spec("2026-10-10", "Apotheke cold medicine", "14.60", "health", "sneha", "sneha,isha", null),
				new Spec("2026-10-11", "Dinner at Monsieur Vuong", "68.50", "dining", "isha", "isha,rohan,aarav", "Aarav's birthday dinner"),
				new Spec("2026-10-12", "IKEA shelf and lamp", "41.90", "household", "aarav", "all", null),
				new Spec("2026-10-13", "Rewe groceries", "61.15", "groceries", "aarav", "all", null),
				new Spec("2026-10-14", "Cinema CinemaxX", "32.00", "entertainment", "isha", "isha,aarav,rohan", null),
				new Spec("2026-10-15", "Bed sheets for the guest room", "29.99", "shopping", "isha", "all", null),
				new Spec("2026-10-15", "Späti drinks", "18.90", "dining", "rohan", "rohan,aarav,sneha", null),
				new Spec("2026-10-16", "FlixBus Berlin to Prague", "96.00", "travel", "rohan", "aarav,rohan,sneha", "Weekend trip to Prague"),
				new Spec("2026-10-17", "Airbnb Prague (2 nights)", "210.00", "travel", "aarav", "aarav,rohan,sneha", null),
				new Spec("2026-10-17", "Dinner at Lokál Prague", "84.60", "dining", "sneha", "aarav,rohan,sneha", null),
				new Spec("2026-10-18", "Prague Castle tickets", "45.00", "entertainment", "rohan", "aarav,rohan,sneha", null),
				new Spec("2026-10-18", "Prague metro and tram", "18.50", "transport", "aarav", "aarav,rohan,sneha", null),
				new Spec("2026-10-18", "Coffee at Café Slavia", "22.30", "dining", "sneha", "aarav,rohan,sneha", null),
				new Spec("2026-10-19", "Edeka groceries", "38.45", "groceries", "isha", "all", null),
				new Spec("2026-10-19", "Cleaning supplies", "16.80", "household", "isha", "all", null),
				new Spec("2026-10-20", "Lidl groceries", "73.20", "groceries", "rohan", "all", null),
				new Spec("2026-10-20", "Spice Village Indian groceries", "44.60", "groceries", "sneha", "all", "Dal, rice, masala"),
				new Spec("2026-10-20", "Laundromat", "8.00", "household", "aarav", "all", null));
		addExpenses(split, u, specs);

		// One earlier calculation, then one real-world payment: the split is now "stale", so the
		// demo can show the Recalculate banner straight away.
		ObjectId owner = u.get("aarav").getId();
		SettlementResponse calc = settlements.calculate(split.id(), owner);
		SettlementResponse.TransferResponse first = calc.transfers().getFirst();
		payments.record(split.id(), new ObjectId(first.fromUserId()),
				new PaymentRequest(first.fromUserId(), first.toUserId(), Money.toDecimalString(first.amountMinor()),
						"PayPal", calc.id()));

		backdateStart(split.id(), LocalDate.of(2026, 10, 1));
		return split;
	}

	// ----------------------------------------------------------------- September (already settled)

	private SplitResponse seedSeptember(Map<String, User> u) {
		SplitResponse split = createSplit(u, "September month split", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
		List<Spec> specs = List.of(
				new Spec("2026-09-01", "Rent September", "1600.00", "rent", "aarav", "all", null),
				new Spec("2026-09-02", "Vodafone internet", "39.99", "utilities", "isha", "all", null),
				new Spec("2026-09-04", "Vattenfall electricity", "71.20", "utilities", "aarav", "all", null),
				new Spec("2026-09-05", "Lidl weekly groceries", "78.90", "groceries", "rohan", "all", null),
				new Spec("2026-09-06", "Spotify Family", "17.99", "subscriptions", "aarav", "all", null),
				new Spec("2026-09-07", "Deutschlandticket Rohan + Sneha", "116.00", "transport", "rohan", "rohan,sneha", null),
				new Spec("2026-09-10", "Rewe groceries", "64.35", "groceries", "sneha", "all", null),
				new Spec("2026-09-12", "Dinner at Maharani", "92.40", "dining", "isha", "all", null),
				new Spec("2026-09-14", "dm drugstore", "27.10", "household", "sneha", "all", null),
				new Spec("2026-09-19", "Aldi groceries", "51.25", "groceries", "aarav", "all", null),
				new Spec("2026-09-21", "Cinema CinemaxX", "28.00", "entertainment", "rohan", "rohan,isha,sneha", null),
				new Spec("2026-09-26", "Edeka groceries", "58.70", "groceries", "isha", "all", null),
				new Spec("2026-09-28", "Cleaning supplies", "19.40", "household", "aarav", "all", null));
		addExpenses(split, u, specs);

		// Calculate, everyone pays what they owe, calculate again, close the split.
		ObjectId owner = u.get("aarav").getId();
		SettlementResponse calc = settlements.calculate(split.id(), owner);
		for (SettlementResponse.TransferResponse t : calc.transfers()) {
			payments.record(split.id(), new ObjectId(t.fromUserId()), new PaymentRequest(t.fromUserId(), t.toUserId(),
					Money.toDecimalString(t.amountMinor()), "Bank transfer", calc.id()));
		}
		settlements.calculate(split.id(), owner);
		settlements.markSettled(split.id(), owner);
		reports.generateFor(split.id(), owner);

		backdateStart(split.id(), LocalDate.of(2026, 9, 1));
		backdateEnd(split.id(), LocalDate.of(2026, 9, 30));
		return split;
	}

	// ----------------------------------------------------------------- helpers

	private SplitResponse createSplit(Map<String, User> u, String name, LocalDate start, LocalDate end) {
		ObjectId owner = u.get("aarav").getId();
		SplitResponse split = splits.create(owner,
				new CreateSplitRequest(name, "Flat 4B, Berlin", "🏠", "EUR", new PeriodRequest(start, end)));
		for (Person p : PEOPLE.subList(1, PEOPLE.size())) {
			splits.join(split.inviteCode(), u.get(p.key()).getId());
		}
		return split;
	}

	private void addExpenses(SplitResponse split, Map<String, User> u, List<Spec> specs) {
		for (Spec s : specs) {
			ObjectId payer = u.get(s.payer()).getId();
			String categoryId = categories.findBySplitIdIsNullAndKey(s.category()).orElseThrow().getId().toHexString();
			List<String> participants = s.with().equals("all") ? null
					: Arrays.stream(s.with().split(",")).map(k -> u.get(k).getId().toHexString()).toList();
			expenses.create(split.id(), payer, new ExpenseRequest(s.description(), s.amount(), categoryId,
					payer.toHexString(), participants, LocalDate.parse(s.date()), s.notes()));
		}
	}

	/**
	 * The services stamp everything with "now". Move expenses to their own dates and the opening timeline
	 * entries (created, joined, expense added) to the period start, leaving calculations, payments and
	 * reports at their real time.
	 */
	private void backdateStart(String splitId, LocalDate periodStart) {
		ObjectId id = new ObjectId(splitId);
		Map<ObjectId, Instant> expenseTimes = new LinkedHashMap<>();
		for (Expense e : mongo.find(new Query(where("splitId").is(id)), Expense.class)) {
			Instant at = e.getDate().atTime(LocalTime.of(18, 30)).toInstant(ZoneOffset.UTC);
			expenseTimes.put(e.getId(), at);
			mongo.updateFirst(new Query(where("_id").is(e.getId())),
					new Update().set("createdAt", at).set("updatedAt", at), Expense.class);
		}
		int joined = 0;
		List<ActivityLog> logs = mongo.find(new Query(where("splitId").is(id)), ActivityLog.class);
		for (ActivityLog entry : logs) {
			Instant at = null;
			if (entry.getType() == ActivityType.SPLIT_CREATED) {
				at = periodStart.atTime(8, 0).toInstant(ZoneOffset.UTC);
			}
			else if (entry.getType() == ActivityType.MEMBER_JOINED) {
				at = periodStart.atTime(8, 20 + 15 * joined++).toInstant(ZoneOffset.UTC);
			}
			else if (entry.getType() == ActivityType.EXPENSE_ADDED && entry.getMetadata() != null) {
				at = expenseTimes.get(new ObjectId((String) entry.getMetadata().get("expenseId")));
			}
			if (at != null) {
				mongo.updateFirst(new Query(where("_id").is(entry.getId())), new Update().set("createdAt", at),
						ActivityLog.class);
			}
		}
	}

	/**
	 * For a finished month: shift calculations, payments, the settle entry and the split's own change
	 * markers back to the end of the period, preserving their order (so nothing looks stale).
	 */
	private void backdateEnd(String splitId, LocalDate periodEnd) {
		ObjectId id = new ObjectId(splitId);
		Instant target = periodEnd.atTime(20, 0).toInstant(ZoneOffset.UTC);
		List<ActivityLog> logs = mongo.find(new Query(where("splitId").is(id)), ActivityLog.class);
		Instant earliest = logs.stream()
			.filter(l -> l.getType() == ActivityType.SPLIT_CALCULATED)
			.map(ActivityLog::getCreatedAt)
			.min(Instant::compareTo)
			.orElseThrow();
		long shift = java.time.Duration.between(target, earliest).toMillis();

		List<ActivityType> late = new ArrayList<>(List.of(ActivityType.SPLIT_CALCULATED, ActivityType.PAYMENT_RECORDED,
				ActivityType.SPLIT_SETTLED, ActivityType.REPORT_GENERATED));
		for (ActivityLog entry : logs) {
			if (late.contains(entry.getType())) {
				mongo.updateFirst(new Query(where("_id").is(entry.getId())),
						new Update().set("createdAt", entry.getCreatedAt().minusMillis(shift)), ActivityLog.class);
			}
		}
		shiftField("settlements", "calculatedAt", id, shift);
		shiftField("payments", "createdAt", id, shift);
		shiftField("reports", "generatedAt", id, shift);
		for (String field : List.of("lastCalculatedAt", "lastExpenseChangeAt", "lastPaymentChangeAt")) {
			shiftField("splits", field, new ObjectId(splitId), shift, "_id");
		}
	}

	private void shiftField(String collection, String field, ObjectId splitId, long shiftMillis) {
		shiftField(collection, field, splitId, shiftMillis, "splitId");
	}

	private void shiftField(String collection, String field, ObjectId id, long shiftMillis, String keyField) {
		for (org.bson.Document doc : mongo.getCollection(collection).find(new org.bson.Document(keyField, id))) {
			java.util.Date value = doc.getDate(field);
			if (value != null) {
				mongo.getCollection(collection)
					.updateOne(new org.bson.Document("_id", doc.getObjectId("_id")), new org.bson.Document("$set",
							new org.bson.Document(field, new java.util.Date(value.getTime() - shiftMillis))));
			}
		}
	}

}
