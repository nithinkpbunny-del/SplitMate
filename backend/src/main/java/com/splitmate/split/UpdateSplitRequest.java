package com.splitmate.split;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update by the owner. Null fields are unchanged; {@code clearPeriod=true} removes the period;
 * {@code status} may be ARCHIVED (archive) or OPEN (reopen).
 */
public record UpdateSplitRequest(
		@Size(min = 3, max = 60, message = "Name must be 3 to 60 characters") String name,
		@Size(max = 140, message = "Description can be at most 140 characters") String description,
		@Size(max = 16, message = "Pick a single emoji") String emoji,
		@Pattern(regexp = "[A-Za-z]{3}", message = "Currency must be a 3-letter code like EUR") String currency,
		@Valid PeriodRequest period,
		Boolean clearPeriod,
		SplitStatus status) {

	public UpdateSplitRequest {
		name = name == null ? null : name.trim();
		description = description == null ? null : description.trim();
		emoji = emoji == null ? null : emoji.trim();
	}

}
