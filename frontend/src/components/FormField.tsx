import { AnimatePresence, motion } from "framer-motion"
import type { ReactNode } from "react"

import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils"

/** Label + control + animated inline error. The control should set aria-invalid / aria-describedby. */
export function FormField({
  id,
  label,
  error,
  hint,
  children,
  className,
  trailing,
}: {
  id: string
  label: string
  error?: string
  hint?: ReactNode
  children: ReactNode
  className?: string
  trailing?: ReactNode
}) {
  return (
    <div className={cn("space-y-1.5", className)}>
      <div className="flex items-center justify-between">
        <Label htmlFor={id} className="text-[13px] font-medium text-muted-foreground">
          {label}
        </Label>
        {trailing}
      </div>
      {children}
      <AnimatePresence initial={false}>
        {error ? (
          <motion.p
            key="error"
            id={`${id}-error`}
            role="alert"
            initial={{ opacity: 0, y: -4, height: 0 }}
            animate={{ opacity: 1, y: 0, height: "auto" }}
            exit={{ opacity: 0, y: -4, height: 0 }}
            className="text-[13px] text-negative"
          >
            {error}
          </motion.p>
        ) : hint ? (
          <p className="text-[13px] text-muted-foreground">{hint}</p>
        ) : null}
      </AnimatePresence>
    </div>
  )
}

export const inputClass =
  "h-11 rounded-xl bg-surface px-3.5 text-[15px] dark:bg-surface-2/60 border-input focus-visible:ring-ring/30"
