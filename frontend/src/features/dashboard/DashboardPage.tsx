import { useQuery } from "@tanstack/react-query"
import { motion } from "framer-motion"
import { Home, Link2, Plus, Receipt, Users } from "lucide-react"
import { useState } from "react"

import { activityApi, splitKeys, splitsApi } from "@/api/splits"
import type { SplitSummary } from "@/api/types"
import { ActivityFeed, ActivitySkeleton } from "@/components/ActivityFeed"
import { EmptyState } from "@/components/EmptyState"
import { MoneyText } from "@/components/MoneyText"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useMe } from "@/features/auth/useAuth"
import { CreateSplitDialog } from "@/features/splits/CreateSplitDialog"
import { firstName } from "@/lib/format"

import { SplitCard } from "./SplitCard"

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 5) return "Up late"
  if (hour < 12) return "Good morning"
  if (hour < 18) return "Good afternoon"
  return "Good evening"
}

/** Sum balances per currency so different currencies are never added together. */
function totals(splits: SplitSummary[]) {
  const byCurrency = new Map<string, { owed: number; owe: number }>()
  for (const s of splits) {
    const t = byCurrency.get(s.currency) ?? { owed: 0, owe: 0 }
    if (s.myNetMinor > 0) t.owed += s.myNetMinor
    else t.owe += -s.myNetMinor
    byCurrency.set(s.currency, t)
  }
  return [...byCurrency.entries()]
}

export function DashboardPage() {
  const { data: me } = useMe()
  const [creating, setCreating] = useState(false)
  const splits = useQuery({ queryKey: splitKeys.all, queryFn: splitsApi.list })
  const activity = useQuery({ queryKey: splitKeys.recentActivity, queryFn: () => activityApi.recent(8) })

  const list = splits.data ?? []
  const ordered = [...list].sort((a, b) => (a.status === "ARCHIVED" ? 1 : 0) - (b.status === "ARCHIVED" ? 1 : 0))
  const names = new Map(list.map((s) => [s.id, s.name]))
  const sums = totals(list)
  const primary = sums.find(([c]) => c === "EUR") ?? sums[0]

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="font-mono text-xs uppercase tracking-[0.2em] text-muted-foreground">Dashboard</p>
          <h1 className="mt-2 font-heading text-[clamp(2rem,5vw,2.75rem)] font-bold leading-tight">
            {greeting()}, {me ? firstName(me.name) : ""}
          </h1>
        </div>
        <Button size="xl" onClick={() => setCreating(true)}>
          <Plus /> New split
        </Button>
      </div>

      {/* Summary strip */}
      <section aria-label="Your balance overall" className="relative overflow-hidden rounded-3xl border border-hairline bg-surface">
        <div className="glow-radial pointer-events-none absolute -left-20 -top-24 size-80 opacity-70" />
        <div className="relative grid divide-y divide-hairline sm:grid-cols-3 sm:divide-x sm:divide-y-0">
          {splits.isPending ? (
            Array.from({ length: 3 }, (_, i) => (
              <div key={i} className="space-y-2 p-5 sm:p-6">
                <Skeleton className="h-3 w-24" />
                <Skeleton className="h-8 w-32" />
              </div>
            ))
          ) : (
            <>
              <div className="p-5 sm:p-6">
                <p className="text-sm text-muted-foreground">You are owed overall</p>
                <MoneyText minor={primary?.[1].owed ?? 0} currency={primary?.[0] ?? "EUR"} className="mt-1 block text-3xl font-semibold text-positive" />
              </div>
              <div className="p-5 sm:p-6">
                <p className="text-sm text-muted-foreground">You owe</p>
                <MoneyText minor={primary?.[1].owe ?? 0} currency={primary?.[0] ?? "EUR"} className="mt-1 block text-3xl font-semibold text-negative" />
              </div>
              <div className="p-5 sm:p-6">
                <p className="text-sm text-muted-foreground">Active splits</p>
                <p className="mt-1 font-money text-3xl font-semibold">{list.filter((s) => s.status === "OPEN").length}</p>
              </div>
            </>
          )}
        </div>
        {sums.length > 1 && (
          <p className="relative border-t border-hairline px-5 py-2.5 text-xs text-muted-foreground sm:px-6">
            Showing {primary?.[0]} balances. You also have splits in {sums.filter(([c]) => c !== primary?.[0]).map(([c]) => c).join(", ")}.
          </p>
        )}
      </section>

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1fr)_320px]">
        <section aria-labelledby="splits-heading" className="min-w-0">
          <h2 id="splits-heading" className="mb-4 font-heading text-xl font-bold">
            Your splits
          </h2>
          {splits.isPending ? (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {Array.from({ length: 4 }, (_, i) => (
                <Skeleton key={i} className="h-[232px] rounded-3xl" />
              ))}
            </div>
          ) : splits.isError ? (
            <p className="rounded-2xl border border-negative/30 bg-negative/10 p-4 text-sm text-negative">Couldn't load your splits. {splits.error.message}</p>
          ) : list.length === 0 ? (
            <EmptyState
              icons={[Users, Home, Receipt]}
              title="No splits yet"
              description="Create a split for this month, send the link to your flatmates, and start adding expenses."
              action={
                <Button size="xl" onClick={() => setCreating(true)}>
                  <Plus /> Create your first split
                </Button>
              }
            />
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {ordered.map((split, i) => (
                <SplitCard key={split.id} split={split} index={i} />
              ))}
              <motion.button
                type="button"
                onClick={() => setCreating(true)}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: ordered.length * 0.05 }}
                whileHover={{ y: -4 }}
                className="flex min-h-[232px] flex-col items-center justify-center gap-3 rounded-3xl border border-dashed border-hairline text-muted-foreground transition-colors hover:border-lime-ink hover:text-foreground"
              >
                <span className="flex size-12 items-center justify-center rounded-2xl bg-surface-2">
                  <Plus className="size-5" />
                </span>
                <span className="font-heading font-bold">New split</span>
                <span className="flex items-center gap-1 text-xs">
                  <Link2 className="size-3" /> with an invite link
                </span>
              </motion.button>
            </div>
          )}
        </section>

        <aside aria-labelledby="activity-heading" className="min-w-0">
          <h2 id="activity-heading" className="mb-4 font-heading text-xl font-bold">
            Recent activity
          </h2>
          <div className="rounded-3xl border border-hairline bg-surface p-5">
            {activity.isPending ? (
              <ActivitySkeleton />
            ) : (activity.data ?? []).length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">Nothing yet. Activity from all your splits shows up here.</p>
            ) : (
              <ActivityFeed items={activity.data ?? []} splitNames={names} compact />
            )}
          </div>
        </aside>
      </div>

      <CreateSplitDialog open={creating} onOpenChange={setCreating} />
    </div>
  )
}
