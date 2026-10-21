import { createContext, useContext } from "react"

export type ThemeChoice = "light" | "dark" | "system"

export type ThemeContextValue = {
  theme: ThemeChoice
  resolved: "light" | "dark"
  setTheme: (theme: ThemeChoice) => void
  toggle: () => void
}

export const ThemeContext = createContext<ThemeContextValue | null>(null)

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error("useTheme must be used inside ThemeProvider")
  return ctx
}
