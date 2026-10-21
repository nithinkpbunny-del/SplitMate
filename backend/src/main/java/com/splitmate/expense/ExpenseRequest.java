package com.splitmate.expense;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update body. {@code amount} is a string so European input like "86,40" survives JSON;
 * {@code paidBy} defaults to the caller and {@code participants} to every active member.
 */
public record ExpenseRequest(
		@NotBlank(message = "Add a short description") @Size(max = 80, message = "Description can be at most 80 characters") String description,
		@NotBlank(message = "Enter an amount") @Size(max = 20, message = "That amount is too long") String amount,
		@NotBlank(message = "Pick a category") String categoryId,
		String paidBy,
		@Size(max = 100) List<String> participants,
		@NotNull(message = "Pick a date") LocalDate date,
		@Size(max = 500, message = "Notes can be at most 500 characters") String notes) {

	public ExpenseRequest {
		description = description == null ? null : description.trim();
		notes = notes == null || notes.isBlank() ? null : notes.trim();
	}

}
