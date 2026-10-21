import { useQuery } from "@tanstack/react-query"
import { motion } from "framer-motion"
import { Archive, CircleCheckBig, History } from "lucide-react"
import { useParams, useSearchParams } from "react-router"

import { ApiError } from "@/api/client"
import { splitKeys, splitsApi } from "@/api/splits"
import type { Split } from "@/api/types"
import { ActivityFeed, ActivitySkeleton } from "@/components/ActivityFeed"
import { Skeleton } from "@/components/ui/skeleton"
import { NotFoundPage } from "@/features/shell/NotFoundPage"

import { SplitHeader } from "./SplitHeader"
import { SplitTabs, type TabDef } from "./SplitTabs"
import { useSplit } from "./useSplit"

type TabId = "activity"

const tabs: TabDef<TabId>[] = [{ id: "activity", label: "Activity", icon: History }]

export function SplitPage() {
  const { splitId = "" } = useParams()
  const split = useSplit(splitId)
  const [params, setParams] = useSearchParams()
  const tab = (tabs.find((t) => t.id === params.get("tab"))?.id ?? tabs[0].id) as TabId
  const setTab = (next: TabId) => setParams((p) => ({ ...Object.fromEntries(p), tab: next }), { replace: true })

  if (split.isError && split.error instanceof ApiError && split.error.status === 404) {
    return <NotFoundPage title="Split not found" message="This split doesn't exist, or you're not a member of it. Ask a member for the invite link." />
  }

  if (split.isPending || !split.data) {
    return (
      <div className="space-y-6" aria-busy="true">
        <Skeleton className="h-[230px] rounded-[28px]" />
        <Skeleton className="h-10 w-80" />
        <Skeleton className="h-64 rounded-3xl" />
      </div>
    )
  }

  const data = split.data
  return (
    <div className="space-y-6">
      <SplitHeader split={data} />
      <StatusBanner split={data} />
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
        {tab === "activity" && <ActivityTab splitId={data.id} />}
      </motion.section>
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

function ActivityTab({ splitId }: { splitId: string }) {
  const activity = useQuery({ queryKey: splitKeys.activity(splitId), queryFn: () => splitsApi.activity(splitId, 100) })
  return (
    <div className="rounded-3xl border border-hairline bg-surface p-5 sm:p-6">
      {activity.isPending ? <ActivitySkeleton rows={6} /> : <ActivityFeed items={activity.data ?? []} />}
    </div>
  )
}
