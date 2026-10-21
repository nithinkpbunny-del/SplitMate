import { AnimatePresence, motion, useReducedMotion } from "framer-motion"
import { ArrowRight, FileBarChart2, HandCoins, X } from "lucide-react"
import { Dialog as DialogPrimitive } from "radix-ui"
import { useEffect, useState } from "react"

import type { Member, Settlement } from "@/api/types"
import { MemberAvatar } from "@/components/MemberAvatar"
import { MoneyText } from "@/components/MoneyText"
import { SettlementGraph } from "@/components/SettlementGraph"
import { Button } from "@/components/ui/button"
import { firstName, plural } from "@/lib/format"
import { currencySymbol } from "@/lib/money"

const CRUNCH_MS = 1200

/**
 * Full-screen moment after pressing "Calculate split": ~1.2s of shimmering digits while the server
 * works, then the results revealed in sequence (total → people → transfers → next steps).
 */
export function CalculationReveal({
  open,
  onOpenChange,
  settlement,
  pending,
  members,
  me,
  currency,
  onRecordPayments,
  onGenerateReport,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  currency: string
  settlement: Settlement | null
  pending: boolean
  members: Member[]
  me: string
  onRecordPayments: () => void
  onGenerateReport?: () => void
}) {
  const reduce = useReducedMotion()
  const [minTimeDone, setMinTimeDone] = useState(false)

  useEffect(() => {
    if (!open) return
    const id = window.setTimeout(() => setMinTimeDone(true), reduce ? 0 : CRUNCH_MS)
    return () => {
      window.clearTimeout(id)
      setMinTimeDone(false)
    }
  }, [open, reduce])

  const revealed = minTimeDone && !pending && settlement !== null

  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-background/80 backdrop-blur-xl data-[state=open]:animate-in data-[state=open]:fade-in-0" />
        <DialogPrimitive.Content
          className="fixed inset-0 z-50 overflow-y-auto outline-none"
          aria-describedby={undefined}
          onOpenAutoFocus={(e) => e.preventDefault()}
        >
          <DialogPrimitive.Title className="sr-only">Calculation results</DialogPrimitive.Title>
          <div className="mx-auto flex min-h-full max-w-3xl flex-col px-4 py-6 sm:py-10">
            <div className="flex justify-end">
              <DialogPrimitive.Close asChild>
                <Button variant="ghost" size="icon-lg" aria-label="Close">
                  <X />
                </Button>
              </DialogPrimitive.Close>
            </div>
            <AnimatePresence mode="wait">
              {!revealed ? <Crunching key="crunch" symbol={currencySymbol(currency)} /> : <Results key="results" settlement={settlement!} members={members} me={me} onRecordPayments={onRecordPayments} onGenerateReport={onGenerateReport} onDone={() => onOpenChange(false)} />}
            </AnimatePresence>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}

function Crunching({ symbol }: { symbol: string }) {
  const [digits, setDigits] = useState("1 284.50")
  useEffect(() => {
    const id = window.setInterval(() => {
      const n = Math.floor(Math.random() * 900000) + 100000
      setDigits(`${String(n).slice(0, -2).replace(/\B(?=(\d{3})+(?!\d))/g, ",")}.${String(n).slice(-2)}`)
    }, 70)
    return () => window.clearInterval(id)
  }, [])
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, scale: 0.96, filter: "blur(6px)" }}
      transition={{ duration: 0.25 }}
      className="flex flex-1 flex-col items-center justify-center py-24 text-center"
      role="status"
      aria-live="polite"
    >
      <div className="glow-radial pointer-events-none absolute inset-0" />
      <p className="relative font-money text-[clamp(3rem,12vw,6rem)] font-bold tracking-tighter shimmer-text" aria-hidden>
        {symbol}
        {digits}
      </p>
      <p className="relative mt-4 font-mono text-xs uppercase tracking-[0.3em] text-muted-foreground">Crunching numbers…</p>
    </motion.div>
  )
}

const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.08, delayChildren: 0.35 } } }
const item = { hidden: { opacity: 0, y: 14 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.22, 1, 0.36, 1] as const } } }

