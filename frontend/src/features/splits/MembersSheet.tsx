import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Crown, LogOut, UserMinus, UserPlus } from "lucide-react"
import { useState } from "react"
import { useNavigate } from "react-router"
import { toast } from "sonner"

import { splitKeys, splitsApi } from "@/api/splits"
import type { Member, Split } from "@/api/types"
import { ConfirmDialog } from "@/components/ConfirmDialog"
import { MemberAvatar } from "@/components/MemberAvatar"
import { ResponsiveSheet } from "@/components/ResponsiveSheet"
import { Button } from "@/components/ui/button"
import { useMe } from "@/features/auth/useAuth"
import { formatShortDate, plural } from "@/lib/format"

export function MembersSheet({
  split,
  open,
  onOpenChange,
  onInvite,
}: {
  split: Split
  open: boolean
  onOpenChange: (open: boolean) => void
  onInvite: () => void
}) {
  const { data: me } = useMe()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [target, setTarget] = useState<Member | null>(null)
  const active = split.members.filter((m) => m.active)
  const isOwner = split.myRole === "OWNER"
  const leaving = target?.userId === me?.id

  const remove = useMutation({
    mutationFn: (member: Member) => splitsApi.removeMember(split.id, member.userId),
    onSuccess: (_data, member) => {
      setTarget(null)
      if (member.userId === me?.id) {
        queryClient.removeQueries({ queryKey: splitKeys.detail(split.id) })
        queryClient.invalidateQueries({ queryKey: splitKeys.all })
        toast.success(`You left ${split.name}`)
        navigate("/app", { replace: true })
      } else {
        queryClient.invalidateQueries({ queryKey: splitKeys.detail(split.id) })
        queryClient.invalidateQueries({ queryKey: splitKeys.activity(split.id) })
        toast.success(`${member.name} was removed`)
      }
    },
    onError: () => setTarget(null),
  })

  return (
    <>
      <ResponsiveSheet open={open} onOpenChange={onOpenChange} title="Members" description={plural(active.length, "person", "people")} desktop="drawer">
        <ul className="divide-y divide-hairline">
          {active.map((m) => {
            const isMe = m.userId === me?.id
            const canRemove = (isOwner && !isMe) || (isMe && m.role !== "OWNER")
            return (
              <li key={m.userId} className="flex items-center gap-3 py-3">
                <MemberAvatar person={m} size="md" />
                <div className="min-w-0 flex-1">
                  <p className="flex items-center gap-1.5 truncate font-medium">
                    {m.name}
                    {isMe && <span className="text-xs text-muted-foreground">(you)</span>}
                    {m.role === "OWNER" && (
                      <span className="inline-flex items-center gap-1 rounded-full bg-primary/15 px-1.5 py-0.5 text-[11px] font-semibold text-lime-ink">
                        <Crown className="size-3" /> Owner
                      </span>
                    )}
                  </p>
                  <p className="truncate text-xs text-muted-foreground">Joined {formatShortDate(m.joinedAt.slice(0, 10))}</p>
                </div>
                {canRemove && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-muted-foreground hover:text-negative"
                    onClick={() => setTarget(m)}
                    aria-label={isMe ? "Leave split" : `Remove ${m.name}`}
                  >
                    {isMe ? <LogOut /> : <UserMinus />}
                    {isMe ? "Leave" : "Remove"}
                  </Button>
                )}
              </li>
            )
          })}
        </ul>
        {split.inviteEnabled && split.status === "OPEN" && (
          <Button
            variant="outline"
            size="xl"
            className="mt-4 w-full"
            onClick={() => {
              onOpenChange(false)
              onInvite()
            }}
          >
            <UserPlus /> Invite people
          </Button>
        )}
        <p className="mt-4 text-xs text-muted-foreground">
          People can only be removed when they're not part of any expense and their balance is zero, so nobody's share changes.
        </p>
      </ResponsiveSheet>

      <ConfirmDialog
        open={target !== null}
        onOpenChange={(o) => !o && setTarget(null)}
        title={leaving ? "Leave this split?" : `Remove ${target?.name}?`}
        description={
          leaving
            ? "You'll lose access to its expenses and balances. You can rejoin with the invite link."
            : "They'll lose access to the split. They can rejoin later with the invite link."
        }
        confirmLabel={leaving ? "Leave split" : "Remove"}
        destructive
        pending={remove.isPending}
        onConfirm={() => target && remove.mutate(target)}
      />
    </>
  )
}
