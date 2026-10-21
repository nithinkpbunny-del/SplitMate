import { motion } from "framer-motion"

import { LogoMark } from "@/components/Logo"

export function Splash() {
  return (
    <div className="flex min-h-dvh items-center justify-center" role="status" aria-label="Loading SplitMate">
      <motion.div
        animate={{ rotate: 360 }}
        transition={{ duration: 1.4, repeat: Infinity, ease: "linear" }}
        className="text-foreground"
      >
        <LogoMark className="size-10" />
      </motion.div>
    </div>
  )
}
