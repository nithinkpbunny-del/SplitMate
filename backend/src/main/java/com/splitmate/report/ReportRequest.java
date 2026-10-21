package com.splitmate.report;

import java.time.LocalDate;

/** Optional period; defaults to the split's period, otherwise the first and last expense dates. */
public record ReportRequest(LocalDate periodStart, LocalDate periodEnd) {
}
