import { motion, useReducedMotion } from "framer-motion"
import { useId, useMemo } from "react"

import { firstName, initials } from "@/lib/format"
import { formatMoney, formatSigned } from "@/lib/money"
import { cn } from "@/lib/utils"

export type GraphMember = { id: string; name: string; avatarColor: string; net?: number }
export type GraphTransfer = { fromUserId: string; toUserId: string; amountMinor: number }

type Point = { x: number; y: number }

const VIEW = 440
const CENTER = VIEW / 2

/**
 * Members sit on a circle; a curved arrow flows from each debtor to the creditor they pay, with the
 * amount on the arrow. Colour is never the only signal: amounts carry +/− and the list view repeats
 * everything in words.
 */
export function SettlementGraph({
  members,
  transfers,
  currency = "EUR",
  highlightUserId,
  className,
  animateKey,
}: {
  members: GraphMember[]
  transfers: GraphTransfer[]
  currency?: string
  highlightUserId?: string
  className?: string
  animateKey?: string | number
}) {
  const reduce = useReducedMotion()
  const uid = useId().replace(/:/g, "")
  const nodeR = members.length > 6 ? 20 : 26
  const ringR = members.length <= 2 ? 120 : members.length > 6 ? 158 : 145

  const positions = useMemo(() => {
    const map = new Map<string, Point>()
    const n = members.length
    members.forEach((m, i) => {
      // Start at the top (or left/right for two people) and go clockwise.
      const angle = n === 2 ? Math.PI * i : -Math.PI / 2 + (2 * Math.PI * i) / n
      map.set(m.id, { x: CENTER + ringR * Math.cos(angle), y: CENTER + ringR * Math.sin(angle) })
    })
    return map
  }, [members, ringR])

  const edges = placeLabels(
    transfers
      .map((t, i) => {
        const from = positions.get(t.fromUserId)
        const to = positions.get(t.toUserId)
        if (!from || !to) return null
        return { ...t, key: `${t.fromUserId}-${t.toUserId}-${i}`, ...curve(from, to, nodeR) }
      })
      .filter((e): e is NonNullable<typeof e> => e !== null),
    [...positions.values()],
    nodeR,
  )

  const nameById = new Map(members.map((m) => [m.id, m.name]))
  const description =
    transfers.length === 0
      ? "Everyone is settled up."
      : transfers
          .map((t) => `${nameById.get(t.fromUserId)} pays ${nameById.get(t.toUserId)} ${formatMoney(t.amountMinor, currency)}`)
          .join(". ")

  return (
    <svg
      viewBox={`0 0 ${VIEW} ${VIEW}`}
      className={cn("h-auto w-full max-w-[460px] select-none", className)}
      role="img"
      aria-label={`Who pays whom. ${description}`}
      key={animateKey}
    >
      <defs>
        <marker id={`arrow-${uid}`} viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--lime-ink)" />
        </marker>
        <radialGradient id={`glow-${uid}`}>
          <stop offset="0%" stopColor="var(--glow)" />
          <stop offset="100%" stopColor="transparent" />
        </radialGradient>
      </defs>

      <circle cx={CENTER} cy={CENTER} r={ringR + 40} fill={`url(#glow-${uid})`} />
      <circle cx={CENTER} cy={CENTER} r={ringR} fill="none" stroke="var(--hairline)" strokeDasharray="2 6" />

      {edges.map((e, i) => (
        <g key={e.key}>
          <motion.path
            d={e.d}
            fill="none"
            stroke="var(--lime-ink)"
            strokeWidth={2.25}
            strokeLinecap="round"
            markerEnd={`url(#arrow-${uid})`}
            initial={reduce ? false : { pathLength: 0, opacity: 0 }}
            animate={{ pathLength: 1, opacity: 1 }}
            transition={{ duration: 0.9, delay: 0.35 + i * 0.25, ease: [0.22, 1, 0.36, 1] }}
          />
          {!reduce && (
            <circle r="3.5" fill="var(--lime-ink)" opacity="0.9">
              <animateMotion dur={`${2.4 + i * 0.3}s`} repeatCount="indefinite" path={e.d} begin={`${1.2 + i * 0.25}s`} />
            </circle>
          )}
          <motion.g
            initial={reduce ? false : { opacity: 0, scale: 0.8 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: 0.9 + i * 0.25, duration: 0.3 }}
            style={{ transformOrigin: `${e.label.x}px ${e.label.y}px` }}
          >
            <rect
              x={e.label.x - 38}
              y={e.label.y - 13}
              width={76}
              height={26}
              rx={13}
              fill="var(--surface)"
              stroke="var(--lime-ink)"
              strokeOpacity={0.5}
            />
            <text
              x={e.label.x}
              y={e.label.y + 4.5}
              textAnchor="middle"
              className="font-money"
              fontSize={12.5}
              fontWeight={600}
              fill="var(--foreground)"
            >
              {formatMoney(e.amountMinor, currency)}
            </text>
          </motion.g>
        </g>
      ))}

      {members.map((m, i) => {
        const p = positions.get(m.id)!
        const net = m.net ?? 0
        const below = p.y >= CENTER - 1 || members.length === 2
        const isMe = m.id === highlightUserId
        return (
          <motion.g
            key={m.id}
            initial={reduce ? false : { opacity: 0, scale: 0.6 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: i * 0.06, type: "spring", stiffness: 300, damping: 22 }}
            style={{ transformOrigin: `${p.x}px ${p.y}px` }}
          >
            {isMe && <circle cx={p.x} cy={p.y} r={nodeR + 5} fill="none" stroke="var(--lime-ink)" strokeWidth={2} strokeDasharray="3 3" />}
            <circle cx={p.x} cy={p.y} r={nodeR} fill={m.avatarColor} stroke="var(--background)" strokeWidth={3} />
            <text x={p.x} y={p.y + 5} textAnchor="middle" fontSize={nodeR * 0.62} fontWeight={700} fill="#0A0C10" className="font-heading">
              {initials(m.name)}
            </text>
            <text
              x={p.x}
              y={below ? p.y + nodeR + 18 : p.y - nodeR - 24}
              textAnchor="middle"
              fontSize={13}
              fontWeight={600}
              fill="var(--foreground)"
            >
              {isMe ? "You" : firstName(m.name)}
            </text>
            {m.net !== undefined && (
              <text
                x={p.x}
                y={below ? p.y + nodeR + 34 : p.y - nodeR - 9}
                textAnchor="middle"
                fontSize={11.5}
                className="font-money"
                fill={net > 0 ? "var(--positive)" : net < 0 ? "var(--negative)" : "var(--muted-foreground)"}
              >
                {net === 0 ? "settled" : formatSigned(net, currency)}
              </text>
            )}
          </motion.g>
        )
      })}
    </svg>
  )
}

