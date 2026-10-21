import { RotateCcw } from "lucide-react"
import { isRouteErrorResponse, Link, useRouteError } from "react-router"

import { Logo } from "@/components/Logo"
import { Button } from "@/components/ui/button"

import { NotFoundPage } from "./NotFoundPage"

/** Error boundary for the router: still on-brand, with a way out. */
export function RouteError() {
  const error = useRouteError()
  if (isRouteErrorResponse(error) && error.status === 404) return <NotFoundPage />
  const message = error instanceof Error ? error.message : "An unexpected error occurred."
  return (
    <div className="relative flex min-h-dvh flex-col items-center justify-center px-4 text-center">
      <div className="glow-radial pointer-events-none absolute inset-0 opacity-60" />
      <Link to="/" className="absolute left-4 top-5 sm:left-8">
        <Logo />
      </Link>
      <div className="relative w-full max-w-md rounded-3xl border border-hairline bg-surface p-8 shadow-xl">
        <p className="font-mono text-xs uppercase tracking-[0.2em] text-negative">Something broke</p>
        <h1 className="mt-3 font-heading text-2xl font-bold">The numbers didn't add up this time</h1>
        <p className="mt-2 text-sm text-muted-foreground">Your data is safe. Try reloading the page.</p>
        <pre className="mt-5 max-h-32 overflow-auto rounded-xl bg-surface-2 p-3 text-left font-mono text-xs text-muted-foreground">{message}</pre>
        <div className="mt-6 flex justify-center gap-2">
          <Button size="xl" onClick={() => window.location.reload()}>
            <RotateCcw /> Reload
          </Button>
          <Button size="xl" variant="outline" asChild>
            <Link to="/app">Dashboard</Link>
          </Button>
        </div>
      </div>
    </div>
  )
}
