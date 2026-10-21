import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AnimatePresence, motion } from "framer-motion"
import { ArrowRight, Calculator, CircleCheckBig, HandCoins, History, List, Network, PartyPopper, Scale, Trash2, Users } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"

import { paymentsApi, settlementKeys, settlementsApi } from "@/api/settlements"
import { splitKeys } from "@/api/splits"
import type { Payment, Settlement, Split } from "@/api/types"
import { BalanceBars } from "@/components/BalanceBars"
import { ConfirmDialog } from "@/components/ConfirmDialog"
import { EmptyState } from "@/components/EmptyState"
import { MemberAvatar } from "@/components/MemberAvatar"
import { MoneyText } from "@/components/MoneyText"
import { SettlementGraph } from "@/components/SettlementGraph"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { celebrate } from "@/lib/confetti"
import { firstName, plural, timeAgo } from "@/lib/format"
import { formatMoney } from "@/lib/money"
import { cn } from "@/lib/utils"

import type { PaymentPreset } from "./RecordPaymentDialog"
import { useLatestSettlement, useRefreshAfterLedgerChange } from "./useSettlement"

export function BalancesTab({
  split,
  me,
  onCalculate,
  onRecordPayment,
}: {
  split: Split
  me: string
  onCalculate: () => void
  onRecordPayment: (preset: PaymentPreset | null) => void
}) {
  const latest = useLatestSettlement(split.id)
  const payments = useQuery({ queryKey: settlementKeys.payments(split.id), queryFn: () => paymentsApi.list(split.id) })
  const [view, setView] = useState<"graph" | "list">("graph")

  if (latest.isPending) {
    return (
      <div className="grid gap-4 lg:grid-cols-2" aria-busy="true">
        <Skeleton className="h-64 rounded-3xl" />
        <Skeleton className="h-64 rounded-3xl" />
      </div>
    )
  }

  const settlement = latest.data
  if (!settlement) {
    return (
      <EmptyState
        icons={[Users, Calculator, Scale]}
        title="No calculation yet"
        description={
          split.expenseCount === 0
            ? "Add a few expenses first, then calculate to see who owes whom."
            : `Calculate the split to see what everyone paid, consumed and owes. ${plural(split.expenseCount, "expense")} so far.`
        }
        action={
          split.expenseCount > 0 ? (
            <Button size="xl" onClick={onCalculate}>
              <Calculator /> Calculate split
            </Button>
          ) : undefined
        }
      />
    )
  }

  const colorOf = new Map(split.members.map((m) => [m.userId, m.avatarColor]))
  const canSettle = settlement.allSettled && !split.calculationStale && split.status === "OPEN"
  const canRecord = (from: string, to: string) => split.status === "OPEN" && (from === me || to === me || split.myRole === "OWNER")

  return (
    <div className="space-y-6">
      <PersonalLine settlement={settlement} me={me} stale={split.calculationStale} />

      {canSettle && <SettleCard splitId={split.id} />}

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        {/* Balances chart */}
        <section className="rounded-3xl border border-hairline bg-surface p-5 sm:p-6" aria-labelledby="balances-heading">
          <div className="flex items-baseline justify-between">
            <h3 id="balances-heading" className="font-heading text-lg font-bold">
              Balances
            </h3>
            <span className="flex items-center gap-3 text-xs text-muted-foreground">
              <span className="flex items-center gap-1.5">
                <span className="size-2 rounded-sm bg-negative" /> owes
              </span>
              <span className="flex items-center gap-1.5">
                <span className="size-2 rounded-sm bg-positive" /> gets back
              </span>
            </span>
          </div>
          <div className="mt-3">
            <BalanceBars members={settlement.memberSummaries} currency={settlement.currency} me={me} />
          </div>
        </section>

        {/* Who pays whom */}
        <section className="rounded-3xl border border-hairline bg-surface p-5 sm:p-6" aria-labelledby="transfers-heading">
          <div className="flex items-center justify-between gap-2">
            <h3 id="transfers-heading" className="font-heading text-lg font-bold">
              Who pays whom
            </h3>
            <div className="flex rounded-xl bg-surface-2 p-0.5" role="radiogroup" aria-label="View">
              {(["graph", "list"] as const).map((v) => (
                <button
                  key={v}
                  type="button"
                  role="radio"
                  aria-checked={view === v}
                  onClick={() => setView(v)}
                  className={cn(
                    "relative flex items-center gap-1.5 rounded-[10px] px-2.5 py-1 text-xs font-medium",
                    view === v ? "text-foreground" : "text-muted-foreground",
                  )}
                >
                  {view === v && <motion.span layoutId="transfer-view" className="absolute inset-0 rounded-[10px] bg-surface shadow-sm" />}
                  <span className="relative flex items-center gap-1.5">
                    {v === "graph" ? <Network className="size-3.5" /> : <List className="size-3.5" />}
                    {v === "graph" ? "Graph" : "List"}
                  </span>
                </button>
              ))}
            </div>
          </div>
          <AnimatePresence mode="wait" initial={false}>
            {view === "graph" ? (
              <motion.div key="graph" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="mt-2">
                <SettlementGraph
                  members={settlement.memberSummaries.map((m) => ({ id: m.userId, name: m.name, avatarColor: colorOf.get(m.userId) ?? m.avatarColor, net: m.netMinor }))}
                  transfers={settlement.transfers}
                  currency={settlement.currency}
                  highlightUserId={me}
                  animateKey={settlement.id}
                  className="mx-auto"
                />
              </motion.div>
            ) : (
              <motion.ul key="list" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="mt-4 space-y-2">
                {settlement.transfers.length === 0 && <li className="py-8 text-center text-sm text-muted-foreground">Nobody owes anybody.</li>}
                {settlement.transfers.map((t) => (
                  <TransferRow
                    key={`${t.fromUserId}-${t.toUserId}`}
                    from={{ name: t.fromName, avatarColor: colorOf.get(t.fromUserId) ?? "#94A3B8" }}
                    to={{ name: t.toName, avatarColor: colorOf.get(t.toUserId) ?? "#94A3B8" }}
                    fromMe={t.fromUserId === me}
                    toMe={t.toUserId === me}
                    amount={t.amountMinor}
                    currency={settlement.currency}
                    onRecord={canRecord(t.fromUserId, t.toUserId) ? () => onRecordPayment({ fromUserId: t.fromUserId, toUserId: t.toUserId, amountMinor: t.amountMinor }) : undefined}
                  />
                ))}
              </motion.ul>
            )}
          </AnimatePresence>
          {view === "graph" && settlement.transfers.some((t) => canRecord(t.fromUserId, t.toUserId)) && (
            <Button variant="outline" size="xl" className="mt-2 w-full" onClick={() => setView("list")}>
              <HandCoins /> Record a payment
            </Button>
          )}
        </section>
      </div>

      {/* Per-member cards (also the table view of the chart) */}
      <section aria-labelledby="members-heading">
        <h3 id="members-heading" className="mb-3 font-heading text-lg font-bold">
          Everyone's numbers
        </h3>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {[...settlement.memberSummaries]
            .sort((a, b) => b.netMinor - a.netMinor)
            .map((m, i) => (
              <motion.article
                key={m.userId}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.05 }}
                className={cn("rounded-3xl border border-hairline bg-surface p-5", m.userId === me && "ring-1 ring-lime-ink/50")}
              >
                <div className="flex items-center gap-3">
                  <MemberAvatar person={{ name: m.name, avatarColor: colorOf.get(m.userId) ?? m.avatarColor }} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold">{m.userId === me ? `${m.name} (you)` : m.name}</p>
                    <p className="text-xs text-muted-foreground">{m.netMinor > 0 ? "gets back" : m.netMinor < 0 ? "owes" : "all square"}</p>
                  </div>
                  <MoneyText minor={m.netMinor} currency={settlement.currency} signed className="text-lg font-semibold" />
                </div>
                <div className="receipt-divider my-4" />
                <dl className="grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <dt className="text-xs text-muted-foreground">Paid</dt>
                    <dd>
                      <MoneyText minor={m.paidMinor} currency={settlement.currency} className="font-medium" />
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted-foreground">Their share</dt>
                    <dd>
                      <MoneyText minor={m.shareMinor} currency={settlement.currency} className="font-medium" />
                    </dd>
                  </div>
                  {(m.paymentsSentMinor > 0 || m.paymentsReceivedMinor > 0) && (
                    <>
                      <div>
                        <dt className="text-xs text-muted-foreground">Paid back</dt>
                        <dd className="font-money">{formatMoney(m.paymentsSentMinor, settlement.currency)}</dd>
                      </div>
                      <div>
                        <dt className="text-xs text-muted-foreground">Received</dt>
                        <dd className="font-money">{formatMoney(m.paymentsReceivedMinor, settlement.currency)}</dd>
                      </div>
                    </>
                  )}
                </dl>
              </motion.article>
            ))}
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <PaymentsList split={split} me={me} payments={payments.data ?? []} loading={payments.isPending} onRecord={() => onRecordPayment(null)} />
        <HistoryList splitId={split.id} current={settlement} />
      </div>
    </div>
  )
}

