import { motion } from "framer-motion"
import { Archive, CircleCheckBig, Receipt } from "lucide-react"
import { Link } from "react-router"

import type { SplitSummary } from "@/api/types"
import { AvatarStack } from "@/components/MemberAvatar"
import { MoneyText } from "@/components/MoneyText"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { formatPeriod, plural, timeAgo } from "@/lib/format"
import { cn } from "@/lib/utils"

export function BalanceChip({ minor, currency }: { minor: number; currency: string }) {
  const label = minor > 0 ? "you get back" : minor < 0 ? "you owe" : "all square"
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium",
        minor > 0 && "bg-positive/12 text-positive",
        minor < 0 && "bg-negative/12 text-negative",
        minor === 0 && "bg-surface-2 text-muted-foreground",
      )}
    >
      {label}
      {minor !== 0 && <MoneyText minor={Math.abs(minor)} currency={currency} animateValue={false} className="font-semibold" />}
    </span>
  )
}

export function SplitCard({ split, index }: { split: SplitSummary; index: number }) {
  const period = formatPeriod(split.period?.start, split.period?.end)
  const inactive = split.status !== "OPEN"
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.05, duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
      whileHover={{ y: -4 }}
    >
      <Link
        to={`/app/splits/${split.id}`}
        className={cn(
          "group relative flex h-full flex-col rounded-3xl border border-hairline bg-surface p-5 transition-shadow hover:shadow-[0_20px_60px_-30px_var(--glow)] focus-visible:ring-2 focus-visible:ring-ring",
          split.status === "ARCHIVED" && "opacity-70",
        )}
      >
        <div className="flex items-start gap-3">
          <span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-surface-2 text-2xl" aria-hidden>
            {split.emoji ?? "🏠"}
          </span>
          <div className="min-w-0 flex-1">
            <h3 className="truncate font-heading text-lg font-bold leading-tight">{split.name}</h3>
            <p className="mt-0.5 truncate text-sm text-muted-foreground">{period ?? split.description ?? `Created ${timeAgo(split.createdAt)}`}</p>
          </div>
          {split.calculationStale && split.status === "OPEN" && (
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="relative mt-1 flex size-2.5" aria-label="Expenses changed since the last calculation">
                  <span className="absolute inline-flex size-full animate-ping rounded-full bg-amber-400 opacity-60" />
                  <span className="relative inline-flex size-2.5 rounded-full bg-amber-400" />
                </span>
              </TooltipTrigger>
              <TooltipContent>Expenses changed since the last calculation</TooltipContent>
            </Tooltip>
          )}
          {split.status === "SETTLED" && (
            <span className="inline-flex items-center gap-1 rounded-full bg-positive/12 px-2 py-0.5 text-xs font-medium text-positive">
              <CircleCheckBig className="size-3" /> Settled
            </span>
          )}
          {split.status === "ARCHIVED" && (
            <span className="inline-flex items-center gap-1 rounded-full bg-surface-2 px-2 py-0.5 text-xs font-medium text-muted-foreground">
              <Archive className="size-3" /> Archived
            </span>
          )}
        </div>

        <div className="receipt-divider my-4" />

        <div className="flex items-end justify-between gap-3">
          <div>
            <p className="text-xs text-muted-foreground">Total spend</p>
            <MoneyText minor={split.totalSpendMinor} currency={split.currency} className="text-2xl font-semibold" />
            <p className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
              <Receipt className="size-3" /> {plural(split.expenseCount, "expense")}
            </p>
          </div>
          <AvatarStack people={split.members} max={4} />
        </div>

        <div className="mt-4 flex items-center justify-between">
          {inactive && split.myNetMinor === 0 ? (
            <span className="text-xs text-muted-foreground">Updated {timeAgo(split.lastActivityAt)}</span>
          ) : (
            <BalanceChip minor={split.myNetMinor} currency={split.currency} />
          )}
          {!(inactive && split.myNetMinor === 0) && <span className="text-xs text-muted-foreground">{timeAgo(split.lastActivityAt)}</span>}
        </div>
      </Link>
    </motion.div>
  )
}
