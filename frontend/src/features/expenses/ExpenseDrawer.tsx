import { zodResolver } from "@hookform/resolvers/zod"
import { AnimatePresence, motion } from "framer-motion"
import { Check, Loader2, Trash2 } from "lucide-react"
import { useEffect, useMemo, useState } from "react"
import { Controller, useForm, useWatch } from "react-hook-form"
import { z } from "zod"

import type { ExpenseBody } from "@/api/expenses"
import type { Category, Expense, Member } from "@/api/types"
import { CategoryIcon } from "@/components/CategoryIcon"
import { ConfirmDialog } from "@/components/ConfirmDialog"
import { FormField, inputClass } from "@/components/FormField"
import { MemberAvatar } from "@/components/MemberAvatar"
import { ResponsiveSheet } from "@/components/ResponsiveSheet"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { firstName, toIsoDate, todayIso } from "@/lib/format"
import { currencySymbol, equalShares, formatMoney, parseMoney, toInputString } from "@/lib/money"
import { cn } from "@/lib/utils"

// Mirrors the backend validation in ExpenseRequest / ExpenseService.
const schema = z.object({
  amount: z.string().superRefine((value, ctx) => {
    const parsed = parseMoney(value)
    if (!parsed.ok) ctx.addIssue({ code: "custom", message: parsed.error })
  }),
  description: z.string().trim().min(1, "Add a short description").max(80, "Description can be at most 80 characters"),
  categoryId: z.string().min(1, "Pick a category"),
  paidBy: z.string().min(1),
  participants: z.array(z.string()).min(1, "Pick at least one person to share it with"),
  date: z
    .string()
    .min(1, "Pick a date")
    .refine((d) => d <= maxDate(), "The date can't be more than a day in the future"),
  notes: z.string().max(500, "Notes can be at most 500 characters"),
})

type Values = z.infer<typeof schema>

function maxDate(): string {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  return toIsoDate(d)
}

