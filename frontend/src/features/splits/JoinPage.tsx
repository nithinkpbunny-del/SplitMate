import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { motion } from "framer-motion"
import { ArrowRight, Link2Off, Loader2, Users } from "lucide-react"
import { Link, useNavigate, useParams } from "react-router"
import { toast } from "sonner"

import { ApiError } from "@/api/client"
import { invitesApi, splitKeys } from "@/api/splits"
import { Logo } from "@/components/Logo"
import { MemberAvatar } from "@/components/MemberAvatar"
import { ThemeToggle } from "@/components/ThemeToggle"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useMe } from "@/features/auth/useAuth"
import { firstName, plural } from "@/lib/format"

/** /join/:code: public invite preview, then login/register (returning here), then one click to join. */
export function JoinPage() {
  const { code = "" } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: me, isPending: meLoading } = useMe()
  const preview = useQuery({ queryKey: ["invite", code], queryFn: () => invitesApi.preview(code), retry: false })

  const join = useMutation({
    mutationFn: () => invitesApi.join(code),
    onSuccess: (split) => {
      queryClient.setQueryData(splitKeys.detail(split.id), split)
      queryClient.invalidateQueries({ queryKey: splitKeys.all })
      toast.success(`You're in ${split.name}`)
      navigate(`/app/splits/${split.id}`, { replace: true })
    },
  })

  const next = encodeURIComponent(`/join/${code}`)
  const invalid = preview.isError && preview.error instanceof ApiError && preview.error.status === 404

  return (
    <div className="relative flex min-h-dvh flex-col overflow-hidden">
      <div className="glow-radial pointer-events-none absolute inset-x-0 top-0 h-[520px]" />
      <header className="relative mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-4 sm:px-6">
        <Link to={me ? "/app" : "/"}>
          <Logo />
        </Link>
        <ThemeToggle />
      </header>

      <main className="relative flex flex-1 items-center justify-center px-4 pb-16">
        {preview.isPending || meLoading ? (
          <div className="w-full max-w-md space-y-4 rounded-[28px] border border-hairline bg-surface p-8">
            <Skeleton className="mx-auto size-20 rounded-3xl" />
            <Skeleton className="mx-auto h-6 w-3/4" />
            <Skeleton className="mx-auto h-4 w-1/2" />
            <Skeleton className="h-12 w-full rounded-xl" />
          </div>
        ) : invalid || preview.isError ? (
          <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-md rounded-[28px] border border-hairline bg-surface p-8 text-center">
            <span className="mx-auto flex size-16 items-center justify-center rounded-2xl bg-surface-2 text-muted-foreground">
              <Link2Off className="size-7" />
            </span>
            <h1 className="mt-5 font-heading text-2xl font-bold">This invite link doesn't work</h1>
            <p className="mt-2 text-muted-foreground">
              {invalid
                ? "It may have been turned off or replaced with a new one. Ask the person who invited you to send the current link."
                : "We couldn't load this invite right now. Check your connection and try again."}
            </p>
            <Button asChild size="xl" variant="outline" className="mt-6">
              <Link to={me ? "/app" : "/"}>{me ? "Go to my splits" : "Go to SplitMate"}</Link>
            </Button>
          </motion.div>
        ) : (
          <motion.div
            initial={{ opacity: 0, y: 16, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
            className="w-full max-w-md rounded-[28px] border border-hairline bg-surface p-8 text-center shadow-2xl"
          >
            <motion.span
              initial={{ rotate: -8, scale: 0.8 }}
              animate={{ rotate: 0, scale: 1 }}
              transition={{ type: "spring", stiffness: 200, damping: 14, delay: 0.1 }}
              className="mx-auto flex size-20 items-center justify-center rounded-3xl bg-surface-2 text-4xl"
              aria-hidden
            >
              {preview.data.emoji ?? "🏠"}
            </motion.span>
            <p className="mt-6 text-muted-foreground">{firstName(preview.data.ownerName)} invited you to</p>
            <h1 className="mt-1 font-heading text-3xl font-bold leading-tight">{preview.data.splitName}</h1>
            {preview.data.description && <p className="mt-1 text-sm text-muted-foreground">{preview.data.description}</p>}

            <div className="mt-6 flex flex-col items-center gap-2">
              <div className="flex -space-x-2">
                {preview.data.members.slice(0, 6).map((m, i) => (
                  <motion.span key={i} initial={{ opacity: 0, x: -6 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.2 + i * 0.05 }}>
                    <MemberAvatar person={{ name: m.firstName, avatarColor: m.avatarColor }} size="md" ring />
                  </motion.span>
                ))}
              </div>
              <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
                <Users className="size-3.5" />
                {plural(preview.data.memberCount, "member")}: {preview.data.members.map((m) => m.firstName).join(", ")}
              </p>
            </div>

            <div className="receipt-divider my-7" />

            {!preview.data.joinable ? (
              <p className="rounded-xl bg-surface-2 p-4 text-sm text-muted-foreground">
                This split is {preview.data.status.toLowerCase()} and isn't taking new members.
              </p>
            ) : me ? (
              <>
                <Button size="xl" className="w-full" onClick={() => join.mutate()} disabled={join.isPending}>
                  {join.isPending ? <Loader2 className="animate-spin" /> : <>Join split <ArrowRight /></>}
                </Button>
                <p className="mt-3 text-xs text-muted-foreground">Joining as {me.name}</p>
              </>
            ) : (
              <div className="space-y-2">
                <Button asChild size="xl" className="w-full">
                  <Link to={`/register?next=${next}`}>
                    Create an account to join <ArrowRight />
                  </Link>
                </Button>
                <Button asChild size="xl" variant="outline" className="w-full">
                  <Link to={`/login?next=${next}`}>I already have an account</Link>
                </Button>
              </div>
            )}
          </motion.div>
        )}
      </main>
    </div>
  )
}
