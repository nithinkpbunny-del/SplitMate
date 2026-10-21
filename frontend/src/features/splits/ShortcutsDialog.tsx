import { ResponsiveSheet } from "@/components/ResponsiveSheet"

const isMac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform)
const mod = isMac ? "⌘" : "Ctrl"

const groups: { title: string; items: { keys: string[]; label: string }[] }[] = [
  {
    title: "On a split",
    items: [
      { keys: ["N"], label: "Add an expense" },
      { keys: ["C"], label: "Calculate the split" },
      { keys: ["/"], label: "Search expenses" },
      { keys: ["?"], label: "Show this help" },
    ],
  },
  {
    title: "In the expense form",
    items: [
      { keys: ["Enter"], label: "Save, while typing in a text field" },
      { keys: [mod, "Enter"], label: "Save from anywhere in the form" },
      { keys: ["Esc"], label: "Close without saving" },
    ],
  },
]

/** Keyboard shortcuts help, opened with "?" or from the split menu. */
export function ShortcutsDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return (
    <ResponsiveSheet open={open} onOpenChange={onOpenChange} title="Keyboard shortcuts" description="Single keys work when you are not typing in a field.">
      <div className="space-y-6">
        {groups.map((g) => (
          <section key={g.title} aria-label={g.title}>
            <h3 className="mb-2 text-xs font-medium uppercase tracking-wider text-muted-foreground">{g.title}</h3>
            <ul className="divide-y divide-dashed divide-hairline rounded-2xl border border-hairline">
              {g.items.map((item) => (
                <li key={item.label} className="flex items-center justify-between gap-4 px-4 py-3 text-sm">
                  <span>{item.label}</span>
                  <span className="flex shrink-0 gap-1">
                    {item.keys.map((k) => (
                      <kbd key={k} className="min-w-7 rounded-md border border-hairline bg-surface-2 px-2 py-1 text-center font-mono text-xs font-medium">
                        {k}
                      </kbd>
                    ))}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </ResponsiveSheet>
  )
}
