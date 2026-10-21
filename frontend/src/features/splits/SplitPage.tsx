import { useQuery } from "@tanstack/react-query"
import { motion } from "framer-motion"
import { AlertTriangle, Archive, Calculator, CircleCheckBig, FileBarChart2, History, Receipt, Scale } from "lucide-react"
import { lazy, Suspense, useRef, useState } from "react"
import { useParams, useSearchParams } from "react-router"

import { ApiError } from "@/api/client"
import { categoriesApi, expenseKeys } from "@/api/expenses"
import { splitKeys, splitsApi } from "@/api/splits"
import type { Expense, Split } from "@/api/types"
import { ActivityFeed, ActivitySkeleton } from "@/components/ActivityFeed"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useMe } from "@/features/auth/useAuth"
import { ExpenseDrawer } from "@/features/expenses/ExpenseDrawer"
import { ExpensesTab } from "@/features/expenses/ExpensesTab"
import { useExpenseMutations } from "@/features/expenses/useExpenseMutations"
import { CalculationReveal } from "@/features/settlement/CalculationReveal"
import { RecordPaymentDialog, type PaymentPreset } from "@/features/settlement/RecordPaymentDialog"
import { useCalculate } from "@/features/settlement/useSettlement"
import { ReportsTab } from "@/features/reports/ReportsTab"
import { useGenerateReport } from "@/features/reports/useGenerateReport"
import { NotFoundPage } from "@/features/shell/NotFoundPage"
import { useHotkeys } from "@/hooks/useHotkeys"

import { SplitActionBar } from "./SplitActionBar"
import { SplitHeader } from "./SplitHeader"
import { SplitTabs, type TabDef } from "./SplitTabs"
import { useSplit } from "./useSplit"

// The balances tab uses the charting library, so it is loaded on demand.
const BalancesTab = lazy(() => import("@/features/settlement/BalancesTab").then((m) => ({ default: m.BalancesTab })))

type TabId = "expenses" | "balances" | "reports" | "activity"

const baseTabs: TabDef<TabId>[] = [
  { id: "expenses", label: "Expenses", icon: Receipt },
  { id: "balances", label: "Balances", icon: Scale },
  { id: "reports", label: "Report", icon: FileBarChart2 },
  { id: "activity", label: "Activity", icon: History },
]

export function SplitPage() {
  const { splitId = "" } = useParams()
  const split = useSplit(splitId)
  const { data: me } = useMe()

  if (split.isError && split.error instanceof ApiError && split.error.status === 404) {
    return <NotFoundPage title="Split not found" message="This split doesn't exist, or you're not a member of it. Ask a member for the invite link." />
  }

  if (split.isPending || !split.data || !me) {
    return (
      <div className="space-y-6" aria-busy="true">
        <Skeleton className="h-[230px] rounded-[28px]" />
        <Skeleton className="h-10 w-80" />
        <Skeleton className="h-64 rounded-3xl" />
      </div>
    )
  }

  return <SplitView split={split.data} me={me.id} />
}

