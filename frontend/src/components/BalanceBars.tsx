import { useMemo } from "react"
import { Bar, BarChart, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"

import type { MemberSummary } from "@/api/types"
import { firstName } from "@/lib/format"
import { formatMoney, formatSigned } from "@/lib/money"

type Row = MemberSummary & { label: string }

const BAR = 20
const ROW = 44

/**
 * Diverging horizontal bars around a zero line: coral to the left (owes), emerald to the right (gets back).
 * Bars are rounded only at the value end; labels use text colours and always carry +/−, so the chart reads
 * without colour. The member cards next to it are the table view of the same numbers.
 */
export function BalanceBars({ members, currency, me }: { members: MemberSummary[]; currency: string; me: string }) {
  // Memoised so a parent re-render with the same data doesn't replay the bar animation.
  const rows: Row[] = useMemo(
    () => [...members].sort((a, b) => b.netMinor - a.netMinor).map((m) => ({ ...m, label: m.userId === me ? "You" : firstName(m.name) })),
    [members, me],
  )
  const max = Math.max(1, ...rows.map((r) => Math.abs(r.netMinor)))
  // headroom on both sides for the value labels
  const domain = [-max * 1.9, max * 1.9]
  const summary = rows.map((r) => `${r.label} ${formatSigned(r.netMinor, currency)}`).join(", ")

  return (
    <div role="img" aria-label={`Balances: ${summary}`} className="w-full">
      <ResponsiveContainer width="100%" height={rows.length * ROW + 16}>
        <BarChart data={rows} layout="vertical" margin={{ top: 8, right: 8, bottom: 8, left: 8 }} barSize={BAR}>
          <XAxis type="number" domain={domain} hide />
          <YAxis
            type="category"
            dataKey="label"
            width={64}
            axisLine={false}
            tickLine={false}
            tick={{ fill: "var(--muted-foreground)", fontSize: 13 }}
          />
          <ReferenceLine x={0} stroke="var(--neutral)" strokeOpacity={0.6} />
          <Tooltip cursor={{ fill: "var(--surface-2)", opacity: 0.6 }} content={<BalanceTooltip currency={currency} />} />
          <Bar dataKey="netMinor" shape={<DataEndBar currency={currency} />} isAnimationActive animationDuration={700}>
            {rows.map((r) => (
              <Cell key={r.userId} fill={r.netMinor >= 0 ? "var(--positive)" : "var(--negative)"} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

type ShapeProps = { x?: number; y?: number; width?: number; height?: number; fill?: string; payload?: Row; currency: string }

/**
 * One bar plus its value label. The bar has a 4px radius on the data end only and is square against the
 * zero line; the label sits just past the data end (or on the zero line, as "settled", for zero).
 */
function DataEndBar({ x = 0, y = 0, width = 0, height = 0, fill, payload, currency }: ShapeProps) {
  const value = payload?.netMinor ?? 0
  // Direction comes from the value, not the geometry (Recharts may report negative bars either way).
  const positive = value >= 0
  const left = Math.min(x, x + width)
  const w = Math.abs(width)
  const r = Math.min(4, w / 2, height / 2)
  const d =
    w < 0.5
      ? null
      : positive
        ? `M${left},${y} h${w - r} a${r},${r} 0 0 1 ${r},${r} v${height - 2 * r} a${r},${r} 0 0 1 -${r},${r} h-${w - r} z`
        : `M${left + w},${y} h-${w - r} a${r},${r} 0 0 0 -${r},${r} v${height - 2 * r} a${r},${r} 0 0 0 ${r},${r} h${w - r} z`
  const end = positive ? left + w : left
  return (
    <g>
      {d && <path d={d} fill={fill} />}
      <text
        x={positive ? end + 8 : end - 8}
        y={y + height / 2}
        dominantBaseline="central"
        textAnchor={positive ? "start" : "end"}
        className="font-money"
        fontSize={12.5}
        fontWeight={600}
        fill={value === 0 ? "var(--muted-foreground)" : "var(--foreground)"}
      >
        {value === 0 ? "settled" : formatSigned(value, currency)}
      </text>
    </g>
  )
}

function BalanceTooltip({ active, payload, currency }: { active?: boolean; payload?: { payload: Row }[]; currency: string }) {
  if (!active || !payload?.length) return null
  const r = payload[0].payload
  return (
    <div className="min-w-44 rounded-xl border border-hairline bg-popover px-3 py-2.5 text-xs shadow-xl">
      <p className="mb-1.5 font-semibold text-foreground">{r.name}</p>
      <dl className="space-y-0.5 text-muted-foreground">
        <div className="flex justify-between gap-4">
          <dt>Paid</dt>
          <dd className="font-money text-foreground">{formatMoney(r.paidMinor, currency)}</dd>
        </div>
        <div className="flex justify-between gap-4">
          <dt>Their share</dt>
          <dd className="font-money text-foreground">{formatMoney(r.shareMinor, currency)}</dd>
        </div>
        {(r.paymentsSentMinor > 0 || r.paymentsReceivedMinor > 0) && (
          <div className="flex justify-between gap-4">
            <dt>Payments</dt>
            <dd className="font-money text-foreground">{formatSigned(r.paymentsSentMinor - r.paymentsReceivedMinor, currency)}</dd>
          </div>
        )}
        <div className="flex justify-between gap-4 border-t border-hairline pt-1">
          <dt>Net</dt>
          <dd className="font-money font-semibold text-foreground">{formatSigned(r.netMinor, currency)}</dd>
        </div>
      </dl>
    </div>
  )
}
