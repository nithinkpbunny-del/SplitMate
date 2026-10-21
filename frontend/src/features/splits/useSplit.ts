import { useQuery } from "@tanstack/react-query"

import { splitKeys, splitsApi } from "@/api/splits"

export function useSplit(id: string) {
  return useQuery({ queryKey: splitKeys.detail(id), queryFn: () => splitsApi.get(id) })
}
