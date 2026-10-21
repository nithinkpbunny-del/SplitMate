import { CalendarRange, X } from "lucide-react"
import { motion } from "framer-motion"

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { Period } from "@/api/types"
import { formatMonth, toIsoDate } from "@/lib/format"
import { cn } from "@/lib/utils"

const SPLIT_EMOJIS = ["🏠", "🏡", "🛒", "🍕", "✈️", "🚆", "🏖️", "⛰️", "🎉", "🍻", "💡", "🎓"]

const CURRENCIES = [
  { code: "EUR", label: "Euro" },
  { code: "INR", label: "Indian rupee" },
  { code: "USD", label: "US dollar" },
  { code: "GBP", label: "British pound" },
  { code: "CHF", label: "Swiss franc" },
  { code: "PLN", label: "Polish złoty" },
  { code: "CZK", label: "Czech koruna" },
  { code: "SEK", label: "Swedish krona" },
]

export function EmojiPicker({ value, onChange }: { value: string; onChange: (emoji: string) => void }) {
  return (
    <div className="grid grid-cols-6 gap-1.5" role="radiogroup" aria-label="Cover emoji">
      {SPLIT_EMOJIS.map((emoji) => (
        <motion.button
          key={emoji}
          type="button"
          role="radio"
          aria-checked={value === emoji}
          aria-label={emoji}
          whileTap={{ scale: 0.88 }}
          onClick={() => onChange(emoji)}
          className={cn(
            "flex aspect-square items-center justify-center rounded-xl border border-hairline bg-surface-2/50 text-xl transition-colors hover:bg-surface-2",
            value === emoji && "border-lime-ink bg-primary/15",
          )}
        >
          {emoji}
        </motion.button>
      ))}
    </div>
  )
}

export function CurrencySelect({ value, onChange, disabled }: { value: string; onChange: (code: string) => void; disabled?: boolean }) {
  return (
    <Select value={value} onValueChange={onChange} disabled={disabled}>
      <SelectTrigger className="h-11 w-full rounded-xl data-[size=default]:h-11 bg-surface px-3.5 dark:bg-surface-2/60" aria-label="Currency">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {CURRENCIES.map((c) => (
          <SelectItem key={c.code} value={c.code}>
            <span className="font-money text-xs">{c.code}</span> {c.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

/** Months from 6 back to 6 ahead; choosing one fills the period with its first and last day. */
function monthOptions(): Period[] {
  const now = new Date()
  return Array.from({ length: 13 }, (_, i) => {
    const first = new Date(now.getFullYear(), now.getMonth() - 6 + i, 1)
    const last = new Date(first.getFullYear(), first.getMonth() + 1, 0)
    return { start: toIsoDate(first), end: toIsoDate(last) }
  })
}

export function MonthPicker({ value, onChange }: { value: Period | null; onChange: (period: Period | null) => void }) {
  const options = monthOptions()
  const custom = value && !options.some((o) => o.start === value.start && o.end === value.end)
  return (
    <div className="flex gap-2">
      <Select
        value={value ? `${value.start}|${value.end}` : "none"}
        onValueChange={(v) => {
          if (v === "none") onChange(null)
          else {
            const [start, end] = v.split("|")
            onChange({ start, end })
          }
        }}
      >
        <SelectTrigger className="h-11 flex-1 rounded-xl data-[size=default]:h-11 bg-surface px-3.5 dark:bg-surface-2/60" aria-label="Period">
          <CalendarRange className="size-4 text-muted-foreground" />
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="none">No fixed period</SelectItem>
          {custom && value && (
            <SelectItem value={`${value.start}|${value.end}`}>
              {value.start} → {value.end}
            </SelectItem>
          )}
          {options.map((o) => (
            <SelectItem key={o.start} value={`${o.start}|${o.end}`}>
              {formatMonth(o.start)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {value && (
        <button
          type="button"
          onClick={() => onChange(null)}
          className="inline-flex size-11 items-center justify-center rounded-xl border border-hairline text-muted-foreground hover:text-foreground"
          aria-label="Clear period"
        >
          <X className="size-4" />
        </button>
      )}
    </div>
  )
}
