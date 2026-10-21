import { MutationCache, QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MotionConfig } from "framer-motion"
import { useEffect } from "react"
import { RouterProvider } from "react-router"
import { toast, Toaster } from "sonner"

import { ApiError, onUnauthorized, primeCsrf } from "@/api/client"
import { ThemeProvider } from "@/components/ThemeProvider"
import { TooltipProvider } from "@/components/ui/tooltip"
import { meKey } from "@/features/auth/useAuth"
import { useTheme } from "@/hooks/useTheme"
import { router } from "@/routes"

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: true,
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
    },
  },
  // Every failed mutation shows a toast with the server's message unless it handles errors itself.
  mutationCache: new MutationCache({
    onError: (error, _vars, _ctx, mutation) => {
      if (mutation.meta?.silent) return
      toast.error(error instanceof ApiError ? error.message : "Something went wrong")
    },
  }),
})

// Any 401 from a protected endpoint means the session is gone: drop the user so guards redirect to /login.
onUnauthorized(() => {
  if (queryClient.getQueryData(meKey)) toast.info("Your session has ended. Please log in again.")
  queryClient.setQueryData(meKey, null)
})

function ThemedToaster() {
  const { resolved } = useTheme()
  return (
    <Toaster
      theme={resolved}
      position="bottom-center"
      offset={104}
      mobileOffset={88}
      toastOptions={{
        classNames: {
          toast: "!rounded-2xl !border-hairline !bg-popover !text-popover-foreground !shadow-2xl !font-sans",
          description: "!text-muted-foreground",
        },
      }}
    />
  )
}

export default function App() {
  useEffect(primeCsrf, [])
  return (
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <MotionConfig reducedMotion="user">
          <TooltipProvider delayDuration={300}>
            <RouterProvider router={router} />
            <ThemedToaster />
          </TooltipProvider>
        </MotionConfig>
      </QueryClientProvider>
    </ThemeProvider>
  )
}
