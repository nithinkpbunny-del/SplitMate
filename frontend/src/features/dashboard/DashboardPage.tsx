import { useMe } from "@/features/auth/useAuth"
import { firstName } from "@/lib/format"

export function DashboardPage() {
  const { data: me } = useMe()
  return (
    <div>
      <p className="font-mono text-xs uppercase tracking-[0.2em] text-muted-foreground">Dashboard</p>
      <h1 className="mt-2 font-heading text-4xl font-bold">Hey {me ? firstName(me.name) : ""} 👋</h1>
      <p className="mt-2 text-muted-foreground">Your splits will show up here.</p>
    </div>
  )
}