/** Quadratic curve between two nodes, bent away from the centre, trimmed to the node edges. */
function curve(from: Point, to: Point, nodeR: number) {
  const mid = { x: (from.x + to.x) / 2, y: (from.y + to.y) / 2 }
  const dx = to.x - from.x
  const dy = to.y - from.y
  const len = Math.hypot(dx, dy) || 1
  // perpendicular, oriented toward the centre so arrows bow inward and stay inside the ring
  let nx = -dy / len
  let ny = dx / len
  const toCenter = { x: CENTER - mid.x, y: CENTER - mid.y }
  if (nx * toCenter.x + ny * toCenter.y < 0) {
    nx = -nx
    ny = -ny
  }
  const bend = Math.min(70, len * 0.28)
  const c = { x: mid.x + nx * bend, y: mid.y + ny * bend }
  const start = towards(from, c, nodeR + 4)
  const end = towards(to, c, nodeR + 9)
  const at = (t: number): Point => ({
    x: (1 - t) * (1 - t) * start.x + 2 * (1 - t) * t * c.x + t * t * end.x,
    y: (1 - t) * (1 - t) * start.y + 2 * (1 - t) * t * c.y + t * t * end.y,
  })
  return { d: `M ${start.x} ${start.y} Q ${c.x} ${c.y} ${end.x} ${end.y}`, at }
}

const LABEL_W = 80
const LABEL_H = 30

/**
 * Puts each amount label on its curve where it overlaps the fewest other labels, member nodes and
 * other arrows. Candidates are tried from the middle of the curve outwards.
 */
function placeLabels<E extends { at: (t: number) => Point }>(edges: E[], nodes: Point[], nodeR: number) {
  const placed: Point[] = []
  const candidates = [0.5, 0.4, 0.6, 0.32, 0.68, 0.25, 0.75]
  const samples = Array.from({ length: 21 }, (_, i) => i / 20)
  const inBox = (p: Point, q: Point, halfW: number, halfH: number) => Math.abs(p.x - q.x) < halfW && Math.abs(p.y - q.y) < halfH
  return edges.map((edge, index) => {
    const otherCurvePoints = edges.filter((_, j) => j !== index).flatMap((other) => samples.map(other.at))
    const score = (p: Point) =>
      placed.filter((q) => inBox(p, q, LABEL_W, LABEL_H)).length * 100 +
      nodes.filter((n) => inBox(p, n, LABEL_W / 2 + nodeR, LABEL_H / 2 + nodeR)).length * 100 +
      otherCurvePoints.filter((c) => inBox(p, c, LABEL_W / 2 + 2, LABEL_H / 2 + 2)).length
    let best = edge.at(candidates[0])
    let bestScore = score(best)
    for (const t of candidates.slice(1)) {
      if (bestScore === 0) break
      const p = edge.at(t)
      const sc = score(p)
      if (sc < bestScore) {
        best = p
        bestScore = sc
      }
    }
    placed.push(best)
    return { ...edge, label: best }
  })
}

function towards(p: Point, target: Point, distance: number): Point {
  const dx = target.x - p.x
  const dy = target.y - p.y
  const len = Math.hypot(dx, dy) || 1
  return { x: p.x + (dx / len) * distance, y: p.y + (dy / len) * distance }
}
