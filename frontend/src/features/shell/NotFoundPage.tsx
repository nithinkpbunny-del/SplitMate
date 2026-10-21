import { ArrowLeft } from "lucide-react"
import { Link } from "react-router"

import { Logo } from "@/components/Logo"
import { Button } from "@/components/ui/button"

export function NotFoundPage({ title = "This page split off", message = "We couldn't find what you were looking for. It may have been moved, or you may not have access to it." }: { title?: string; message?: string }) {
  return (
    <div className="relative flex min-h-dvh flex-col items-center justify-center overflow-hidden px-4 text-center">
      <div className="glow-radial pointer-events-none absolute inset-0" />
      <Link to="/" className="absolute left-4 top-5 sm:left-8">
        <Logo />
      </Link>
      <p className="relative font-money text-[min(32vw,180px)] font-bold leading-none tracking-tighter text-foreground/90">
        4<span className="text-lime-ink">0</span>4
      </p>
      <div className="receipt-divider relative my-6 w-64" />
      <h1 className="relative font-heading text-2xl font-bold sm:text-3xl">{title}</h1>
      <p className="relative mt-2 max-w-md text-muted-foreground">{message}</p>
      <Button asChild size="xl" className="relative mt-8">
        <Link to="/app">
          <ArrowLeft /> Back to my splits
        </Link>
      </Button>
    </div>
  )
}
