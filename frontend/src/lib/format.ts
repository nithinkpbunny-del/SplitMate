const dayFormatter = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short" })
const longDayFormatter = new Intl.DateTimeFormat("en-GB", { weekday: "long", day: "numeric", month: "long" })
const shortDateFormatter = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" })
const monthFormatter = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric" })
const relative = new Intl.RelativeTimeFormat("en", { numeric: "auto" })

/** Parses a yyyy-MM-dd string as a local calendar date (no time zone shift). */
export function parseLocalDate(value: string): Date {
  const [y, m, d] = value.split("-").map(Number)
  return new Date(y, m - 1, d)
}

export function toIsoDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, "0")
  const d = String(date.getDate()).padStart(2, "0")
  return `${y}-${m}-${d}`
}

export function todayIso(): string {
  return toIsoDate(new Date())
}

export function formatDay(value: string): string {
  return dayFormatter.format(parseLocalDate(value))
}

export function formatLongDay(value: string): string {
  const date = parseLocalDate(value)
  const today = todayIso()
  if (value === today) return "Today"
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  if (value === toIsoDate(yesterday)) return "Yesterday"
  return longDayFormatter.format(date)
}

export function formatShortDate(value: string): string {
  return shortDateFormatter.format(parseLocalDate(value))
}

export function formatMonth(value: string): string {
  return monthFormatter.format(parseLocalDate(value))
}

export function formatPeriod(start?: string | null, end?: string | null): string | null {
  if (!start || !end) return null
  const s = parseLocalDate(start)
  const e = parseLocalDate(end)
  const lastOfMonth = new Date(s.getFullYear(), s.getMonth() + 1, 0).getDate()
  if (s.getDate() === 1 && e.getMonth() === s.getMonth() && e.getFullYear() === s.getFullYear() && e.getDate() === lastOfMonth) {
    return monthFormatter.format(s)
  }
  return `${shortDateFormatter.format(s)} – ${shortDateFormatter.format(e)}`
}

/** "3 minutes ago", "yesterday"... */
export function timeAgo(iso: string): string {
  const diff = (new Date(iso).getTime() - Date.now()) / 1000
  const abs = Math.abs(diff)
  if (abs < 45) return "just now"
  if (abs < 3600) return relative.format(Math.round(diff / 60), "minute")
  if (abs < 86400) return relative.format(Math.round(diff / 3600), "hour")
  if (abs < 86400 * 7) return relative.format(Math.round(diff / 86400), "day")
  return shortDateFormatter.format(new Date(iso))
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return "?"
  if (parts.length === 1) return parts[0][0].toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

export function firstName(name: string): string {
  return name.trim().split(/\s+/)[0] ?? name
}

export function plural(count: number, singular: string, pluralForm = singular + "s"): string {
  return `${count} ${count === 1 ? singular : pluralForm}`
}

/** First and last day of the month `offset` months from now. */
export function monthPeriod(offset = 0): { start: string; end: string } {
  const now = new Date()
  const first = new Date(now.getFullYear(), now.getMonth() + offset, 1)
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0)
  return { start: toIsoDate(first), end: toIsoDate(last) }
}
