import { api, download } from "./client"
import type { Report, ReportSummary } from "./types"

export const reportsApi = {
  list: (splitId: string) => api<ReportSummary[]>(`/splits/${splitId}/reports`),
  get: (reportId: string) => api<Report>(`/reports/${reportId}`),
  generate: (splitId: string, body?: { periodStart?: string; periodEnd?: string }) =>
    api<Report>(`/splits/${splitId}/reports`, { method: "POST", body: body ?? {} }),
  downloadPdf: (report: Report) => download(`/reports/${report.id}/pdf`, report.pdfFileName ?? "SplitMate-report.pdf"),
  exportCsv: (splitId: string) => download(`/splits/${splitId}/export.csv`, "SplitMate-expenses.csv"),
}

export const reportKeys = {
  list: (splitId: string) => ["splits", splitId, "reports"] as const,
  detail: (reportId: string) => ["reports", reportId] as const,
}