function PersonalLine({ settlement, me, stale }: { settlement: Settlement; me: string; stale: boolean }) {
  const mine = settlement.transfers.filter((t) => t.fromUserId === me || t.toUserId === me)
  return (
    <div className="relative overflow-hidden rounded-3xl border border-hairline bg-surface p-5 sm:p-6">
      <div className="glow-radial pointer-events-none absolute -left-24 -top-24 size-72 opacity-70" />
      <p className="relative text-xs text-muted-foreground">
        Calculation v{settlement.version} · by {firstName(settlement.calculatedByName)} {timeAgo(settlement.calculatedAt)} · {plural(settlement.expenseCount, "expense")}
        {stale && <span className="ml-1.5 font-medium text-amber-500">· out of date</span>}
      </p>
      <div className="relative mt-2 space-y-1">
        {mine.length === 0 ? (
          <p className="font-heading text-2xl font-bold">You're all square 🎉</p>
        ) : (
          mine.map((t) => (
            <p key={`${t.fromUserId}-${t.toUserId}`} className="font-heading text-2xl font-bold">
              {t.fromUserId === me ? (
                <>
                  You owe {firstName(t.toName)} <MoneyText minor={t.amountMinor} currency={settlement.currency} className="text-negative" />
                </>
              ) : (
                <>
                  {firstName(t.fromName)} owes you <MoneyText minor={t.amountMinor} currency={settlement.currency} className="text-positive" />
                </>
              )}
            </p>
          ))
        )}
      </div>
    </div>
  )
}

