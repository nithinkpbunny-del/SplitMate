import { motion } from "framer-motion"
import type { LucideIcon } from "lucide-react"

import { cn } from "@/lib/utils"

export type TabDef<T extends string> = { id: T; label: string; icon: LucideIcon; badge?: boolean }

/** Underlined tab bar with a sliding indicator; scrolls horizontally on narrow screens. */
export function SplitTabs<T extends string>({ tabs, value, onChange }: { tabs: TabDef<T>[]; value: T; onChange: (tab: T) => void }) {
  return (
    <div role="tablist" aria-label="Split sections" className="scrollbar-none -mx-4 flex gap-1 overflow-x-auto border-b border-hairline px-4 sm:mx-0 sm:px-0">
      {tabs.map((tab) => {
        const selected = tab.id === value
        return (
          <button
            key={tab.id}
            role="tab"
            id={`tab-${tab.id}`}
            aria-selected={selected}
            aria-controls={`panel-${tab.id}`}
            onClick={() => onChange(tab.id)}
            className={cn(
              "relative flex shrink-0 items-center gap-2 rounded-t-lg px-3.5 py-3 text-sm font-medium transition-colors",
              selected ? "text-foreground" : "text-muted-foreground hover:text-foreground",
            )}
          >
            <tab.icon className="size-4" aria-hidden />
            {tab.label}
            {tab.badge && <span className="size-1.5 rounded-full bg-amber-400" aria-label="needs attention" />}
            {selected && <motion.span layoutId="split-tab-indicator" className="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-lime-ink" />}
          </button>
        )
      })}
    </div>
  )
}
