import { lazy, Suspense } from "react"

import { Skeleton } from "@/components/ui/skeleton"

// The report page pulls in the charting library, so it is loaded on demand instead of with the first screen.
const ReportPage = lazy(() => import("./ReportPage").then((m) => ({ default: m.ReportPage })))

export function LazyReportPage() {
  return (
    <Suspense fallback={<Skeleton className="h-72 rounded-3xl" />}>
      <ReportPage />
    </Suspense>
  )
}
