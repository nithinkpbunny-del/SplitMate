import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { settlementKeys, settlementsApi } from "@/api/settlements"
import { splitKeys } from "@/api/splits"

export function useLatestSettlement(splitId: string) {
  return useQuery({ queryKey: settlementKeys.latest(splitId), queryFn: () => settlementsApi.latest(splitId) })
}

export function useRefreshAfterLedgerChange(splitId: string) {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: splitKeys.detail(splitId) })
    queryClient.invalidateQueries({ queryKey: splitKeys.activity(splitId) })
    queryClient.invalidateQueries({ queryKey: settlementKeys.history(splitId) })
    queryClient.invalidateQueries({ queryKey: settlementKeys.payments(splitId) })
    queryClient.invalidateQueries({ queryKey: splitKeys.all, exact: true })
  }
}

export function useCalculate(splitId: string) {
  const queryClient = useQueryClient()
  const refresh = useRefreshAfterLedgerChange(splitId)
  return useMutation({
    mutationFn: () => settlementsApi.calculate(splitId),
    onSuccess: (settlement) => {
      queryClient.setQueryData(settlementKeys.latest(splitId), settlement)
      refresh()
    },
  })
}
