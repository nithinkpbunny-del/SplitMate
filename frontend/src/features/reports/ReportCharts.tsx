import { Table2 } from "lucide-react"
import { useMemo, useState, type ReactNode } from "react"
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ReferenceDot,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts"

import type { CategoryStat, MemberStat, ReportStats } from "@/api/types"
import { useTheme } from "@/hooks/useTheme"
import { firstName, formatDay, parseLocalDate } from "@/lib/format"
import { formatCompact, formatMoney, formatWhole } from "@/lib/money"
import { cn } from "@/lib/utils"

/*
 * Chart colours come from the dataviz palette (CSS variables --viz-*, validated for colour-blind separation
 * and contrast on the surface). Single-series charts use slot 1; the donut uses slots 1-5 in rank order and
 * folds the rest into a neutral "Other". Text always wears text tokens, never the series colour.
 */
const SLOTS = ["var(--viz-1)", "var(--viz-2)", "var(--viz-3)", "var(--viz-4)", "var(--viz-5)"]
const OTHER = "var(--viz-other)"
const GRID = "var(--hairline)"
const TICK = { fill: "var(--muted-foreground)", fontSize: 11.5 }

// ----------------------------------------------------------------------------- shared pieces

export function ChartCard({
  title,
  subtitle,
  table,
  children,
  className,
}: {
  title: string
  subtitle?: string
  /** Same data as a table, for people who prefer or need numbers over shapes. */
  table?: { columns: string[]; rows: ReactNode[][] }
  children: ReactNode
  className?: string
}) {
  const [asTable, setAsTable] = useState(false)
  return (
    <section className={cn("rounded-3xl border border-hairline bg-surface p-5 sm:p-6", className)}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="font-heading text-lg font-bold">{title}</h3>
          {subtitle && <p className="mt-0.5 text-sm text-muted-foreground">{subtitle}</p>}
        </div>
        {table && (
          <button
            type="button"
            onClick={() => setAsTable((v) => !v)}
            aria-pressed={asTable}
            className={cn(
              "inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-hairline px-2.5 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground",
              asTable && "bg-surface-2 text-foreground",
            )}
          >
            <Table2 className="size-3.5" /> {asTable ? "Chart" : "Table"}
          </button>
        )}
      </div>
      <div className="mt-4">
        {asTable && table ? (
          <div className="max-h-80 overflow-auto rounded-xl border border-hairline">
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-surface-2 text-left text-xs text-muted-foreground">
                <tr>
                  {table.columns.map((c, i) => (
                    <th key={c} className={cn("px-3 py-2 font-medium", i > 0 && "text-right")}>
                      {c}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {table.rows.map((row, r) => (
                  <tr key={r} className="border-t border-hairline">
                    {row.map((cell, i) => (
                      <td key={i} className={cn("px-3 py-1.5", i > 0 && "text-right font-money")}>
                        {cell}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          children
        )}
      </div>
    </section>
  )
}

function TooltipCard({ title, rows }: { title: string; rows: { label: string; value: string; swatch?: string }[] }) {
  return (
    <div className="min-w-40 rounded-xl border border-hairline bg-popover px-3 py-2.5 text-xs shadow-xl">
      <p className="mb-1.5 font-semibold text-foreground">{title}</p>
      <dl className="space-y-1">
        {rows.map((r) => (
          <div key={r.label} className="flex items-center justify-between gap-4">
            <dt className="flex items-center gap-1.5 text-muted-foreground">
              {r.swatch && <span className="size-2 rounded-[2px]" style={{ backgroundColor: r.swatch }} />}
              {r.label}
            </dt>
            <dd className="font-money text-foreground">{r.value}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}

function Legend({ items }: { items: { label: string; color: string }[] }) {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground" aria-label="Legend">
      {items.map((i) => (
        <li key={i.label} className="flex items-center gap-1.5">
          <span className="size-2.5 rounded-[3px]" style={{ backgroundColor: i.color }} />
          {i.label}
        </li>
      ))}
    </ul>
  )
}

// ----------------------------------------------------------------------------- category donut

type Slice = { name: string; value: number; color: string; percent: number; count: number }

/** Top five categories in colour, everything else folded into one neutral "Other" slice. */
function slices(categories: CategoryStat[]): { slices: Slice[]; rest: CategoryStat[] } {
  const top = categories.slice(0, 5)
  const rest = categories.slice(5)
  const result: Slice[] = top.map((c, i) => ({ name: c.name, value: c.totalMinor, color: SLOTS[i], percent: c.percent, count: c.count }))
  if (rest.length > 0) {
    result.push({
      name: `Other (${rest.length})`,
      value: rest.reduce((s, c) => s + c.totalMinor, 0),
      color: OTHER,
      percent: Math.round(rest.reduce((s, c) => s + c.percent, 0) * 10) / 10,
      count: rest.reduce((s, c) => s + c.count, 0),
    })
  }
  return { slices: result, rest }
}

export function CategoryDonut({ stats, currency }: { stats: ReportStats; currency: string }) {
  const { slices: data } = useMemo(() => slices(stats.byCategory), [stats.byCategory])
  const colorByName = new Map(data.map((d) => [d.name, d.color]))
  const topNames = new Set(data.map((d) => d.name))
  return (
    <ChartCard
      title="Where the money went"
      subtitle="Share of total spending by category"
      table={{
        columns: ["Category", "Expenses", "Total", "Share"],
        rows: stats.byCategory.map((c) => [c.name, c.count, formatMoney(c.totalMinor, currency), `${c.percent.toFixed(1)}%`]),
      }}
    >
      <div className="grid grid-cols-1 items-center gap-6 sm:grid-cols-[220px_minmax(0,1fr)]">
        <div className="relative mx-auto size-[220px]" role="img" aria-label={`Donut chart: ${data.map((d) => `${d.name} ${d.percent}%`).join(", ")}`}>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Tooltip
                content={({ active, payload }) => {
                  const p = active ? (payload?.[0]?.payload as Slice | undefined) : undefined
                  return p ? (
                    <TooltipCard
                      title={p.name}
                      rows={[
                        { label: "Total", value: formatMoney(p.value, currency), swatch: p.color },
                        { label: "Share", value: `${p.percent}%` },
                        { label: "Expenses", value: String(p.count) },
                      ]}
                    />
                  ) : null
                }}
              />
              <Pie data={data} dataKey="value" nameKey="name" innerRadius={72} outerRadius={104} paddingAngle={0} startAngle={90} endAngle={-270} stroke="var(--surface)" strokeWidth={2} isAnimationActive animationDuration={800}>
                {data.map((d) => (
                  <Cell key={d.name} fill={d.color} />
                ))}
              </Pie>
            </PieChart>
          </ResponsiveContainer>
          <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-center">
            <span className="text-xs text-muted-foreground">Total</span>
            <span className="font-money text-xl font-semibold">{formatWhole(stats.totalSpendMinor, currency)}</span>
          </div>
        </div>
        <ul className="space-y-1.5">
          {stats.byCategory.map((c) => (
            <li key={c.categoryId} className="flex items-center gap-2.5 text-sm">
              <span className="size-2.5 shrink-0 rounded-[3px]" style={{ backgroundColor: topNames.has(c.name) ? colorByName.get(c.name) : OTHER }} aria-hidden />
              <span className="min-w-0 flex-1 truncate">{c.name}</span>
              <span className="font-money text-muted-foreground">{formatMoney(c.totalMinor, currency)}</span>
              <span className="w-12 text-right font-money text-xs text-muted-foreground">{c.percent.toFixed(1)}%</span>
            </li>
          ))}
        </ul>
      </div>
    </ChartCard>
  )
}

// ----------------------------------------------------------------------------- daily trend

export function DailyTrend({ stats, currency }: { stats: ReportStats; currency: string }) {
  const data = stats.dailyTrend
  const peak = data.reduce((best, d) => (d.totalMinor > best.totalMinor ? d : best), data[0])
  return (
    <ChartCard
      title="Spending day by day"
      subtitle={`${stats.days} days · busiest day ${formatDay(peak.date)}`}
      table={{
        columns: ["Date", "Spent"],
        rows: data.filter((d) => d.totalMinor > 0).map((d) => [formatDay(d.date), formatMoney(d.totalMinor, currency)]),
      }}
    >
      <div role="img" aria-label={`Area chart of daily spending. Busiest day ${formatDay(peak.date)} with ${formatMoney(peak.totalMinor, currency)}.`}>
        <ResponsiveContainer width="100%" height={240}>
          <AreaChart data={data} margin={{ top: 22, right: 18, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke={GRID} />
            <XAxis dataKey="date" tick={TICK} tickLine={false} axisLine={{ stroke: GRID }} interval="preserveStartEnd" minTickGap={28} tickFormatter={(v: string) => parseLocalDate(v).toLocaleDateString("en-GB", { day: "numeric", month: "short" })} />
            <YAxis tick={TICK} tickLine={false} axisLine={false} width={52} tickFormatter={(v: number) => formatCompact(v, currency)} />
            <Tooltip
              cursor={{ stroke: "var(--muted-foreground)", strokeOpacity: 0.5, strokeDasharray: "0" }}
              content={({ active, payload }) => {
                const p = active ? (payload?.[0]?.payload as { date: string; totalMinor: number } | undefined) : undefined
                return p ? <TooltipCard title={formatDay(p.date)} rows={[{ label: "Spent", value: formatMoney(p.totalMinor, currency), swatch: SLOTS[0] }]} /> : null
              }}
            />
            <Area type="monotone" dataKey="totalMinor" stroke={SLOTS[0]} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" fill={SLOTS[0]} fillOpacity={0.1} activeDot={{ r: 5, fill: SLOTS[0], stroke: "var(--surface)", strokeWidth: 2 }} isAnimationActive animationDuration={900} />
            {peak.totalMinor > 0 && (
              <ReferenceDot
                x={peak.date}
                y={peak.totalMinor}
                r={5}
                fill={SLOTS[0]}
                stroke="var(--surface)"
                strokeWidth={2}
                label={{ value: formatWhole(peak.totalMinor, currency), position: "top", fill: "var(--foreground)", fontSize: 12, fontWeight: 600 }}
              />
            )}
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  )
}

// ----------------------------------------------------------------------------- weekday pattern

const WEEKDAYS: Record<string, string> = { MONDAY: "Mon", TUESDAY: "Tue", WEDNESDAY: "Wed", THURSDAY: "Thu", FRIDAY: "Fri", SATURDAY: "Sat", SUNDAY: "Sun" }

export function WeekdayBars({ stats, currency }: { stats: ReportStats; currency: string }) {
  const data = stats.weekdayPattern.map((w) => ({ day: WEEKDAYS[w.weekday] ?? w.weekday, full: w.weekday.charAt(0) + w.weekday.slice(1).toLowerCase(), totalMinor: w.totalMinor }))
  const max = data.reduce((m, d) => Math.max(m, d.totalMinor), 0)
  return (
    <ChartCard
      title="Spending by weekday"
      subtitle="Which days of the week cost the most"
      table={{ columns: ["Weekday", "Spent"], rows: data.map((d) => [d.full, formatMoney(d.totalMinor, currency)]) }}
    >
      <div role="img" aria-label={`Column chart of spending by weekday. ${data.map((d) => `${d.full} ${formatMoney(d.totalMinor, currency)}`).join(", ")}`}>
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={data} margin={{ top: 22, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke={GRID} />
            <XAxis dataKey="day" tick={TICK} tickLine={false} axisLine={{ stroke: GRID }} />
            <YAxis tick={TICK} tickLine={false} axisLine={false} width={52} tickFormatter={(v: number) => formatCompact(v, currency)} />
            <Tooltip
              cursor={{ fill: "var(--surface-2)", opacity: 0.6 }}
              content={({ active, payload }) => {
                const p = active ? (payload?.[0]?.payload as (typeof data)[number] | undefined) : undefined
                return p ? <TooltipCard title={p.full} rows={[{ label: "Spent", value: formatMoney(p.totalMinor, currency), swatch: SLOTS[0] }]} /> : null
              }}
            />
            <Bar
              dataKey="totalMinor"
              fill={SLOTS[0]}
              barSize={24}
              radius={[4, 4, 0, 0]}
              isAnimationActive
              animationDuration={700}
              label={(props: { x?: string | number; y?: string | number; width?: string | number; value?: unknown }) =>
                Number(props.value) === max && max > 0 ? (
                  <text x={Number(props.x ?? 0) + Number(props.width ?? 0) / 2} y={Number(props.y ?? 0) - 8} textAnchor="middle" fontSize={12} fontWeight={600} fill="var(--foreground)">
                    {formatWhole(max, currency)}
                  </text>
                ) : (
                  <g />
                )
              }
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  )
}

// ----------------------------------------------------------------------------- paid vs share per member

export function MemberBars({ members, currency }: { members: MemberStat[]; currency: string }) {
  const data = members.map((m) => ({ name: firstName(m.name), full: m.name, paid: m.paidMinor, share: m.shareMinor, net: m.netMinor }))
  return (
    <ChartCard
      title="Paid vs their share"
      subtitle="What each person put out, next to what they actually used"
      table={{
        columns: ["Person", "Paid", "Their share", "Net"],
        rows: data.map((d) => [d.full, formatMoney(d.paid, currency), formatMoney(d.share, currency), `${d.net > 0 ? "+" : d.net < 0 ? "−" : ""}${formatMoney(Math.abs(d.net), currency)}`]),
      }}
    >
      <Legend
        items={[
          { label: "Paid", color: SLOTS[0] },
          { label: "Their share", color: SLOTS[1] },
        ]}
      />
      <div className="mt-3" role="img" aria-label={`Grouped columns of paid versus share. ${data.map((d) => `${d.full} paid ${formatMoney(d.paid, currency)}, share ${formatMoney(d.share, currency)}`).join(". ")}`}>
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barGap={2} barCategoryGap="30%">
            <CartesianGrid vertical={false} stroke={GRID} />
            <XAxis dataKey="name" tick={TICK} tickLine={false} axisLine={{ stroke: GRID }} />
            <YAxis tick={TICK} tickLine={false} axisLine={false} width={52} tickFormatter={(v: number) => formatCompact(v, currency)} />
            <Tooltip
              cursor={{ fill: "var(--surface-2)", opacity: 0.6 }}
              content={({ active, payload }) => {
                const p = active ? (payload?.[0]?.payload as (typeof data)[number] | undefined) : undefined
                return p ? (
                  <TooltipCard
                    title={p.full}
                    rows={[
                      { label: "Paid", value: formatMoney(p.paid, currency), swatch: SLOTS[0] },
                      { label: "Their share", value: formatMoney(p.share, currency), swatch: SLOTS[1] },
                      { label: "Net", value: `${p.net > 0 ? "+" : p.net < 0 ? "−" : ""}${formatMoney(Math.abs(p.net), currency)}` },
                    ]}
                  />
                ) : null
              }}
            />
            <Bar dataKey="paid" fill={SLOTS[0]} barSize={20} radius={[4, 4, 0, 0]} isAnimationActive animationDuration={700} />
            <Bar dataKey="share" fill={SLOTS[1]} barSize={20} radius={[4, 4, 0, 0]} isAnimationActive animationDuration={700} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  )
}

// ----------------------------------------------------------------------------- member × category heat-map

/**
 * One-hue blue ramp. "Near zero" always recedes toward the surface and "more" is always more prominent:
 * on the dark surface lightness rises with value (dark to light), on the light surface it falls (light to dark).
 */
// #2a78d6 is left out: neither white (4.42) nor ink (4.43) text reaches 4.5:1 on it. Every remaining step does.
const RAMP_DARK = ["#0d366b", "#104281", "#184f95", "#1c5cab", "#256abf", "#3987e5", "#5598e7", "#6da7ec", "#86b6ef"]
const RAMP_LIGHT = [...RAMP_DARK].reverse()

function heat(ratio: number, dark: boolean): { background: string; color: string } {
  if (ratio <= 0) return { background: "transparent", color: "var(--muted-foreground)" }
  const ramp = dark ? RAMP_DARK : RAMP_LIGHT
  const index = Math.min(ramp.length - 1, Math.floor(ratio * (ramp.length - 1) + 0.0001))
  // white text on the dark steps (5.4:1 or more), ink on the light ones (5.4:1 or more)
  const lightCell = dark ? index >= 5 : index <= 3
  return { background: ramp[index], color: lightCell ? "#0A0C10" : "#FFFFFF" }
}

export function MemberCategoryHeatmap({ stats, currency }: { stats: ReportStats; currency: string }) {
  const dark = useTheme().resolved === "dark"
  const ramp = dark ? RAMP_DARK : RAMP_LIGHT
  const categories = stats.byCategory
  const cells = new Map(stats.memberCategoryMatrix.map((c) => [`${c.userId}|${c.categoryKey}`, c.shareMinor]))
  const max = stats.memberCategoryMatrix.reduce((m, c) => Math.max(m, c.shareMinor), 0)
  return (
    <section className="rounded-3xl border border-hairline bg-surface p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-heading text-lg font-bold">Who used what</h3>
          <p className="mt-0.5 text-sm text-muted-foreground">Each person's share of every category</p>
        </div>
        <div className="flex items-center gap-2 text-xs text-muted-foreground" aria-hidden>
          less
          <span className="flex overflow-hidden rounded-md">
            {ramp.filter((_, i) => i % 2 === 0).map((c) => (
              <span key={c} className="h-3 w-4" style={{ backgroundColor: c }} />
            ))}
          </span>
          more
        </div>
      </div>
      <div className="mt-4 overflow-x-auto rounded-xl border border-hairline">
        <table className="w-full min-w-[640px] border-separate border-spacing-0.5 text-sm">
          <thead>
            <tr className="text-xs text-muted-foreground">
              <th className="sticky left-0 bg-surface px-3 py-2 text-left font-medium">Person</th>
              {categories.map((c) => (
                <th key={c.categoryId} className="px-1 py-2 text-center font-medium">
                  <span className="block max-w-[5.5rem] truncate" title={c.name}>
                    {c.name}
                  </span>
                </th>
              ))}
              <th className="px-3 py-2 text-right font-medium">Total</th>
            </tr>
          </thead>
          <tbody>
            {stats.byMember.map((m) => (
              <tr key={m.userId}>
                <th scope="row" className="sticky left-0 bg-surface px-3 py-2 text-left font-medium">
                  {firstName(m.name)}
                </th>
                {categories.map((c) => {
                  const value = cells.get(`${m.userId}|${c.key}`) ?? 0
                  const style = heat(max === 0 ? 0 : value / max, dark)
                  return (
                    <td key={c.categoryId} className="rounded-md px-1 py-2 text-center font-money text-xs" style={style} title={`${m.name} · ${c.name}: ${formatMoney(value, currency)}`}>
                      {value === 0 ? "–" : formatWhole(value, currency)}
                    </td>
                  )
                })}
                <td className="px-3 py-2 text-right font-money text-xs font-semibold">{formatWhole(m.shareMinor, currency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
