import { motion } from "framer-motion"
import { Calculator, Plus } from "lucide-react"

import { Button } from "@/components/ui/button"

/**
 * Primary floating actions. Desktop: a pill bar at the bottom centre. Mobile: a round "+" button
 * in the thumb zone, with Calculate beside it.
 */
export function SplitActionBar({ onAdd, onCalculate }: { onAdd: () => void; onCalculate?: () => void }) {
  return (
    <>
      <motion.div
        initial={{ y: 80, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ type: "spring", stiffness: 260, damping: 26, delay: 0.2 }}
        className="fixed bottom-6 left-1/2 z-30 hidden -translate-x-1/2 items-center gap-1.5 rounded-2xl border border-hairline bg-popover/90 p-1.5 shadow-2xl backdrop-blur-xl md:flex"
      >
        <Button size="xl" onClick={onAdd}>
          <Plus /> Add expense
          <kbd className="ml-1 rounded bg-black/10 px-1.5 font-mono text-[11px]">N</kbd>
        </Button>
        {onCalculate && (
          <Button size="xl" variant="ghost" onClick={onCalculate}>
            <Calculator /> Calculate split
            <kbd className="ml-1 rounded border border-hairline px-1.5 font-mono text-[11px] text-muted-foreground">C</kbd>
          </Button>
        )}
      </motion.div>

      <div className="fixed bottom-[max(1.25rem,env(safe-area-inset-bottom))] right-4 z-30 flex items-center gap-2 md:hidden">
        {onCalculate && (
          <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ delay: 0.25 }}>
            <Button size="xl" variant="outline" onClick={onCalculate} className="h-12 rounded-2xl bg-popover/90 shadow-xl backdrop-blur" aria-label="Calculate split">
              <Calculator /> Calculate
            </Button>
          </motion.div>
        )}
        <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: "spring", stiffness: 300, damping: 18, delay: 0.15 }}>
          <Button onClick={onAdd} className="size-14 rounded-full shadow-[0_12px_40px_-8px_var(--glow)] [&_svg:not([class*='size-'])]:size-6" aria-label="Add expense">
            <Plus />
          </Button>
        </motion.div>
      </div>
    </>
  )
}
