import { useEffect, useState } from "react"

export default function App() {
  const [status, setStatus] = useState("checking…")
  useEffect(() => {
    fetch("/api/health")
      .then((r) => r.json())
      .then((d: { status: string }) => setStatus(d.status))
      .catch(() => setStatus("unreachable"))
  }, [])
  return <main className="p-8 font-mono">SplitMate backend: {status}</main>
}
