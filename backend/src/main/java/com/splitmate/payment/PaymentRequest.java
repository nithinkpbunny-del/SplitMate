package com.splitmate.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentRequest(
		@NotBlank(message = "Who paid?") String fromUserId,
		@NotBlank(message = "Who was paid?") String toUserId,
		@NotBlank(message = "Enter an amount") @Size(max = 20) String amount,
		@Size(max = 80, message = "Note can be at most 80 characters") String note,
		String settlementId) {

	public PaymentRequest {
		note = note == null || note.isBlank() ? null : note.trim();
	}

}
