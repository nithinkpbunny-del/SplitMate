package com.splitmate.split;

import java.time.LocalDate;

/** Optional date range a split covers (e.g. one month); used as the default report period. */
public record SplitPeriod(LocalDate start, LocalDate end) {
}
