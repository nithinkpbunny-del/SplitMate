import { cn } from "@/lib/utils"

/** The SplitMate mark: a coin cut in two, one half in lime. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" className={cn("size-7", className)}>
      <circle cx="16" cy="16" r="13" fill="none" stroke="currentColor" strokeWidth="2.5" />
      <path d="M16 3a13 13 0 0 0 0 26Z" fill="var(--lime)" />
      <path d="M16 3a13 13 0 0 0 0 26" fill="none" stroke="currentColor" strokeWidth="2.5" />
      <path d="M16 1.5v29" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
    </svg>
  )
}

export function Logo({ className, compact = false }: { className?: string; compact?: boolean }) {
  return (
    <span className={cn("inline-flex items-center gap-2 font-heading text-lg font-bold tracking-tight", className)}>
      <LogoMark />
      {!compact && (
        <span>
          Split<span className="text-lime-ink">Mate</span>
        </span>
      )}
    </span>
  )
}
