import confetti from "canvas-confetti"

/** A celebratory burst in the brand colours. Skipped for people who prefer reduced motion. */
export function celebrate() {
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return
  const colors = ["#D4FF3F", "#34D399", "#F472B6", "#60A5FA", "#FBBF24"]
  const burst = (x: number, angle: number) =>
    confetti({ particleCount: 90, spread: 70, startVelocity: 55, angle, origin: { x, y: 0.7 }, colors, disableForReducedMotion: true })
  burst(0.2, 60)
  burst(0.8, 120)
  window.setTimeout(() => confetti({ particleCount: 120, spread: 120, origin: { y: 0.4 }, colors, scalar: 1.1, disableForReducedMotion: true }), 250)
}