function Results({
  settlement,
  members,
  me,
  onRecordPayments,
  onGenerateReport,
  onDone,
}: {
  settlement: Settlement
  members: Member[]
  me: string
  onRecordPayments: () => void
  onGenerateReport?: () => void
  onDone: () => void
}) {
  const colorOf = new Map(members.map((m) => [m.userId, m.avatarColor]))
  const people = [...settlement.memberSummaries].sort((a, b) => b.netMinor - a.netMinor)
  return (
    <motion.div initial="hidden" animate="show" variants={stagger} className="relative">
      <motion.div variants={item} className="text-center">
        <p className="font-mono text-xs uppercase tracking-[0.25em] text-muted-foreground">Calculation v{settlement.version} · {plural(settlement.expenseCount, "expense")}</p>
        <div className="relative mt-3 inline-block">
          <div className="glow-radial pointer-events-none absolute -inset-16" />
          <MoneyText minor={settlement.totalSpendMinor} currency={settlement.currency} className="relative text-[clamp(2.75rem,10vw,5rem)] font-bold tracking-tighter" />
        </div>
        <p className="mt-1 text-muted-foreground">spent together</p>
      </motion.div>

      <motion.div variants={item} className="mt-8 overflow-hidden rounded-3xl border border-hairline bg-surface">
        <div className="hidden grid-cols-[1fr_repeat(3,minmax(0,7.5rem))] gap-2 border-b border-hairline px-5 py-2.5 text-xs text-muted-foreground sm:grid">
          <span>Person</span>
          <span className="text-right">Paid</span>
          <span className="text-right">Their share</span>
          <span className="text-right">Net</span>
        </div>
        <motion.ul variants={stagger}>
          {people.map((p) => (
            <motion.li
              key={p.userId}
              variants={item}
              className="grid grid-cols-2 items-center gap-x-2 gap-y-1 border-b border-dashed border-hairline px-5 py-3.5 last:border-0 sm:grid-cols-[1fr_repeat(3,minmax(0,7.5rem))]"
            >
              <span className="col-span-2 flex items-center gap-2.5 font-medium sm:col-span-1">
                <MemberAvatar person={{ name: p.name, avatarColor: colorOf.get(p.userId) ?? p.avatarColor }} size="sm" />
                {p.userId === me ? "You" : p.name}
              </span>
              <span className="text-sm sm:text-right">
                <span className="text-xs text-muted-foreground sm:hidden">Paid </span>
                <MoneyText minor={p.paidMinor} currency={settlement.currency} />
              </span>
              <span className="text-right text-sm">
                <span className="text-xs text-muted-foreground sm:hidden">Share </span>
                <MoneyText minor={p.shareMinor} currency={settlement.currency} />
              </span>
              <span className="col-span-2 text-right sm:col-span-1">
                <MoneyText minor={p.netMinor} currency={settlement.currency} signed className="font-semibold" />
              </span>
            </motion.li>
          ))}
        </motion.ul>
      </motion.div>

      <motion.div variants={item} className="mt-8 grid items-center gap-6 md:grid-cols-[1fr_1fr]">
        <SettlementGraph
          members={people.map((p) => ({ id: p.userId, name: p.name, avatarColor: colorOf.get(p.userId) ?? p.avatarColor, net: p.netMinor }))}
          transfers={settlement.transfers}
          currency={settlement.currency}
          highlightUserId={me}
          className="mx-auto max-w-[340px]"
        />
        <div>
          <h2 className="font-heading text-2xl font-bold">
            {settlement.transfers.length === 0 ? "Everyone is square" : `${plural(settlement.transfers.length, "transfer")} settle everything`}
          </h2>
          <ul className="mt-4 space-y-2">
            {settlement.transfers.map((t) => (
              <li key={`${t.fromUserId}-${t.toUserId}`} className="flex items-center gap-2 rounded-2xl bg-surface-2/60 px-3.5 py-2.5 text-sm">
                <span className="flex-1">
                  <strong>{t.fromUserId === me ? "You" : firstName(t.fromName)}</strong> {t.fromUserId === me ? "pay" : "pays"}{" "}
                  <strong>{t.toUserId === me ? "you" : firstName(t.toName)}</strong>
                </span>
                <MoneyText minor={t.amountMinor} currency={settlement.currency} className="font-semibold" />
              </li>
            ))}
          </ul>
        </div>
      </motion.div>

      <motion.div variants={item} className="mt-10 flex flex-col gap-2 sm:flex-row sm:justify-center">
        {onGenerateReport && (
          <Button size="xl" onClick={onGenerateReport}>
            <FileBarChart2 /> Generate spending report
          </Button>
        )}
        {settlement.transfers.length > 0 ? (
          <Button size="xl" variant={onGenerateReport ? "outline" : "default"} onClick={onRecordPayments}>
            <HandCoins /> Record payments
          </Button>
        ) : (
          <Button size="xl" variant="outline" onClick={onDone}>
            Done <ArrowRight />
          </Button>
        )}
      </motion.div>
    </motion.div>
  )
}
