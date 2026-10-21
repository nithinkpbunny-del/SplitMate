import { api } from "./client"
import type { Payment, Settlement, SettlementHistoryItem } from "./types"

export const settlementsApi = {
  calculate: (splitId: string) => api<Settlement>(`/splits/${splitId}/calculate`, { method: "POST" }),
  /** null when the split was never calculated (204). */
  latest: async (splitId: string) => (await api<Settlement | undefined>(`/splits/${splitId}/settlements/latest`)) ?? null,
  history: (splitId: string) => api<SettlementHistoryItem[]>(`/splits/${splitId}/settlements`),
  settle: (splitId: string) => api<void>(`/splits/${splitId}/settle`, { method: "POST" }),
}

export type PaymentBody = { fromUserId: string; toUserId: string; amount: string; note?: string }

export const paymentsApi = {
  list: (splitId: string) => api<Payment[]>(`/splits/${splitId}/payments`),
  record: (splitId: string, body: PaymentBody) => api<Payment>(`/splits/${splitId}/payments`, { method: "POST", body }),
  remove: (splitId: string, id: string) => api<void>(`/splits/${splitId}/payments/${id}`, { method: "DELETE" }),
}

export const settlementKeys = {
  latest: (splitId: string) => ["splits", splitId, "settlement", "latest"] as const,
  history: (splitId: string) => ["splits", splitId, "settlement", "history"] as const,
  payments: (splitId: string) => ["splits", splitId, "payments"] as const,
}
