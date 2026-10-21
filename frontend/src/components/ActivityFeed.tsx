import { motion } from "framer-motion"
import {
  Archive,
  Calculator,
  CircleCheckBig,
  FilePlus2,
  FileText,
  HandCoins,
  Link2,
  Link2Off,
  LogIn,
  LogOut,
  Pencil,
  PlusCircle,
  RefreshCw,
  RotateCcw,
  Sparkles,
  Trash2,
  UserMinus,
  type LucideIcon,
} from "lucide-react"
import { Link } from "react-router"

import type { Activity, ActivityType } from "@/api/types"
import { Skeleton } from "@/components/ui/skeleton"
import { timeAgo } from "@/lib/format"
import { cn } from "@/lib/utils"

const meta: Record<ActivityType, { icon: LucideIcon; tone: string }> = {
  SPLIT_CREATED: { icon: Sparkles, tone: "text-lime-ink" },
  SPLIT_UPDATED: { icon: Pencil, tone: "text-muted-foreground" },
  SPLIT_ARCHIVED: { icon: Archive, tone: "text-muted-foreground" },
  SPLIT_REOPENED: { icon: RotateCcw, tone: "text-muted-foreground" },
  MEMBER_JOINED: { icon: LogIn, tone: "text-positive" },
  MEMBER_LEFT: { icon: LogOut, tone: "text-muted-foreground" },
  MEMBER_REMOVED: { icon: UserMinus, tone: "text-negative" },
  INVITE_REGENERATED: { icon: RefreshCw, tone: "text-muted-foreground" },
  INVITE_ENABLED: { icon: Link2, tone: "text-muted-foreground" },
  INVITE_DISABLED: { icon: Link2Off, tone: "text-muted-foreground" },
  EXPENSE_ADDED: { icon: PlusCircle, tone: "text-lime-ink" },
  EXPENSE_UPDATED: { icon: Pencil, tone: "text-muted-foreground" },
  EXPENSE_DELETED: { icon: Trash2, tone: "text-negative" },
  SPLIT_CALCULATED: { icon: Calculator, tone: "text-lime-ink" },
  PAYMENT_RECORDED: { icon: HandCoins, tone: "text-positive" },
  PAYMENT_DELETED: { icon: Trash2, tone: "text-negative" },
  REPORT_GENERATED: { icon: FileText, tone: "text-lime-ink" },
  SPLIT_SETTLED: { icon: CircleCheckBig, tone: "text-positive" },
}

export function ActivityFeed({
  items,
  splitNames,
  compact = false,
}: {
  items: Activity[]
  splitNames?: Map<string, string>
  compact?: boolean
}) {
  return (
    <ol className="relative">
      {items.map((item, i) => {
        const { icon: Icon, tone } = meta[item.type] ?? { icon: FilePlus2, tone: "text-muted-foreground" }
        return (
          <motion.li
            key={item.id}
            initial={{ opacity: 0, x: -6 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: Math.min(i, 12) * 0.03 }}
            className="relative flex gap-3 pb-5 last:pb-0"
          >
            {i < items.length - 1 && <span className="absolute left-[15px] top-8 h-[calc(100%-28px)] w-px bg-hairline" aria-hidden />}
            <span className={cn("relative mt-0.5 inline-flex size-8 shrink-0 items-center justify-center rounded-full border border-hairline bg-surface-2", tone)}>
              <Icon className="size-4" aria-hidden />
            </span>
            <div className="min-w-0 flex-1 pt-1">
              <p className={cn("text-sm leading-snug", compact && "line-clamp-2")}>{item.message}</p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                <time dateTime={item.createdAt}>{timeAgo(item.createdAt)}</time>
                {splitNames?.get(item.splitId) && (
                  <>
                    {" · "}
                    <Link to={`/app/splits/${item.splitId}`} className="hover:text-foreground hover:underline">
                      {splitNames.get(item.splitId)}
                    </Link>
                  </>
                )}
              </p>
            </div>
          </motion.li>
        )
      })}
    </ol>
  )
}

export function ActivitySkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="space-y-5" aria-hidden>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex gap-3">
          <Skeleton className="size-8 rounded-full" />
          <div className="flex-1 space-y-2 pt-1">
            <Skeleton className="h-3.5 w-4/5" />
            <Skeleton className="h-3 w-1/4" />
          </div>
        </div>
      ))}
    </div>
  )
}
