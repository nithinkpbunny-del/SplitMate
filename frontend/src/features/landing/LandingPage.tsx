import { motion, useReducedMotion } from "framer-motion"
import { ArrowRight, Calculator, FileBarChart2, Link2, Users } from "lucide-react"
import { useEffect, useState } from "react"
import { Link } from "react-router"

import { Logo } from "@/components/Logo"
import { SettlementGraph, type GraphMember, type GraphTransfer } from "@/components/SettlementGraph"
import { ThemeToggle } from "@/components/ThemeToggle"
import { Button } from "@/components/ui/button"

// A consistent example: nets sum to zero and the transfers are what the greedy settlement produces.
const demoMembers: GraphMember[] = [
  { id: "a", name: "Aarav Mehta", avatarColor: "#F59E0B", net: 41230 },
  { id: "i", name: "Isha Rao", avatarColor: "#F472B6", net: 3800 },
  { id: "r", name: "Rohan Nair", avatarColor: "#60A5FA", net: -23010 },
  { id: "s", name: "Sneha Iyer", avatarColor: "#34D399", net: -22020 },
]
const demoTransfers: GraphTransfer[] = [
  { fromUserId: "r", toUserId: "a", amountMinor: 23010 },
  { fromUserId: "s", toUserId: "a", amountMinor: 18220 },
  { fromUserId: "s", toUserId: "i", amountMinor: 3800 },
]

const features = [
  {
    icon: Link2,
    title: "One link, everyone's in",
    body: "Create a split for the month and send the invite link to the flat group chat. Copy, share, WhatsApp or QR.",
  },
  {
    icon: Users,
    title: "Split with everyone, or just a few",
    body: "Rent for all four, dinner for two, Prague for three. Pick who shares each expense; the cents always add up.",
  },
  {
    icon: Calculator,
    title: "Calculate, settle, done",
    body: "See what each person paid and consumed, then the fewest transfers that settle everyone. Record payments as they happen.",
  },
]

export function LandingPage() {
  const reduce = useReducedMotion()
  const [cycle, setCycle] = useState(0)
  useEffect(() => {
    if (reduce) return
    const id = window.setInterval(() => setCycle((c) => c + 1), 9000)
    return () => window.clearInterval(id)
  }, [reduce])

  return (
    <div className="relative min-h-dvh overflow-x-hidden">
      <div className="pointer-events-none absolute left-1/2 top-[-280px] h-[640px] w-[1100px] -translate-x-1/2 rounded-full bg-[radial-gradient(closest-side,var(--glow),transparent)]" />

      <header className="relative mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
        <Logo />
        <nav className="flex items-center gap-1 sm:gap-2">
          <ThemeToggle />
          <Button asChild variant="ghost" size="lg" className="px-3">
            <Link to="/login">Log in</Link>
          </Button>
          <Button asChild size="lg" className="hidden px-4 font-semibold sm:inline-flex">
            <Link to="/register">Get started</Link>
          </Button>
        </nav>
      </header>

      <section className="relative mx-auto grid max-w-6xl items-center gap-10 px-4 pb-16 pt-10 sm:px-6 lg:grid-cols-[1.1fr_1fr] lg:pt-16">
        <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}>
          <p className="inline-flex items-center gap-2 rounded-full border border-hairline bg-surface/60 px-3 py-1 font-mono text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
            <span className="size-1.5 rounded-full bg-lime-ink" /> For flatmates · shared homes · trips
          </p>
          <h1 className="mt-5 font-heading text-[clamp(2.5rem,7vw,4.5rem)] font-bold leading-[0.98] tracking-[-0.035em]">
            Split rent, groceries and everything in between.
            <span className="mt-2 block text-muted-foreground">No awkward maths.</span>
          </h1>
          <p className="mt-6 max-w-lg text-lg text-muted-foreground">
            Track what everyone pays, split it fairly to the cent, and see exactly who owes whom at the end of the month.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Button asChild size="xl" className="px-6">
              <Link to="/register">
                Start a split <ArrowRight />
              </Link>
            </Button>
            <Button asChild size="xl" variant="outline">
              <Link to="/login">I already have an account</Link>
            </Button>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, scale: 0.96 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.6, delay: 0.1 }}
          className="relative mx-auto w-full max-w-[480px] rounded-[28px] border border-hairline bg-surface/80 p-4 shadow-2xl backdrop-blur sm:p-6"
        >
          <div className="flex items-center justify-between px-1 font-mono text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
            <span>🏠 October month split</span>
            <span className="text-lime-ink">3 transfers</span>
          </div>
          <SettlementGraph members={demoMembers} transfers={demoTransfers} animateKey={cycle} className="mx-auto mt-2" />
        </motion.div>
      </section>

      <section className="relative mx-auto max-w-6xl px-4 pb-20 sm:px-6">
        <div className="grid gap-4 md:grid-cols-3">
          {features.map((f, i) => (
            <motion.article
              key={f.title}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-60px" }}
              transition={{ delay: i * 0.08, duration: 0.45 }}
              className="group rounded-3xl border border-hairline bg-surface p-6 transition-transform hover:-translate-y-1"
            >
              <span className="inline-flex size-11 items-center justify-center rounded-2xl bg-primary text-primary-foreground">
                <f.icon className="size-5" />
              </span>
              <h2 className="mt-5 font-heading text-xl font-bold">{f.title}</h2>
              <p className="mt-2 text-[15px] leading-relaxed text-muted-foreground">{f.body}</p>
            </motion.article>
          ))}
        </div>

        <div className="mt-4 flex flex-col items-start justify-between gap-6 rounded-3xl border border-hairline bg-[#0A0C10] p-8 text-[#ECEEF2] sm:flex-row sm:items-center sm:p-10">
          <div className="flex items-start gap-4">
            <span className="mt-1 inline-flex size-11 shrink-0 items-center justify-center rounded-2xl bg-[#D4FF3F] text-[#0A0C10]">
              <FileBarChart2 className="size-5" />
            </span>
            <div>
              <h2 className="font-heading text-2xl font-bold">And a spending report at the end of the month</h2>
              <p className="mt-1 text-[#8D96A7]">Where the money went, who paid most, the priciest day. In the app and as a PDF.</p>
            </div>
          </div>
          <Button asChild size="xl" className="shrink-0 bg-[#D4FF3F] text-[#0A0C10] hover:bg-[#D4FF3F]/85">
            <Link to="/register">
              Create a free account <ArrowRight />
            </Link>
          </Button>
        </div>
      </section>

      <footer className="relative border-t border-hairline">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-2 px-4 py-6 text-sm text-muted-foreground sm:flex-row sm:px-6">
          <Logo compact />
          <p>SplitMate · Built with Java, Spring Boot, MongoDB and React</p>
        </div>
      </footer>
    </div>
  )
}
