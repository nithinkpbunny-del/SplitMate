import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useNavigate } from "react-router"

import { authApi } from "@/api/auth"
import { ApiError } from "@/api/client"
import type { User } from "@/api/types"

export const meKey = ["me"] as const

/** The logged-in user, or null when logged out. */
export function useMe() {
  return useQuery<User | null>({
    queryKey: meKey,
    queryFn: async () => {
      try {
        return await authApi.me()
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) return null
        throw err
      }
    },
    staleTime: Infinity,
    retry: false,
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      queryClient.clear()
      queryClient.setQueryData(meKey, null)
      navigate("/login", { replace: true })
    },
  })
}

/** Only allow same-site relative paths as post-login destinations. */
export function safeNext(next: string | null | undefined, fallback = "/app"): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/login") || next.startsWith("/register")) {
    return fallback
  }
  return next
}
