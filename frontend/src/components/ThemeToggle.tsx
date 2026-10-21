import { AnimatePresence, motion } from "framer-motion"
import { Moon, Sun } from "lucide-react"

import { Button } from "@/components/ui/button"
import { useTheme } from "@/hooks/useTheme"

export function ThemeToggle() {
  const { resolved, toggle } = useTheme()
  const next = resolved === "dark" ? "light" : "dark"
  return (
    <Button variant="ghost" size="icon-lg" onClick={toggle} aria-label={`Switch to ${next} theme`} title={`Switch to ${next} theme`}>
      <AnimatePresence mode="wait" initial={false}>
        <motion.span
          key={resolved}
          initial={{ rotate: -90, opacity: 0, scale: 0.6 }}
          animate={{ rotate: 0, opacity: 1, scale: 1 }}
          exit={{ rotate: 90, opacity: 0, scale: 0.6 }}
          transition={{ duration: 0.2 }}
          className="inline-flex"
        >
          {resolved === "dark" ? <Moon /> : <Sun />}
        </motion.span>
      </AnimatePresence>
    </Button>
  )
}
