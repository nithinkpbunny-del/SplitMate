/**
 * Money helpers. Amounts are integers in minor units (cents). Parsing works on strings only, so no
 * floating point value ever represents money.
 */

export const MAX_AMOUNT_MINOR = 10_000_000

export type ParseResult = { ok: true; minor: number } | { ok: false; error: string }

const GROUPED_DOT = /^[1-9]\d{0,2}(\.\d{3})+$/
const GROUPED_COMMA = /^[1-9]\d{0,2}(,\d{3})+$/

/**
 * Parses what people type: "86.4", "86,40", "1.234,50", "1,234.50", "€ 12". Mirrors the backend's
 * Money.parse so the live preview and the server always agree.
 */
export function parseMoney(input: string): ParseResult {
  if (!input || !input.trim()) return { ok: false, error: "Enter an amount" }
  let s = input.trim().replace(/[\s '€$£₹]/g, "")
  if (s.startsWith("+")) s = s.slice(1)
  if (s.startsWith("-")) return { ok: false, error: "Amount must be greater than zero" }
  if (!/^[0-9.,]+$/.test(s)) return { ok: false, error: "That doesn't look like an amount" }

  const normalized = normalizeSeparators(s)
  if (normalized === null) return { ok: false, error: "That doesn't look like an amount" }

  const [intPart, fraction = ""] = normalized.split(".")
  if (fraction.length > 2) return { ok: false, error: "Use at most 2 decimal places" }
  if (intPart === "" && fraction === "") return { ok: false, error: "That doesn't look like an amount" }
  if (intPart.length > 9) return { ok: false, error: "That amount is too large" }

  const minor = Number(intPart || "0") * 100 + Number(fraction.padEnd(2, "0"))
  if (minor <= 0) return { ok: false, error: "Amount must be greater than zero" }
  if (minor > MAX_AMOUNT_MINOR) return { ok: false, error: "Amount cannot be more than €100,000.00" }
  return { ok: true, minor }
}

function normalizeSeparators(s: string): string | null {
  const lastDot = s.lastIndexOf(".")
  const lastComma = s.lastIndexOf(",")
  if (lastDot >= 0 && lastComma >= 0) {
    const decimal = lastDot > lastComma ? "." : ","
    const group = decimal === "." ? "," : "."
    const idx = s.lastIndexOf(decimal)
    const intPart = s.slice(0, idx)
    const fraction = s.slice(idx + 1)
    const validGrouping = (group === "." ? GROUPED_DOT : GROUPED_COMMA).test(intPart)
    if (!validGrouping || fraction === "") return null
    return intPart.split(group).join("") + "." + fraction
  }
  const sep = lastDot >= 0 ? "." : lastComma >= 0 ? "," : null
  if (sep === null) return s
  if ((sep === "." ? GROUPED_DOT : GROUPED_COMMA).test(s)) return s.split(sep).join("")
  if (s.indexOf(sep) !== s.lastIndexOf(sep)) return null
  return s.replace(sep, ".")
}

const formatters = new Map<string, Intl.NumberFormat>()

function formatter(currency: string, compact = false): Intl.NumberFormat {
  const key = `${currency}:${compact}`
  let f = formatters.get(key)
  if (!f) {
    f = new Intl.NumberFormat("en-IE", {
      style: "currency",
      currency,
      minimumFractionDigits: compact ? 0 : 2,
      maximumFractionDigits: compact ? 1 : 2,
      notation: compact ? "compact" : "standard",
    })
    formatters.set(key, f)
  }
  return f
}

/** €86.40 */
export function formatMoney(minor: number, currency = "EUR"): string {
  return formatter(currency).format(minorToMajor(minor))
}

/** +€12.00 / −€12.00 (real minus sign). */
export function formatSigned(minor: number, currency = "EUR"): string {
  if (minor === 0) return formatMoney(0, currency)
  return (minor > 0 ? "+" : "−") + formatMoney(Math.abs(minor), currency)
}

/** €1.2K style for chart axes. */
export function formatCompact(minor: number, currency = "EUR"): string {
  return formatter(currency, true).format(minorToMajor(minor))
}

/** Splits an amount into equal shares exactly like the server: the first `remainder` people get +1 cent. */
export function equalShares(amountMinor: number, count: number): number[] {
  if (count <= 0) return []
  const base = Math.floor(amountMinor / count)
  const remainder = amountMinor % count
  return Array.from({ length: count }, (_, i) => base + (i < remainder ? 1 : 0))
}

/** Plain "86.40" for edit forms. */
export function toInputString(minor: number): string {
  const sign = minor < 0 ? "-" : ""
  const abs = Math.abs(minor)
  return `${sign}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, "0")}`
}

/** Exact decimal string ("86.40"); Intl.NumberFormat formats numeric strings without float rounding. */
function minorToMajor(minor: number): `${number}` {
  return toInputString(minor) as `${number}`
}

export function currencySymbol(currency = "EUR"): string {
  return (
    formatter(currency)
      .formatToParts(0)
      .find((p) => p.type === "currency")?.value ?? currency
  )
}