function SettleCard({ splitId }: { splitId: string }) {
  const queryClient = useQueryClient()
  const refresh = useRefreshAfterLedgerChange(splitId)
  const settle = useMutation({
    mutationFn: () => settlementsApi.settle(splitId),
    onSuccess: () => {
      celebrate()
      toast.success("Split settled. Everyone is square!")
      queryClient.invalidateQueries({ queryKey: splitKeys.detail(splitId) })
      refresh()
    },
  })
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: 1, scale: 1 }}
      className="flex flex-col items-start gap-4 rounded-3xl border border-positive/30 bg-positive/10 p-5 sm:flex-row sm:items-center sm:p-6"
    >
      <span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-positive text-[#0A0C10]">
        <PartyPopper className="size-6" />
      </span>
      <div className="flex-1">
        <p className="font-heading text-xl font-bold">Everyone is square</p>
        <p className="text-sm text-muted-foreground">Every balance is exactly zero. Mark the split as settled to close it.</p>
      </div>
      <Button size="xl" onClick={() => settle.mutate()} disabled={settle.isPending}>
        <CircleCheckBig /> Mark split as settled
      </Button>
    </motion.div>
  )
}

function TransferRow({
  from,
  to,
  fromMe,
  toMe,
  amount,
  currency,
  onRecord,
}: {
  from: { name: string; avatarColor: string }
  to: { name: string; avatarColor: string }
  fromMe: boolean
  toMe: boolean
  amount: number
  currency: string
  onRecord?: () => void
}) {
  return (
    <li className="flex flex-wrap items-center gap-3 rounded-2xl bg-surface-2/60 p-3">
      <div className="flex items-center gap-1.5">
        <MemberAvatar person={from} size="sm" />
        <ArrowRight className="size-3.5 text-lime-ink" aria-hidden />
        <MemberAvatar person={to} size="sm" />
      </div>
      <p className="min-w-0 flex-1 text-sm">
        <strong>{fromMe ? "You" : firstName(from.name)}</strong> {fromMe ? "pay" : "pays"} <strong>{toMe ? "you" : firstName(to.name)}</strong>
      </p>
      <MoneyText minor={amount} currency={currency} className="font-semibold" />
      {onRecord && (
        <Button size="sm" variant="outline" onClick={onRecord}>
          <HandCoins /> Record payment
        </Button>
      )}
    </li>
  )
}

