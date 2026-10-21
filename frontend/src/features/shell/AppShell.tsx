import { AnimatePresence, motion } from "framer-motion"
import { Link, Outlet, useLocation } from "react-router"

import { Logo } from "@/components/Logo"
import { ThemeToggle } from "@/components/ThemeToggle"
import { useMe } from "@/features/auth/useAuth"

import { UserMenu } from "./UserMenu"

export function AppShell() {
  const { data: me } = useMe()
  const location = useLocation()
  // Animate between pages, but not between tabs of the same split.
  const pageKey = location.pathname.split("/").slice(0, 4).join("/")
  return (
    <div className="min-h-dvh">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[200] focus:rounded-lg focus:bg-primary focus:px-3 focus:py-2 focus:text-primary-foreground"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-40 border-b border-hairline bg-background/75 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
          <Link to="/app" className="rounded-lg" aria-label="SplitMate dashboard">
            <Logo />
          </Link>
          <div className="flex items-center gap-1.5">
            <ThemeToggle />
            {me && <UserMenu user={me} />}
          </div>
        </div>
      </header>
      <AnimatePresence mode="wait" initial={false}>
        <motion.main
          id="main"
          key={pageKey}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -6 }}
          transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
          className="mx-auto max-w-6xl px-4 pb-28 pt-6 sm:px-6 sm:pt-8"
        >
          <Outlet />
        </motion.main>
      </AnimatePresence>
    </div>
  )
}
