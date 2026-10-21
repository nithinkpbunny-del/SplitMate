import { useMutation, useQueryClient, type InfiniteData, type QueryKey } from "@tanstack/react-query"
import { toast } from "sonner"

import { expenseKeys, expensesApi, type ExpenseBody, type ExpenseFilters } from "@/api/expenses"
import { splitKeys } from "@/api/splits"
import type { Expense, ExpensePage } from "@/api/types"
import { equalShares, formatMoney, parseMoney } from "@/lib/money"

type Lists = [QueryKey, InfiniteData<ExpensePage> | undefined][]

function isUnfiltered(key: QueryKey): boolean {
  const filters = key[3] as ExpenseFilters | undefined
  return !filters || Object.values(filters).every((v) => v === undefined || v === "" || v === "date_desc")
}

/** Builds the expense the server will create, so it can be shown instantly. */
function optimisticExpense(splitId: string, body: ExpenseBody, currency: string, me: string): Expense {
  const parsed = parseMoney(body.amount)
  const amount = parsed.ok ? parsed.minor : 0
  const participants = [...(body.participants ?? [])].sort()
  const shares = equalShares(amount, participants.length)
  const now = new Date().toISOString()
  return {
    id: `optimistic-${crypto.randomUUID()}`,
    splitId,
    description: body.description,
    amountMinor: amount,
    currency,
    categoryId: body.categoryId,
    paidBy: body.paidBy ?? me,
    splitType: "EQUAL",
    participants,
    shares: participants.map((userId, i) => ({ userId, amountMinor: shares[i] })),
    date: body.date,
    notes: body.notes ?? null,
    createdBy: me,
    canEdit: false,
    createdAt: now,
    updatedAt: now,
  }
}

export function useExpenseMutations(splitId: string, currency: string, me: string) {
  const queryClient = useQueryClient()
  const listsKey = expenseKeys.all(splitId)

  const refreshEverything = () => {
    queryClient.invalidateQueries({ queryKey: listsKey })
    queryClient.invalidateQueries({ queryKey: splitKeys.detail(splitId) })
    queryClient.invalidateQueries({ queryKey: splitKeys.activity(splitId) })
    queryClient.invalidateQueries({ queryKey: splitKeys.all, exact: true })
  }

  const snapshot = async (): Promise<Lists> => {
    await queryClient.cancelQueries({ queryKey: listsKey })
    return queryClient.getQueriesData<InfiniteData<ExpensePage>>({ queryKey: listsKey })
  }
  const rollback = (lists?: Lists) => lists?.forEach(([key, data]) => queryClient.setQueryData(key, data))

  const create = useMutation({
    mutationFn: (body: ExpenseBody) => expensesApi.create(splitId, body),
    onMutate: async (body) => {
      const lists = await snapshot()
      const temp = optimisticExpense(splitId, body, currency, me)
      for (const [key, data] of lists) {
        if (!data || !isUnfiltered(key)) continue
        queryClient.setQueryData<InfiniteData<ExpensePage>>(key, {
          ...data,
          pages: data.pages.map((page, i) =>
            i === 0
              ? { ...page, items: [temp, ...page.items], totalItems: page.totalItems + 1, totalAmountMinor: page.totalAmountMinor + temp.amountMinor }
              : page,
          ),
        })
      }
      return { lists }
    },
    onError: (_err, _body, ctx) => rollback(ctx?.lists),
    onSuccess: (expense) => toast.success(`Added ${expense.description} · ${formatMoney(expense.amountMinor, expense.currency)}`),
    onSettled: refreshEverything,
  })

  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: ExpenseBody }) => expensesApi.update(splitId, id, body),
    onSuccess: (expense) => toast.success(`Saved ${expense.description}`),
    onSettled: refreshEverything,
  })

  const remove = useMutation({
    mutationFn: (expense: Expense) => expensesApi.remove(splitId, expense.id),
    onMutate: async (expense) => {
      const lists = await snapshot()
      for (const [key, data] of lists) {
        if (!data) continue
        queryClient.setQueryData<InfiniteData<ExpensePage>>(key, {
          ...data,
          pages: data.pages.map((page) =>
            page.items.some((e) => e.id === expense.id)
              ? {
                  ...page,
                  items: page.items.filter((e) => e.id !== expense.id),
                  totalItems: page.totalItems - 1,
                  totalAmountMinor: page.totalAmountMinor - expense.amountMinor,
                }
              : page,
          ),
        })
      }
      return { lists }
    },
    onError: (_err, _expense, ctx) => rollback(ctx?.lists),
    onSuccess: (_data, expense) => toast.success(`Deleted ${expense.description}`),
    onSettled: refreshEverything,
  })

  return { create, update, remove }
}