function SplitView({ split, me }: { split: Split; me: string }) {
  const [params, setParams] = useSearchParams()
  const tabs = baseTabs.map((t) => (t.id === "balances" ? { ...t, badge: split.calculationStale && split.status === "OPEN" } : t))
  const tab = (tabs.find((t) => t.id === params.get("tab"))?.id ?? tabs[0].id) as TabId
  const setTab = (next: TabId) => setParams((p) => ({ ...Object.fromEntries(p), tab: next }), { replace: true })

  const categories = useQuery({ queryKey: expenseKeys.categories(split.id), queryFn: () => categoriesApi.list(split.id), staleTime: 5 * 60_000 })
  const { create, update, remove } = useExpenseMutations(split.id, split.currency, me)
  const [drawer, setDrawer] = useState<{ open: boolean; expense: Expense | null }>({ open: false, expense: null })
  const searchRef = useRef<HTMLInputElement>(null)
  const isOpen = split.status === "OPEN"
  const calculate = useCalculate(split.id)
  const generateReport = useGenerateReport(split.id)
  const [revealOpen, setRevealOpen] = useState(false)
  const [payment, setPayment] = useState<{ open: boolean; preset: PaymentPreset | null }>({ open: false, preset: null })

  const runCalculation = () => {
    if (split.expenseCount === 0 && split.lastCalculatedAt === null) {
      setTab("expenses")
      return
    }
    setRevealOpen(true)
    calculate.mutate(undefined, { onError: () => setRevealOpen(false) })
  }

  const recordPayment = (preset: PaymentPreset | null) => setPayment({ open: true, preset })

  const addExpense = () => {
    if (tab !== "expenses") setTab("expenses")
    setDrawer({ open: true, expense: null })
  }

  useHotkeys({
    n: () => isOpen && addExpense(),
    c: () => runCalculation(),
    "/": () => {
      if (tab !== "expenses") setTab("expenses")
      window.setTimeout(() => searchRef.current?.focus(), 50)
    },
  })

  return (
    <div className="space-y-6">
      <SplitHeader split={split} />
      <StatusBanner split={split} />
      <StaleBanner split={split} onRecalculate={runCalculation} pending={calculate.isPending} />
      <SplitTabs tabs={tabs} value={tab} onChange={setTab} />
      <motion.section
        key={tab}
        role="tabpanel"
        id={`panel-${tab}`}
        aria-labelledby={`tab-${tab}`}
        initial={{ opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.2 }}
      >
        {tab === "expenses" && (
          <ExpensesTab
            split={split}
            me={me}
            categories={categories.data ?? []}
            searchRef={searchRef}
            onAdd={addExpense}
            onOpen={(expense) => setDrawer({ open: true, expense })}
            onDelete={(expense) => remove.mutate(expense)}
          />
        )}
        {tab === "balances" && (
          <Suspense fallback={<Skeleton className="h-64 rounded-3xl" />}>
            <BalancesTab split={split} me={me} onCalculate={runCalculation} onRecordPayment={recordPayment} />
          </Suspense>
        )}
        {tab === "reports" && <ReportsTab split={split} onCalculate={runCalculation} />}
        {tab === "activity" && <ActivityTab splitId={split.id} />}
      </motion.section>

      {isOpen && <SplitActionBar onAdd={addExpense} onCalculate={runCalculation} />}

      <CalculationReveal
        open={revealOpen}
        onOpenChange={setRevealOpen}
        settlement={calculate.data ?? null}
        pending={calculate.isPending}
        members={split.members}
        me={me}
        currency={split.currency}
        onRecordPayments={() => {
          setRevealOpen(false)
          setTab("balances")
        }}
        onGenerateReport={() => generateReport.mutate(undefined, { onSuccess: () => setRevealOpen(false) })}
      />
      <RecordPaymentDialog
        splitId={split.id}
        members={split.members}
        currency={split.currency}
        me={me}
        preset={payment.preset}
        open={payment.open}
        onOpenChange={(open) => setPayment((p) => ({ ...p, open }))}
      />

      <ExpenseDrawer
        open={drawer.open}
        onOpenChange={(open) => setDrawer((d) => ({ ...d, open }))}
        expense={drawer.expense}
        members={split.members}
        categories={categories.data ?? []}
        currency={split.currency}
        me={me}
        readOnly={drawer.expense !== null && (!drawer.expense.canEdit || !isOpen)}
        pending={update.isPending}
        onSubmit={(body) => (drawer.expense ? update.mutateAsync({ id: drawer.expense.id, body }) : create.mutateAsync(body))}
        onDelete={(expense) => remove.mutate(expense)}
      />
    </div>
  )
}

function StatusBanner({ split }: { split: Split }) {
  if (split.status === "OPEN") return null
  const settled = split.status === "SETTLED"
  return (
    <div className="flex items-start gap-3 rounded-2xl border border-hairline bg-surface-2/60 p-4 text-sm">
      {settled ? <CircleCheckBig className="mt-0.5 size-4 shrink-0 text-positive" /> : <Archive className="mt-0.5 size-4 shrink-0 text-muted-foreground" />}
      <p>
        <span className="font-semibold">{settled ? "Everyone is settled up." : "This split is archived."}</span>{" "}
        <span className="text-muted-foreground">
          It's read-only now{split.myRole === "OWNER" ? ". You can reopen it from the menu if something changes." : "."}
        </span>
      </p>
    </div>
  )
}

function StaleBanner({ split, onRecalculate, pending }: { split: Split; onRecalculate: () => void; pending: boolean }) {
  if (!split.calculationStale || split.status !== "OPEN" || (split.expenseCount === 0 && split.lastCalculatedAt === null)) return null
  const never = split.lastCalculatedAt === null
  return (
    <div role="status" className="flex flex-col gap-3 rounded-2xl border border-amber-400/30 bg-amber-400/10 p-4 sm:flex-row sm:items-center">
      <AlertTriangle className="size-5 shrink-0 text-amber-500" aria-hidden />
      <p className="flex-1 text-sm">
        <span className="font-semibold">{never ? "Not calculated yet." : "Expenses changed since the last calculation."}</span>{" "}
        <span className="text-muted-foreground">{never ? "Calculate to see who owes whom." : "Recalculate to update everyone's balance."}</span>
      </p>
      <Button size="lg" onClick={onRecalculate} disabled={pending} className="h-10 rounded-xl px-4">
        <Calculator /> {never ? "Calculate" : "Recalculate"}
      </Button>
    </div>
  )
}

function ActivityTab({ splitId }: { splitId: string }) {
  const activity = useQuery({ queryKey: splitKeys.activity(splitId), queryFn: () => splitsApi.activity(splitId, 100) })
  return (
    <div className="rounded-3xl border border-hairline bg-surface p-5 sm:p-6">
      {activity.isPending ? <ActivitySkeleton rows={6} /> : <ActivityFeed items={activity.data ?? []} />}
    </div>
  )
}
