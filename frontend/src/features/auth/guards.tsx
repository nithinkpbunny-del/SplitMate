import { Navigate, Outlet, useLocation } from "react-router"

import { Splash } from "@/features/shell/Splash"

import { useMe } from "./useAuth"

/** /app/** requires a logged-in user; otherwise go to /login and come back afterwards. */
export function RequireAuth() {
  const { data: me, isPending } = useMe()
  const location = useLocation()
  if (isPending) return <Splash />
  if (!me) {
    const next = location.pathname + location.search
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace />
  }
  return <Outlet />
}

/** Login/register/landing: logged-in users go straight to where they were heading. */
export function RedirectIfAuthed({ to = "/app" }: { to?: string }) {
  const { data: me, isPending } = useMe()
  const location = useLocation()
  if (isPending) return <Splash />
  if (me) {
    const next = new URLSearchParams(location.search).get("next")
    const target = next && next.startsWith("/") && !next.startsWith("//") ? next : to
    return <Navigate to={target} replace />
  }
  return <Outlet />
}
