package com.splitmate.report;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

import com.splitmate.category.Category;
import com.splitmate.category.CategoryService;
import com.splitmate.common.Money;
import com.splitmate.expense.Expense;
import com.splitmate.expense.ExpenseService;
import com.splitmate.split.Member;
import com.splitmate.split.Split;
import com.splitmate.split.SplitAccess;

import org.springframework.stereotype.Component;

/**
 * All of a split's expenses as CSV: UTF-8 with a byte order mark (so Excel shows umlauts correctly), RFC 4180
 * quoting, and a leading apostrophe on cells that would otherwise be read as spreadsheet formulas.
 */
@Component
public class CsvExporter {

	private static final String HEADER = "Date,Description,Category,Amount,Currency,Paid by,Shared by,Notes\r\n";

	private final ExpenseService expenses;

	private final CategoryService categories;

	private final SplitAccess access;

	public CsvExporter(ExpenseService expenses, CategoryService categories, SplitAccess access) {
		this.expenses = expenses;
		this.categories = categories;
		this.access = access;
	}

	public record CsvFile(String fileName, byte[] content) {
	}

	public CsvFile export(String splitId, ObjectId userId) {
		Split split = access.requireMember(splitId, userId);
		Map<ObjectId, Category> byId = categories.byIdFor(split.getId());
		Map<ObjectId, String> names = split.getMembers().stream().collect(Collectors.toMap(Member::getUserId, Member::getName));

		StringBuilder csv = new StringBuilder("﻿").append(HEADER);
		for (Expense e : expenses.liveExpenses(split.getId())) {
			Category category = byId.get(e.getCategoryId());
			String sharedBy = e.getParticipants().stream().map(id -> names.getOrDefault(id, "Former member")).collect(Collectors.joining("; "));
			csv.append(cell(e.getDate().toString())).append(',')
				.append(cell(e.getDescription())).append(',')
				.append(cell(category == null ? "" : category.getName())).append(',')
				.append(Money.toDecimalString(e.getAmountMinor())).append(',')
				.append(e.getCurrency()).append(',')
				.append(cell(names.getOrDefault(e.getPaidBy(), "Former member"))).append(',')
				.append(cell(sharedBy)).append(',')
				.append(cell(e.getNotes() == null ? "" : e.getNotes())).append("\r\n");
		}
		return new CsvFile("SplitMate-" + ReportService.slug(split.getName()) + "-expenses.csv",
				csv.toString().getBytes(StandardCharsets.UTF_8));
	}

	static String cell(String value) {
		String v = value == null ? "" : value;
		if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
			v = "'" + v;
		}
		if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
			v = "\"" + v.replace("\"", "\"\"") + "\"";
		}
		return v;
	}

}
