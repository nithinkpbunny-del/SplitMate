package com.splitmate.payment;

import com.splitmate.split.SplitDeletedEvent;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Removes a deleted split's payments. */
@Component
class PaymentCleanup {

	private final PaymentRepository payments;

	PaymentCleanup(PaymentRepository payments) {
		this.payments = payments;
	}

	@EventListener
	void onSplitDeleted(SplitDeletedEvent event) {
		payments.deleteBySplitId(event.splitId());
	}

}
