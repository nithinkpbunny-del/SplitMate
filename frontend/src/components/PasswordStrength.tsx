import { motion } from "framer-motion"

import { cn } from "@/lib/utils"

const levels = [
  { label: "Too short", color: "var(--negative)" },
  { label: "Weak", color: "var(--negative)" },
  { label: "Okay", color: "#F59E0B" },
  { label: "Good", color: "var(--lime-ink)" },
  { label: "Strong", color: "var(--positive)" },
]

export function passwordScore(password: string): number {
  if (password.length < 8) return 0
  let score = 1
  if (/\p{L}/u.test(password) && /\d/.test(password)) score++
  if (password.length >= 12) score++
  if (/[^\p{L}\d]/u.test(password) || (/[a-z]/.test(password) && /[A-Z]/.test(password))) score++
  return Math.min(score, 4)
}

export function PasswordStrength({ password }: { password: string }) {
  const score = passwordScore(password)
  const level = levels[score]
  return (
    <div className="flex items-center gap-3" aria-live="polite">
      <div className="flex flex-1 gap-1">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="h-1 flex-1 overflow-hidden rounded-full bg-muted">
            <motion.div
              className="h-full rounded-full"
              initial={false}
              animate={{ width: password && score >= i ? "100%" : "0%", backgroundColor: level.color }}
              transition={{ duration: 0.25 }}
            />
          </div>
        ))}
      </div>
      <span className={cn("w-16 text-right text-xs font-medium", !password && "invisible")} style={{ color: level.color }}>
        {level.label}
      </span>
    </div>
  )
}