export function ExpenseDrawer({
  open,
  onOpenChange,
  expense,
  members,
  categories,
  currency,
  me,
  onSubmit,
  onDelete,
  pending,
  readOnly = false,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  expense: Expense | null
  members: Member[]
  categories: Category[]
  currency: string
  me: string
  onSubmit: (body: ExpenseBody) => Promise<unknown>
  onDelete?: (expense: Expense) => void
  pending: boolean
  readOnly?: boolean
}) {
  const active = useMemo(() => members.filter((m) => m.active), [members])
  const everyone = useMemo(() => active.map((m) => m.userId), [active])
  const [confirmDelete, setConfirmDelete] = useState(false)

  const defaults = useMemo<Values>(
    () =>
      expense
        ? {
            amount: toInputString(expense.amountMinor),
            description: expense.description,
            categoryId: expense.categoryId,
            paidBy: expense.paidBy,
            participants: expense.participants,
            date: expense.date,
            notes: expense.notes ?? "",
          }
        : {
            amount: "",
            description: "",
            categoryId: categories.find((c) => c.key === "groceries")?.id ?? categories[0]?.id ?? "",
            paidBy: me,
            participants: everyone,
            date: todayIso(),
            notes: "",
          },
    [expense, categories, me, everyone],
  )

  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: defaults })
  const { errors } = form.formState

  useEffect(() => {
    if (open) form.reset(defaults)
  }, [open, defaults, form])

  const [amount, participants, paidBy] = useWatch({ control: form.control, name: ["amount", "participants", "paidBy"] })
  const parsed = parseMoney(amount ?? "")
  const sortedParticipants = [...(participants ?? [])].sort()
  const shares = parsed.ok ? equalShares(parsed.minor, sortedParticipants.length) : []
  const memberById = new Map(members.map((m) => [m.userId, m]))

  const submit = form.handleSubmit(async (v) => {
    const body: ExpenseBody = {
      amount: v.amount,
      description: v.description,
      categoryId: v.categoryId,
      paidBy: v.paidBy,
      participants: v.participants,
      date: v.date,
      notes: v.notes || undefined,
    }
    if (!expense) {
      // New expenses appear in the list optimistically, so close right away.
      onSubmit(body).catch(() => undefined)
      onOpenChange(false)
      return
    }
    try {
      await onSubmit(body)
      onOpenChange(false)
    } catch {
      // the error toast is shown by the global mutation handler; keep the form open to fix it
    }
  })

  const title = readOnly ? "Expense" : expense ? "Edit expense" : "Add expense"

  return (
    <ResponsiveSheet open={open} onOpenChange={onOpenChange} title={title} desktop="drawer">
      <form onSubmit={submit} className="space-y-6 pb-2" noValidate>
        <fieldset disabled={readOnly} className="min-w-0 space-y-6">
          {/* Amount */}
          <div>
            <label htmlFor="expense-amount" className="text-[13px] font-medium text-muted-foreground">
              Amount
            </label>
            <div className="mt-1 flex items-baseline gap-2 border-b-2 border-hairline pb-2 transition-colors focus-within:border-lime-ink">
              <span className="font-money text-3xl text-muted-foreground">{currencySymbol(currency)}</span>
              <input
                id="expense-amount"
                inputMode="decimal"
                autoComplete="off"
                autoFocus={!expense}
                placeholder="0,00"
                aria-invalid={!!errors.amount}
                aria-describedby="expense-amount-hint"
                className="min-w-0 flex-1 bg-transparent font-money text-5xl font-semibold tracking-tight outline-none placeholder:text-muted-foreground/40"
                {...form.register("amount")}
              />
            </div>
            <p id="expense-amount-hint" className={cn("mt-1.5 text-[13px]", errors.amount ? "text-negative" : "text-muted-foreground")} role={errors.amount ? "alert" : undefined}>
              {errors.amount?.message ?? (parsed.ok ? formatMoney(parsed.minor, currency) : "86,40 and 86.40 both work")}
            </p>
          </div>

          <FormField id="expense-description" label="What was it?" error={errors.description?.message}>
            <Input
              id="expense-description"
              placeholder="Lidl weekly groceries"
              maxLength={80}
              className={inputClass}
              aria-invalid={!!errors.description}
              {...form.register("description")}
            />
          </FormField>

          {/* Category */}
          <div>
            <p className="mb-2 text-[13px] font-medium text-muted-foreground" id="category-label">
              Category
            </p>
            <Controller
              control={form.control}
              name="categoryId"
              render={({ field }) => (
                <div className="grid grid-cols-3 gap-1.5 sm:grid-cols-4" role="radiogroup" aria-labelledby="category-label">
                  {categories.map((c) => {
                    const selected = field.value === c.id
                    return (
                      <button
                        key={c.id}
                        type="button"
                        role="radio"
                        aria-checked={selected}
                        onClick={() => field.onChange(c.id)}
                        className={cn(
                          "flex flex-col items-center gap-1.5 rounded-xl border border-hairline px-1 py-2.5 text-[11.5px] font-medium transition-colors hover:bg-surface-2",
                          selected && "border-transparent bg-surface-2 text-foreground ring-2 ring-lime-ink",
                        )}
                      >
                        <CategoryIcon category={c} size="sm" />
                        <span className="w-full truncate text-center">{c.name}</span>
                      </button>
                    )
                  })}
                </div>
              )}
            />
          </div>

          <FormField id="expense-date" label="Date" error={errors.date?.message}>
            <Input id="expense-date" type="date" max={maxDate()} className={cn(inputClass, "font-money")} aria-invalid={!!errors.date} {...form.register("date")} />
          </FormField>

          {/* Paid by */}
          <div>
            <p className="mb-2 text-[13px] font-medium text-muted-foreground" id="paidby-label">
              Paid by
            </p>
            <Controller
              control={form.control}
              name="paidBy"
              render={({ field }) => (
                <div className="flex flex-wrap gap-2" role="radiogroup" aria-labelledby="paidby-label">
                  {active.map((m) => {
                    const selected = field.value === m.userId
                    return (
                      <button
                        key={m.userId}
                        type="button"
                        role="radio"
                        aria-checked={selected}
                        onClick={() => field.onChange(m.userId)}
                        className={cn(
                          "flex items-center gap-2 rounded-full border border-hairline py-1 pl-1 pr-3 text-sm transition-colors hover:bg-surface-2",
                          selected && "border-transparent bg-primary text-primary-foreground hover:bg-primary",
                        )}
                      >
                        <MemberAvatar person={m} size="sm" />
                        {m.userId === me ? "Me" : firstName(m.name)}
                      </button>
                    )
                  })}
                </div>
              )}
            />
          </div>

          {/* Split with */}
          <div>
            <div className="mb-2 flex items-center justify-between gap-2">
              <p className="text-[13px] font-medium text-muted-foreground" id="splitwith-label">
                Split with
              </p>
              <div className="flex gap-1.5">
                <QuickChip
                  active={participants?.length === everyone.length}
                  onClick={() => form.setValue("participants", everyone, { shouldValidate: true })}
                >
                  Everyone
                </QuickChip>
                <QuickChip
                  active={participants?.length === 1 && participants[0] === me}
                  onClick={() => form.setValue("participants", [me], { shouldValidate: true })}
                >
                  Only me + …
                </QuickChip>
              </div>
            </div>
            <Controller
              control={form.control}
              name="participants"
              render={({ field }) => (
                <div className="grid grid-cols-2 gap-1.5" role="group" aria-labelledby="splitwith-label">
                  {active.map((m) => {
                    const on = field.value.includes(m.userId)
                    return (
                      <button
                        key={m.userId}
                        type="button"
                        aria-pressed={on}
                        onClick={() =>
                          field.onChange(on ? field.value.filter((id) => id !== m.userId) : [...field.value, m.userId])
                        }
                        className={cn(
                          "flex items-center gap-2.5 rounded-xl border border-hairline p-2 text-left text-sm transition-all",
                          on ? "bg-surface-2" : "opacity-55 hover:opacity-90",
                        )}
                      >
                        <MemberAvatar person={m} size="sm" />
                        <span className="min-w-0 flex-1 truncate">{m.userId === me ? "Me" : firstName(m.name)}</span>
                        <span
                          className={cn(
                            "flex size-5 items-center justify-center rounded-md border border-hairline",
                            on && "border-transparent bg-lime-ink text-background",
                          )}
                        >
                          {on && <Check className="size-3.5" strokeWidth={3} />}
                        </span>
                      </button>
                    )
                  })}
                </div>
              )}
            />
            {errors.participants && (
              <p role="alert" className="mt-1.5 text-[13px] text-negative">
                {errors.participants.message}
              </p>
            )}

            {/* Live preview */}
            <AnimatePresence initial={false}>
              {parsed.ok && sortedParticipants.length > 0 && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: "auto" }}
                  exit={{ opacity: 0, height: 0 }}
                  className="overflow-hidden"
                >
                  <div className="mt-3 rounded-2xl border border-dashed border-hairline bg-surface-2/40 p-3.5" aria-live="polite">
                    <p className="font-money text-lg font-semibold">
                      {shares.every((s) => s === shares[0])
                        ? `${formatMoney(shares[0], currency)} each × ${shares.length}`
                        : `${formatMoney(shares[0], currency)} / ${formatMoney(shares[shares.length - 1], currency)} × ${shares.length}`}
                    </p>
                    <ul className="mt-2 space-y-1">
                      {sortedParticipants.map((id, i) => {
                        const m = memberById.get(id)
                        if (!m) return null
                        return (
                          <li key={id} className="flex items-center justify-between text-[13px]">
                            <span className="flex items-center gap-2 text-muted-foreground">
                              <MemberAvatar person={m} size="xs" />
                              {id === me ? "You" : firstName(m.name)}
                              {id === paidBy && <span className="text-[11px] text-lime-ink">paid</span>}
                            </span>
                            <span className="font-money">{formatMoney(shares[i], currency)}</span>
                          </li>
                        )
                      })}
                    </ul>
                    {!sortedParticipants.includes(paidBy) && memberById.get(paidBy) && (
                      <p className="mt-2 text-xs text-muted-foreground">
                        {paidBy === me ? "You're" : `${firstName(memberById.get(paidBy)!.name)} is`} paying without taking a share.
                      </p>
                    )}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          <FormField id="expense-notes" label="Notes (optional)" error={errors.notes?.message}>
            <Textarea id="expense-notes" rows={2} placeholder="Receipt is on the fridge" className="rounded-xl bg-surface dark:bg-surface-2/60" {...form.register("notes")} />
          </FormField>
        </fieldset>

        {!readOnly && (
          <div className="sticky bottom-0 -mx-1 flex gap-2 bg-gradient-to-t from-popover via-popover to-transparent px-1 pt-4">
            {expense && onDelete && (
              <Button type="button" variant="outline" size="xl" onClick={() => setConfirmDelete(true)} aria-label="Delete expense" className="text-negative">
                <Trash2 />
              </Button>
            )}
            <Button type="submit" size="xl" className="flex-1" disabled={pending}>
              {pending ? <Loader2 className="animate-spin" /> : expense ? "Save changes" : "Add expense"}
              {!pending && <kbd className="ml-1 hidden rounded bg-black/10 px-1.5 py-0.5 font-mono text-[10px] sm:inline">Enter</kbd>}
            </Button>
          </div>
        )}
        {readOnly && <p className="text-sm text-muted-foreground">Only the person who added it, the payer or the owner can change this expense.</p>}
      </form>

      {expense && onDelete && (
        <ConfirmDialog
          open={confirmDelete}
          onOpenChange={setConfirmDelete}
          title={`Delete ${expense.description}?`}
          description="It will no longer count in anyone's balance. The record stays in the activity log."
          confirmLabel="Delete expense"
          destructive
          onConfirm={() => {
            setConfirmDelete(false)
            onOpenChange(false)
            onDelete(expense)
          }}
        />
      )}
    </ResponsiveSheet>
  )
}

function QuickChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        "rounded-full border border-hairline px-2.5 py-1 text-xs font-medium transition-colors hover:bg-surface-2",
        active && "border-transparent bg-foreground text-background hover:bg-foreground",
      )}
    >
      {children}
    </button>
  )
}
