import { motion } from "framer-motion"
import type { ReactNode } from "react"
import { Link } from "react-router"

import { Logo } from "@/components/Logo"
import { ThemeToggle } from "@/components/ThemeToggle"

import { BrandPanel } from "./BrandPanel"

export function AuthLayout({ title, subtitle, children, footer }: { title: string; subtitle: ReactNode; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]">
      <div className="flex flex-col px-4 py-5 sm:px-8">
        <header className="flex items-center justify-between">
          <Link to="/" aria-label="SplitMate home" className="rounded-lg">
            <Logo />
          </Link>
          <ThemeToggle />
        </header>
        <motion.main
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
          className="mx-auto flex w-full max-w-[380px] flex-1 flex-col justify-center py-10"
        >
          <h1 className="font-heading text-[32px] font-bold leading-tight">{title}</h1>
          <p className="mt-2 text-[15px] text-muted-foreground">{subtitle}</p>
          <div className="mt-8">{children}</div>
          <div className="mt-8 text-center text-sm text-muted-foreground">{footer}</div>
        </motion.main>
        <p className="text-center text-xs text-muted-foreground">Made for flatmates who'd rather not do maths at dinner.</p>
      </div>
      <div className="hidden p-3 lg:block">
        <BrandPanel />
      </div>
    </div>
  )
}
