import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react"

import { ThemeContext, type ThemeChoice, type ThemeContextValue } from "@/hooks/useTheme"

type Resolved = "light" | "dark"

const STORAGE_KEY = "sm.theme"

function readStored(): ThemeChoice {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === "light" || value === "dark" ? value : "system"
  } catch {
    return "system"
  }
}

const media = () => window.matchMedia("(prefers-color-scheme: dark)")

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeChoice>(readStored)
  const [systemDark, setSystemDark] = useState(() => media().matches)

  useEffect(() => {
    const mq = media()
    const listener = (e: MediaQueryListEvent) => setSystemDark(e.matches)
    mq.addEventListener("change", listener)
    return () => mq.removeEventListener("change", listener)
  }, [])

  const resolved: Resolved = theme === "system" ? (systemDark ? "dark" : "light") : theme

  useEffect(() => {
    document.documentElement.classList.toggle("dark", resolved === "dark")
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", resolved === "dark" ? "#0A0C10" : "#FAFAF7")
  }, [resolved])

  const setTheme = useCallback((next: ThemeChoice) => {
    setThemeState(next)
    try {
      if (next === "system") localStorage.removeItem(STORAGE_KEY)
      else localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // storage unavailable (private mode); the choice still applies for this visit
    }
  }, [])

  const value = useMemo<ThemeContextValue>(
    () => ({ theme, resolved, setTheme, toggle: () => setTheme(resolved === "dark" ? "light" : "dark") }),
    [theme, resolved, setTheme],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
