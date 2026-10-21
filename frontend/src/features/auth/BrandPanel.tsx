import { AnimatePresence, motion, useReducedMotion } from "framer-motion"
import { ArrowRight, Check } from "lucide-react"
import { useEffect, useState } from "react"

import { MemberAvatar } from "@/components/MemberAvatar"
import { formatMoney } from "@/lib/money"

const people = {
  aarav: { name: "Aarav Mehta", avatarColor: "#F59E0B" },
  isha: { name: "Isha Rao", avatarColor: "#F472B6" },
  rohan: { name: "Rohan Nair", avatarColor: "#60A5FA" },
}

const receipt = [
  { what: "Rent, October", who: people.aarav, amount: 160000, ways: 3 },
  { what: "Lidl weekly groceries", who: people.isha, amount: 8640, ways: 3 },
  { what: "Deutschlandticket", who: people.rohan, amount: 11600, ways: 2 },
  { what: "Dinner at Monsieur Vuong", who: people.isha, amount: 6450, ways: 2 },
]

/** Animated "live ledger" shown next to the login/register forms. */
export function BrandPanel() {
  const reduce = useReducedMotion()
  const [step, setStep] = useState(reduce ? receipt.length + 1 : 0)

  useEffect(() => {
    if (reduce) return
    const id = window.setInterval(() => setStep((s) => (s >= receipt.length + 3 ? 0 : s + 1)), 1100)
    return () => window.clearInterval(id)
  }, [reduce])

  const visible = Math.min(step, receipt.length)
  const showTransfer = step > receipt.length

  return (
    <div className="relative flex h-full flex-col justify-between overflow-hidden rounded-3xl border border-hairline bg-[#0A0C10] p-10 text-[#ECEEF2]">
      <div className="pointer-events-none absolute -right-24 -top-24 size-[420px] rounded-full bg-[radial-gradient(closest-side,rgba(212,255,63,0.18),transparent)]" />
      <div className="pointer-events-none absolute -bottom-32 -left-20 size-[360px] rounded-full bg-[radial-gradient(closest-side,rgba(52,211,153,0.12),transparent)]" />

      <div className="relative">
        <p className="font-mono text-xs uppercase tracking-[0.2em] text-[#D4FF3F]">Flat 4B · Berlin</p>
        <h2 className="mt-4 max-w-md font-heading text-4xl font-bold leading-[1.05] tracking-tight xl:text-5xl">
          Split rent, groceries and everything in between.
        </h2>
        <p className="mt-4 max-w-sm text-[15px] text-[#8D96A7]">No awkward maths. Add what you paid, and SplitMate works out who pays whom, to the cent.</p>
      </div>

      <div className="relative mt-10 space-y-3">
        <div className="rounded-2xl border border-white/[0.07] bg-[#11141A]/90 p-4 shadow-2xl backdrop-blur">
          <div className="mb-2 flex items-center justify-between font-mono text-[11px] uppercase tracking-widest text-[#8D96A7]">
            <span>October month split</span>
            <span>EUR</span>
          </div>
          <ul className="min-h-[188px]">
            <AnimatePresence initial={false}>
              {receipt.slice(0, visible).map((line) => (
                <motion.li
                  key={line.what}
                  initial={{ opacity: 0, y: 12, filter: "blur(4px)" }}
                  animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.35 }}
                  className="flex items-center gap-3 border-b border-dashed border-white/[0.08] py-2.5 last:border-0"
                >
                  <MemberAvatar person={line.who} size="sm" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{line.what}</p>
                    <p className="text-xs text-[#8D96A7]">split {line.ways} ways</p>
                  </div>
                  <span className="font-money text-sm">{formatMoney(line.amount)}</span>
                </motion.li>
              ))}
            </AnimatePresence>
          </ul>
        </div>

        <AnimatePresence>
          {showTransfer && (
            <motion.div
              initial={{ opacity: 0, y: 16, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8 }}
              transition={{ type: "spring", stiffness: 260, damping: 24 }}
              className="flex items-center gap-3 rounded-2xl border border-[#D4FF3F]/25 bg-[#D4FF3F]/[0.07] p-4"
            >
              <MemberAvatar person={people.rohan} size="sm" />
              <ArrowRight className="size-4 text-[#D4FF3F]" aria-hidden />
              <MemberAvatar person={people.aarav} size="sm" />
              <p className="flex-1 text-sm">
                Rohan pays Aarav <span className="font-money font-semibold text-[#D4FF3F]">{formatMoney(48867)}</span>
              </p>
              <span className="inline-flex size-6 items-center justify-center rounded-full bg-[#D4FF3F] text-[#0A0C10]">
                <Check className="size-3.5" strokeWidth={3} />
              </span>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}
