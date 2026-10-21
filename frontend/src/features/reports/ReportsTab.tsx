import { useQuery } from "@tanstack/react-query"
import { motion } from "framer-motion"
import { ArrowRight, Calculator, FileBarChart2, Lightbulb, Loader2, PieChart, Plus, TrendingUp } from "lucide-react"
import { Link } from "react-router"

import { reportKeys, reportsApi } from "@/api/reports"
import type { Split } from "@/api/types"
import { EmptyState } from "@/components/EmptyState"
import { MoneyText } from "@/components/MoneyText"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useLatestSettlement } from "@/features/settlement/useSettlement"
import { firstName, formatPeriod, plural, timeAgo } from "@/lib/format"

import { useGenerateReport } from "./useGenerateReport"

export function ReportsTab({ split, onCalculate }: { split: Split; onCalculate: () => void }) {
  const reports = useQuery({ queryKey: reportKeys.list(split.id), queryFn: () => reportsApi.list(split.id) })
  const latest = useLatestSettlement(split.id)
  const generate = useGenerateReport(split.id)
  const calculated = latest.data != null
  const list = reports.data ?? []

  if (reports.isPending || latest.isPending) {
    return (
      <div className="space-y-3" aria-busy="true">
        <Skeleton className="h-28 rounded-3xl" />
        <Skeleton className="h-28 rounded-3xl" />
      </div>
    )
  }

  if (!calculated) {
    return (
      <EmptyState
        icons={[PieChart, FileBarChart2, TrendingUp]}
        title="Calculate first, then get your report"
        description="The spending report builds on the latest calculation: where the money went, who paid most, the priciest day, and a PDF to keep."
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

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">{list.length === 0 ? "No reports yet." : plural(list.length, "report")}</p>
        <Button size="xl" onClick={() => generate.mutate()} disabled={generate.isPending}>
          {generate.isPending ? <Loader2 className="animate-spin" /> : <Plus />} Generate report
        </Button>
      </div>
      {list.length === 0 ? (
        <EmptyState
          icons={[PieChart, FileBarChart2, TrendingUp]}
          title="No reports yet"
          description="Generate one to see charts, insights and a downloadable PDF for this split."
        />
      ) : (
        <ul className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {list.map((r, i) => (
            <motion.li key={r.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}>
              <Link
                to={`/app/splits/${split.id}/reports/${r.id}`}
                className="group flex h-full flex-col rounded-3xl border border-hairline bg-surface p-5 transition-all hover:-translate-y-0.5 hover:shadow-[0_20px_60px_-30px_var(--glow)]"
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-2.5">
                    <span className="flex size-10 items-center justify-center rounded-xl bg-primary/15 text-lime-ink">
                      <FileBarChart2 className="size-5" />
                    </span>
                    <div>
                      <p className="font-heading font-bold">{formatPeriod(r.periodStart, r.periodEnd)}</p>
                      <p className="text-xs text-muted-foreground">
                        {firstName(r.generatedByName)} · {timeAgo(r.generatedAt)}
                      </p>
                    </div>
                  </div>
                  <ArrowRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-foreground" />
                </div>
                <div className="receipt-divider my-4" />
                <div className="flex items-baseline justify-between">
                  <MoneyText minor={r.totalSpendMinor} currency={split.currency} animateValue={false} className="text-xl font-semibold" />
                  <span className="text-xs text-muted-foreground">{plural(r.expenseCount, "expense")}</span>
                </div>
                {r.topInsight && (
                  <p className="mt-3 flex gap-2 text-sm text-muted-foreground">
                    <Lightbulb className="mt-0.5 size-3.5 shrink-0 text-lime-ink" />
                    {r.topInsight}
                  </p>
                )}
              </Link>
            </motion.li>
          ))}
        </ul>
      )}
    </div>
  )
}