function PaymentsList({ split, me, payments, loading, onRecord }: { split: Split; me: string; payments: Payment[]; loading: boolean; onRecord: () => void }) {
  const refresh = useRefreshAfterLedgerChange(split.id)
  const [target, setTarget] = useState<Payment | null>(null)
  const byId = new Map(split.members.map((m) => [m.userId, m]))
  const remove = useMutation({
    mutationFn: (p: Payment) => paymentsApi.remove(split.id, p.id),
    onSuccess: () => {
      setTarget(null)
      toast.success("Payment deleted")
      refresh()
    },
    onError: () => setTarget(null),
  })
  return (
    <section className="rounded-3xl border border-hairline bg-surface p-5 sm:p-6" aria-labelledby="payments-heading">
      <div className="flex items-center justify-between">
        <h3 id="payments-heading" className="font-heading text-lg font-bold">
          Payments
        </h3>
        {split.status === "OPEN" && (
          <Button size="sm" variant="ghost" onClick={onRecord}>
            <HandCoins /> Record
          </Button>
        )}
      </div>
      {loading ? (
        <Skeleton className="mt-4 h-16 rounded-2xl" />
      ) : payments.length === 0 ? (
        <p className="mt-4 text-sm text-muted-foreground">No payments yet. When someone pays someone back, record it here.</p>
      ) : (
        <ul className="mt-3 divide-y divide-dashed divide-hairline">
          {payments.map((p) => {
            const from = byId.get(p.fromUserId)
            const to = byId.get(p.toUserId)
            const canDelete = split.status === "OPEN" && (p.recordedBy === me || split.myRole === "OWNER")
            return (
              <li key={p.id} className="flex items-center gap-3 py-3">
                <HandCoins className="size-4 shrink-0 text-positive" aria-hidden />
                <div className="min-w-0 flex-1 text-sm">
                  <p className="truncate">
                    <strong>{p.fromUserId === me ? "You" : firstName(from?.name ?? "Someone")}</strong> paid{" "}
                    <strong>{p.toUserId === me ? "you" : firstName(to?.name ?? "someone")}</strong>
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {timeAgo(p.createdAt)}
                    {p.note && ` · ${p.note}`}
                  </p>
                </div>
                <MoneyText minor={p.amountMinor} currency={split.currency} animateValue={false} className="font-semibold" />
                {canDelete && (
                  <Button size="icon-sm" variant="ghost" onClick={() => setTarget(p)} aria-label="Delete payment" className="text-muted-foreground hover:text-negative">
                    <Trash2 />
                  </Button>
                )}
              </li>
            )
          })}
        </ul>
      )}
      <ConfirmDialog
        open={target !== null}
        onOpenChange={(o) => !o && setTarget(null)}
        title="Delete this payment?"
        description="Balances will change back when the split is recalculated."
        confirmLabel="Delete payment"
        destructive
        pending={remove.isPending}
        onConfirm={() => target && remove.mutate(target)}
      />
    </section>
  )
}

function HistoryList({ splitId, current }: { splitId: string; current: Settlement }) {
  const history = useQuery({ queryKey: settlementKeys.history(splitId), queryFn: () => settlementsApi.history(splitId) })
  return (
    <section className="rounded-3xl border border-hairline bg-surface p-5 sm:p-6" aria-labelledby="history-heading">
      <h3 id="history-heading" className="flex items-center gap-2 font-heading text-lg font-bold">
        <History className="size-4 text-muted-foreground" /> Calculation history
      </h3>
      {history.isPending ? (
        <Skeleton className="mt-4 h-16 rounded-2xl" />
      ) : (
        <ol className="mt-3 space-y-1.5">
          {(history.data ?? []).slice(0, 8).map((h) => (
            <li key={h.id} className={cn("flex items-center gap-3 rounded-xl px-3 py-2 text-sm", h.id === current.id && "bg-surface-2/70")}>
              <span className="w-9 font-money text-xs text-muted-foreground">v{h.version}</span>
              <span className="min-w-0 flex-1 truncate">
                {firstName(h.calculatedByName)} · {timeAgo(h.calculatedAt)}
              </span>
              <span className="text-xs text-muted-foreground">{plural(h.transferCount, "transfer")}</span>
              <MoneyText minor={h.totalSpendMinor} currency={current.currency} animateValue={false} className="text-xs" />
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
