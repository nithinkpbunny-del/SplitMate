import { useEffect, useRef } from "react"

type Handlers = Record<string, (event: KeyboardEvent) => void>

function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  return target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName)
}

/**
 * Single-key shortcuts (e.g. "n", "c", "/", "?"). Ignored while typing in a field, while a dialog is
 * open, or when a modifier key is held, so they never fight with normal text input.
 */
export function useHotkeys(handlers: Handlers, enabled = true) {
  const ref = useRef(handlers)
  useEffect(() => {
    ref.current = handlers
  })

  useEffect(() => {
    if (!enabled) return
    const onKey = (event: KeyboardEvent) => {
      if (event.metaKey || event.ctrlKey || event.altKey || isTyping(event.target)) return
      if (document.querySelector('[role="dialog"][data-state="open"], [role="alertdialog"]')) return
      const handler = ref.current[event.key.toLowerCase()] ?? ref.current[event.key]
      if (handler) {
        event.preventDefault()
        handler(event)
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [enabled])
}
