import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { ArrowRight, Loader2 } from "lucide-react"
import { useEffect, useMemo } from "react"
import { Controller, useForm, useWatch } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"

import { paymentsApi, settlementKeys } from "@/api/settlements"
import type { Member } from "@/api/types"
import { FormField, inputClass } from "@/components/FormField"
import { MemberAvatar } from "@/components/MemberAvatar"
import { ResponsiveSheet } from "@/components/ResponsiveSheet"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { firstName } from "@/lib/format"
import { currencySymbol, formatMoney, parseMoney, toInputString } from "@/lib/money"
import { cn } from "@/lib/utils"

import { useRefreshAfterLedgerChange } from "./useSettlement"

const NOTES = ["PayPal", "Bank transfer", "Cash", "Wise", "UPI"]

const schema = z
  .object({
    fromUserId: z.string().min(1, "Who paid?"),
    toUserId: z.string().min(1, "Who was paid?"),
    amount: z.string().superRefine((v, ctx) => {
      const parsed = parseMoney(v)
      if (!parsed.ok) ctx.addIssue({ code: "custom", message: parsed.error })
    }),
    note: z.string().max(80, "Note can be at most 80 characters"),
  })
  .refine((v) => v.fromUserId !== v.toUserId, { path: ["toUserId"], message: "Choose two different people" })

type Values = z.infer<typeof schema>

export type PaymentPreset = { fromUserId: string; toUserId: string; amountMinor: number }

export function RecordPaymentDialog({
  splitId,
  members,
  currency,
  me,
  preset,
  open,
  onOpenChange,
}: {
  splitId: string
  members: Member[]
  currency: string
  me: string
  preset: PaymentPreset | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const refresh = useRefreshAfterLedgerChange(splitId)
  const active = members.filter((m) => m.active)
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { fromUserId: me, toUserId: "", amount: "", note: "" } })
  const { errors } = form.formState
  const [from, to, amount] = useWatch({ control: form.control, name: ["fromUserId", "toUserId", "amount"] })
  const parsed = parseMoney(amount ?? "")

  const defaults = useMemo<Values>(
    () =>
      preset
        ? { fromUserId: preset.fromUserId, toUserId: preset.toUserId, amount: toInputString(preset.amountMinor), note: "" }
        : { fromUserId: me, toUserId: members.find((m) => m.active && m.userId !== me)?.userId ?? "", amount: "", note: "" },
    [preset, me, members],
  )

  useEffect(() => {
    if (open) form.reset(defaults)
  }, [open, defaults, form])

  const record = useMutation({
    mutationFn: (v: Values) => paymentsApi.record(splitId, { ...v, note: v.note || undefined }),
    onSuccess: (payment) => {
      queryClient.invalidateQueries({ queryKey: settlementKeys.payments(splitId) })
      refresh()
      const fromName = active.find((m) => m.userId === payment.fromUserId)?.name ?? "Someone"
      const toName = active.find((m) => m.userId === payment.toUserId)?.name ?? "someone"
      if (payment.warning) toast.warning(payment.warning, { duration: 9000 })
      else toast.success(`Recorded: ${firstName(fromName)} paid ${firstName(toName)} ${formatMoney(payment.amountMinor, currency)}`)
      onOpenChange(false)
    },
  })

  const person = (id: string) => active.find((m) => m.userId === id)

  return (
    <ResponsiveSheet open={open} onOpenChange={onOpenChange} title="Record a payment" description="Money that changed hands outside the app. Balances update when you recalculate.">
      <form onSubmit={form.handleSubmit((v) => record.mutate(v))} className="space-y-5" noValidate>
        <div className="flex items-center justify-center gap-4 rounded-2xl bg-surface-2/50 py-5">
          <PersonBubble person={person(from)} label={from === me ? "You" : undefined} />
          <ArrowRight className="size-5 text-lime-ink" aria-hidden />
          <PersonBubble person={person(to)} label={to === me ? "You" : undefined} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <FormField id="payment-from" label="From" error={errors.fromUserId?.message}>
            <Controller control={form.control} name="fromUserId" render={({ field }) => <PersonSelect id="payment-from" value={field.value} onChange={field.onChange} members={active} me={me} />} />
          </FormField>
          <FormField id="payment-to" label="To" error={errors.toUserId?.message}>
            <Controller control={form.control} name="toUserId" render={({ field }) => <PersonSelect id="payment-to" value={field.value} onChange={field.onChange} members={active} me={me} />} />
          </FormField>
        </div>
        <FormField id="payment-amount" label="Amount" error={errors.amount?.message} hint={parsed.ok ? formatMoney(parsed.minor, currency) : undefined}>
          <div className="relative">
            <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 font-money text-muted-foreground">{currencySymbol(currency)}</span>
            <Input id="payment-amount" inputMode="decimal" autoComplete="off" className={cn(inputClass, "pl-8 font-money text-lg")} aria-invalid={!!errors.amount} {...form.register("amount")} />
          </div>
        </FormField>
        <FormField id="payment-note" label="How? (optional)" error={errors.note?.message}>
          <Input id="payment-note" placeholder="PayPal" className={inputClass} {...form.register("note")} />
          <div className="flex flex-wrap gap-1.5 pt-1">
            {NOTES.map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => form.setValue("note", n)}
                className="rounded-full border border-hairline px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:bg-surface-2 hover:text-foreground"
              >
                {n}
              </button>
            ))}
          </div>
        </FormField>
        <Button type="submit" size="xl" className="w-full" disabled={record.isPending}>
          {record.isPending ? <Loader2 className="animate-spin" /> : "Record payment"}
        </Button>
      </form>
    </ResponsiveSheet>
  )
}

function PersonBubble({ person, label }: { person?: Member; label?: string }) {
  return (
    <div className="flex w-24 flex-col items-center gap-1.5 text-center">
      {person ? <MemberAvatar person={person} size="lg" /> : <span className="size-12 rounded-full border border-dashed border-hairline" />}
      <span className="w-full truncate text-sm font-medium">{label ?? (person ? firstName(person.name) : "—")}</span>
    </div>
  )
}

function PersonSelect({ id, value, onChange, members, me }: { id: string; value: string; onChange: (v: string) => void; members: Member[]; me: string }) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger id={id} className="h-11 w-full rounded-xl bg-surface data-[size=default]:h-11 dark:bg-surface-2/60">
        <SelectValue placeholder="Choose" />
      </SelectTrigger>
      <SelectContent>
        {members.map((m) => (
          <SelectItem key={m.userId} value={m.userId}>
            {m.userId === me ? "Me" : m.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
