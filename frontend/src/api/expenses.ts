import { api } from "./client"
import type { Category, Expense, ExpensePage } from "./types"

export type ExpenseBody = {
  description: string
  amount: string
  categoryId: string
  paidBy?: string
  participants?: string[]
  date: string
  notes?: string
}

export type ExpenseFilters = {
  category?: string
  paidBy?: string
  participant?: string
  from?: string
  to?: string
  q?: string
  sort?: "date_desc" | "date_asc" | "amount_desc" | "amount_asc"
}

export const expensesApi = {
  list: (splitId: string, filters: ExpenseFilters, page = 0, size = 50) =>
    api<ExpensePage>(`/splits/${splitId}/expenses`, { query: { ...filters, page, size } }),
  create: (splitId: string, body: ExpenseBody) => api<Expense>(`/splits/${splitId}/expenses`, { method: "POST", body }),
  update: (splitId: string, id: string, body: ExpenseBody) =>
    api<Expense>(`/splits/${splitId}/expenses/${id}`, { method: "PUT", body }),
  remove: (splitId: string, id: string) => api<void>(`/splits/${splitId}/expenses/${id}`, { method: "DELETE" }),
}

export const categoriesApi = {
  list: (splitId?: string) => api<Category[]>("/categories", { query: { splitId } }),
}

export const expenseKeys = {
  all: (splitId: string) => ["splits", splitId, "expenses"] as const,
  list: (splitId: string, filters: ExpenseFilters) => ["splits", splitId, "expenses", filters] as const,
  categories: (splitId: string) => ["splits", splitId, "categories"] as const,
}
