import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useNavigate } from "react-router"
import { toast } from "sonner"

import { reportKeys, reportsApi } from "@/api/reports"
import { splitKeys } from "@/api/splits"

/** Creates a report for the split's default period and opens it. Shared by the tab and the calculation results. */
export function useGenerateReport(splitId: string) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: () => reportsApi.generate(splitId),
    onSuccess: (report) => {
      queryClient.setQueryData(reportKeys.detail(report.id), report)
      queryClient.invalidateQueries({ queryKey: reportKeys.list(splitId) })
      queryClient.invalidateQueries({ queryKey: splitKeys.activity(splitId) })
      toast.success("Report ready")
      navigate(`/app/splits/${splitId}/reports/${report.id}`)
    },
  })
}
