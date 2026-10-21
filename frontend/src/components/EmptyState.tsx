import { motion } from "framer-motion"
import type { LucideIcon } from "lucide-react"
import type { ReactNode } from "react"

import { cn } from "@/lib/utils"

/** A composed "illustration" of three tilted icon tiles, a title, one line of help and a single CTA. */
export function EmptyState({
  icons,
  title,
  description,
  action,
  className,
}: {
  icons: [LucideIcon, LucideIcon, LucideIcon]
  title: string
  description: ReactNode
  action?: ReactNode
  className?: string
}) {
  const [Left, Center, Right] = icons
  return (
    <div className={cn("flex flex-col items-center rounded-3xl border border-dashed border-hairline px-6 py-14 text-center", className)}>
      <div className="relative mb-6 h-20 w-40" aria-hidden>
        <motion.span
          initial={{ rotate: 0, x: 0, opacity: 0 }}
          animate={{ rotate: -12, x: -34, opacity: 1 }}
          transition={{ delay: 0.1, type: "spring", stiffness: 200, damping: 18 }}
          className="absolute left-1/2 top-3 -ml-7 inline-flex size-14 items-center justify-center rounded-2xl border border-hairline bg-surface-2 text-muted-foreground"
        >
          <Left className="size-6" />
        </motion.span>
        <motion.span
          initial={{ rotate: 0, x: 0, opacity: 0 }}
          animate={{ rotate: 12, x: 34, opacity: 1 }}
          transition={{ delay: 0.15, type: "spring", stiffness: 200, damping: 18 }}
          className="absolute left-1/2 top-3 -ml-7 inline-flex size-14 items-center justify-center rounded-2xl border border-hairline bg-surface-2 text-muted-foreground"
        >
          <Right className="size-6" />
        </motion.span>
        <motion.span
          initial={{ y: 10, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ type: "spring", stiffness: 220, damping: 18 }}
          className="absolute left-1/2 top-0 -ml-8 inline-flex size-16 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-[0_10px_40px_-8px_var(--glow)]"
        >
          <Center className="size-7" />
        </motion.span>
      </div>
      <h3 className="font-heading text-xl font-bold">{title}</h3>
      <p className="mt-1.5 max-w-sm text-[15px] text-muted-foreground">{description}</p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  )
}
