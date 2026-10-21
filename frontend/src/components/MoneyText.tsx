import { animate, useReducedMotion } from "framer-motion"
import { useEffect, useRef, useState } from "react"

import { formatMoney, formatSigned } from "@/lib/money"
import { cn } from "@/lib/utils"

/**
 * A money amount in tabular mono digits. With `signed`, it is coloured by sign (emerald = gets back,
 * coral = owes) and always carries a +/− so colour is never the only signal. Changes count up smoothly.
 */
export function MoneyText({
  minor,
  currency = "EUR",
  signed = false,
  colored = signed,
  animateValue = true,
  className,
}: {
  minor: number
  currency?: string
  signed?: boolean
  colored?: boolean
  animateValue?: boolean
  className?: string
}) {
  const reduce = useReducedMotion()
  const animated = animateValue && !reduce
  const [shown, setShown] = useState(animated ? 0 : minor)
  const previous = useRef(animated ? 0 : minor)

  useEffect(() => {
    if (!animated) {
      previous.current = minor
      return
    }
    const controls = animate(previous.current, minor, {
      duration: Math.min(1.1, 0.35 + Math.abs(minor - previous.current) / 400000),
      ease: [0.22, 1, 0.36, 1],
      onUpdate: (v) => setShown(Math.round(v)),
    })
    previous.current = minor
    return () => controls.stop()
  }, [minor, animated])

  const value = animated ? shown : minor
  const text = signed ? formatSigned(value, currency) : formatMoney(value, currency)
  return (
    <span
      className={cn(
        "font-money whitespace-nowrap",
        colored && (minor > 0 ? "text-positive" : minor < 0 ? "text-negative" : "text-muted-foreground"),
        className,
      )}
      aria-label={signed ? formatSigned(minor, currency) : formatMoney(minor, currency)}
    >
      {text}
    </span>
  )
}
