package com.splitmate.settlement;

/** Headline numbers for one split from one member's point of view. */
public record SplitFigures(long totalSpendMinor, long expenseCount, long myPaidMinor, long myShareMinor,
		long myPaymentsSentMinor, long myPaymentsReceivedMinor) {

	public static final SplitFigures EMPTY = new SplitFigures(0, 0, 0, 0, 0, 0);

	/** net = paid − share + payments sent − payments received; positive means the group owes them. */
	public long myNetMinor() {
		return myPaidMinor - myShareMinor + myPaymentsSentMinor - myPaymentsReceivedMinor;
	}

}
