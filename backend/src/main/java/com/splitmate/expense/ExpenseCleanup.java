package com.splitmate.expense;

import com.splitmate.split.SplitDeletedEvent;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Removes a deleted split's (soft-deleted) expenses. */
@Component
class ExpenseCleanup {

	private final ExpenseRepository expenses;

	ExpenseCleanup(ExpenseRepository expenses) {
		this.expenses = expenses;
	}

	@EventListener
	void onSplitDeleted(SplitDeletedEvent event) {
		expenses.deleteBySplitId(event.splitId());
	}

}
