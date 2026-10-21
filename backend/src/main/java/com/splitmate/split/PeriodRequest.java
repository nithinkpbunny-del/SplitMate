package com.splitmate.split;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record PeriodRequest(@NotNull(message = "Choose a start date") LocalDate start,
		@NotNull(message = "Choose an end date") LocalDate end) {

	SplitPeriod toPeriod() {
		return new SplitPeriod(start, end);
	}

}
