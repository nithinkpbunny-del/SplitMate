package com.splitmate.split;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSplitRequest(
		@NotBlank(message = "Give the split a name") @Size(min = 3, max = 60, message = "Name must be 3 to 60 characters") String name,
		@Size(max = 140, message = "Description can be at most 140 characters") String description,
		@Size(max = 16, message = "Pick a single emoji") String emoji,
		@Pattern(regexp = "[A-Za-z]{3}", message = "Currency must be a 3-letter code like EUR") String currency,
		@Valid PeriodRequest period) {

	public CreateSplitRequest {
		name = name == null ? null : name.trim();
		description = description == null || description.isBlank() ? null : description.trim();
		emoji = emoji == null || emoji.isBlank() ? null : emoji.trim();
	}

}
