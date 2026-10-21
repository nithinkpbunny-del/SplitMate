package com.splitmate.split;

public enum SplitStatus {
	/** Expenses can be added and changed. */
	OPEN,
	/** Everyone is square; marked by a member after a calculation with all balances at zero. */
	SETTLED,
	/** Hidden from day-to-day use by the owner; read-only. */
	ARCHIVED
}
