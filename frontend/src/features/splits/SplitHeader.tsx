import { useMutation, useQueryClient } from "@tanstack/react-query"
import {
  Archive,
  ArchiveRestore,
  Keyboard,
  Link2,
  Link2Off,
  LogOut,
  MoreHorizontal,
  Pencil,
  RefreshCw,
  Trash2,
  UserPlus,
  Users,
} from "lucide-react"
import { useState } from "react"
import { useNavigate } from "react-router"
import { toast } from "sonner"

import { splitKeys, splitsApi } from "@/api/splits"
import type { Split } from "@/api/types"
import { ConfirmDialog } from "@/components/ConfirmDialog"
import { AvatarStack } from "@/components/MemberAvatar"
import { MoneyText } from "@/components/MoneyText"
import { ResponsiveSheet } from "@/components/ResponsiveSheet"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useMe } from "@/features/auth/useAuth"
import { formatPeriod, plural } from "@/lib/format"
import { balanceWords } from "@/lib/money"

import { EditSplitDialog } from "./EditSplitDialog"
import { MembersSheet } from "./MembersSheet"
import { ShareInvite } from "./ShareInvite"

type Confirm = "regenerate" | "delete" | "leave" | null

export function SplitHeader({ split, onShortcuts }: { split: Split; onShortcuts: () => void }) {
  const { data: me } = useMe()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [inviteOpen, setInviteOpen] = useState(false)
  const [membersOpen, setMembersOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [confirm, setConfirm] = useState<Confirm>(null)
  const isOwner = split.myRole === "OWNER"
  const active = split.members.filter((m) => m.active)
  const period = formatPeriod(split.period?.start, split.period?.end)

  const refresh = (updated?: Split) => {
    if (updated) queryClient.setQueryData(splitKeys.detail(split.id), updated)
    queryClient.invalidateQueries({ queryKey: splitKeys.all })
    queryClient.invalidateQueries({ queryKey: splitKeys.activity(split.id) })
  }

  const regenerate = useMutation({
    mutationFn: () => splitsApi.regenerateInvite(split.id),
    onSuccess: (s) => {
      refresh(s)
      setConfirm(null)
      toast.success("New invite link created. The old one no longer works.")
      setInviteOpen(true)
    },
  })
  const toggleInvite = useMutation({
    mutationFn: () => splitsApi.setInviteEnabled(split.id, !split.inviteEnabled),
    onSuccess: (s) => {
      refresh(s)
      toast.success(s.inviteEnabled ? "Invite link turned on" : "Invite link turned off")
    },
  })
  const setStatus = useMutation({
    mutationFn: (status: "ARCHIVED" | "OPEN") => splitsApi.update(split.id, { status }),
    onSuccess: (s) => {
      refresh(s)
      toast.success(s.status === "ARCHIVED" ? "Split archived" : "Split reopened")
    },
  })
  const remove = useMutation({
    mutationFn: () => splitsApi.remove(split.id),
    onSuccess: (result) => {
      setConfirm(null)
      if (result.deleted) {
        queryClient.removeQueries({ queryKey: splitKeys.detail(split.id) })
        queryClient.invalidateQueries({ queryKey: splitKeys.all })
        toast.success("Split deleted")
        navigate("/app", { replace: true })
      } else {
        refresh()
        queryClient.invalidateQueries({ queryKey: splitKeys.detail(split.id) })
        toast.info(result.message)
      }
    },
  })
  const leave = useMutation({
    mutationFn: () => splitsApi.removeMember(split.id, me!.id),
    onSuccess: () => {
      setConfirm(null)
      queryClient.removeQueries({ queryKey: splitKeys.detail(split.id) })
      queryClient.invalidateQueries({ queryKey: splitKeys.all })
      toast.success(`You left ${split.name}`)
      navigate("/app", { replace: true })
    },
    onError: () => setConfirm(null),
  })

  return (
    <header className="relative overflow-hidden rounded-[28px] border border-hairline bg-surface">
      <div className="glow-radial pointer-events-none absolute -right-24 -top-32 size-96 opacity-80" />
      <div className="relative p-5 sm:p-7">
        <div className="flex items-start gap-4">
          <span className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-surface-2 text-3xl sm:size-16 sm:text-4xl" aria-hidden>
            {split.emoji ?? "🏠"}
          </span>
          <div className="min-w-0 flex-1">
            <h1 className="font-heading text-[clamp(1.5rem,4vw,2.25rem)] font-bold leading-tight">{split.name}</h1>
            <p className="mt-0.5 text-sm text-muted-foreground">
              {[period, split.description].filter(Boolean).join(" · ") || `${split.currency} split`}
            </p>
            <button
              type="button"
              onClick={() => setMembersOpen(true)}
              className="mt-3 inline-flex items-center gap-2 rounded-full pr-2 text-sm text-muted-foreground transition-colors hover:text-foreground"
              aria-label={`Show ${plural(active.length, "member")}`}
            >
              <AvatarStack people={active} max={5} />
              <span>{plural(active.length, "member")}</span>
            </button>
          </div>
          <div className="flex shrink-0 items-center gap-1.5">
            {split.status === "OPEN" && (
              <Button size="lg" variant="outline" className="hidden h-10 rounded-xl px-3.5 sm:inline-flex" onClick={() => setInviteOpen(true)}>
                <UserPlus /> Invite
              </Button>
            )}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon-lg" className="size-10 rounded-xl" aria-label="Split options">
                  <MoreHorizontal />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56">
                <DropdownMenuItem onSelect={() => setInviteOpen(true)} className="sm:hidden">
                  <UserPlus /> Invite people
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => setMembersOpen(true)}>
                  <Users /> Members
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={onShortcuts} className="hidden md:flex">
                  <Keyboard /> Keyboard shortcuts <span className="ml-auto font-mono text-xs text-muted-foreground">?</span>
                </DropdownMenuItem>
                {isOwner ? (
                  <>
                    <DropdownMenuItem onSelect={() => setEditOpen(true)}>
                      <Pencil /> Edit split
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem onSelect={() => setConfirm("regenerate")}>
                      <RefreshCw /> New invite link
                    </DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => toggleInvite.mutate()}>
                      {split.inviteEnabled ? <Link2Off /> : <Link2 />}
                      {split.inviteEnabled ? "Turn invite link off" : "Turn invite link on"}
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    {split.status === "ARCHIVED" || split.status === "SETTLED" ? (
                      <DropdownMenuItem onSelect={() => setStatus.mutate("OPEN")}>
                        <ArchiveRestore /> Reopen split
                      </DropdownMenuItem>
                    ) : (
                      <DropdownMenuItem onSelect={() => setStatus.mutate("ARCHIVED")}>
                        <Archive /> Archive split
                      </DropdownMenuItem>
                    )}
                    <DropdownMenuItem variant="destructive" onSelect={() => setConfirm("delete")}>
                      <Trash2 /> Delete split
                    </DropdownMenuItem>
                  </>
                ) : (
                  <>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem variant="destructive" onSelect={() => setConfirm("leave")}>
                      <LogOut /> Leave split
                    </DropdownMenuItem>
                  </>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>

        <div className="receipt-divider my-5" />

        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          <div>
            <dt className="text-xs text-muted-foreground">Total spend</dt>
            <dd>
              <MoneyText minor={split.totalSpendMinor} currency={split.currency} className="text-[clamp(1.5rem,4vw,2rem)] font-semibold" />
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Your balance · {balanceWords(split.myNetMinor)}</dt>
            <dd>
              <MoneyText minor={split.myNetMinor} currency={split.currency} signed className="text-[clamp(1.5rem,4vw,2rem)] font-semibold" />
            </dd>
          </div>
          <div className="hidden sm:block">
            <dt className="text-xs text-muted-foreground">Expenses</dt>
            <dd className="font-money text-[clamp(1.5rem,4vw,2rem)] font-semibold">{split.expenseCount}</dd>
          </div>
        </dl>
      </div>

      <ResponsiveSheet open={inviteOpen} onOpenChange={setInviteOpen} title="Invite flatmates" description={`Anyone with this link can join ${split.name}.`}>
        <ShareInvite splitId={split.id} splitName={split.name} inviteUrl={split.inviteUrl} inviteEnabled={split.inviteEnabled} />
      </ResponsiveSheet>
      <MembersSheet split={split} open={membersOpen} onOpenChange={setMembersOpen} onInvite={() => setInviteOpen(true)} />
      {isOwner && <EditSplitDialog split={split} open={editOpen} onOpenChange={setEditOpen} />}

      <ConfirmDialog
        open={confirm === "regenerate"}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="Create a new invite link?"
        description="The current link will stop working immediately. People who already joined stay in the split."
        confirmLabel="Create new link"
        pending={regenerate.isPending}
        onConfirm={() => regenerate.mutate()}
      />
      <ConfirmDialog
        open={confirm === "delete"}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="Delete this split?"
        description={
          split.expenseCount > 0
            ? "This split has expenses, so it will be archived instead of deleted. Its history stays available."
            : "This permanently deletes the split for everyone. This can't be undone."
        }
        confirmLabel={split.expenseCount > 0 ? "Archive split" : "Delete split"}
        destructive
        pending={remove.isPending}
        onConfirm={() => remove.mutate()}
      />
      <ConfirmDialog
        open={confirm === "leave"}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="Leave this split?"
        description="You'll lose access to its expenses and balances. You can rejoin with the invite link."
        confirmLabel="Leave split"
        destructive
        pending={leave.isPending}
        onConfirm={() => leave.mutate()}
      />
    </header>
  )
}
