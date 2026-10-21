import { initials } from "@/lib/format"
import { cn } from "@/lib/utils"

type Person = { name: string; avatarColor: string }

const sizes = {
  xs: "size-5 text-[9px]",
  sm: "size-7 text-[11px]",
  md: "size-9 text-[13px]",
  lg: "size-12 text-base",
  xl: "size-16 text-xl",
} as const

export type AvatarSize = keyof typeof sizes

/** Coloured initials circle. The colour is the member's own avatar colour. */
export function MemberAvatar({
  person,
  size = "md",
  className,
  ring = false,
  title,
}: {
  person: Person
  size?: AvatarSize
  className?: string
  ring?: boolean
  title?: string
}) {
  return (
    <span
      role="img"
      aria-label={title ?? person.name}
      title={title ?? person.name}
      className={cn(
        "inline-flex shrink-0 select-none items-center justify-center rounded-full font-heading font-bold text-[#0A0C10]",
        ring && "ring-2 ring-background",
        sizes[size],
        className,
      )}
      style={{ backgroundColor: person.avatarColor }}
    >
      {initials(person.name)}
    </span>
  )
}

/** Overlapping avatars with a "+N" chip when there are more than `max`. */
export function AvatarStack({
  people,
  max = 4,
  size = "sm",
  className,
}: {
  people: Person[]
  max?: number
  size?: AvatarSize
  className?: string
}) {
  const shown = people.slice(0, max)
  const extra = people.length - shown.length
  return (
    <div className={cn("flex items-center -space-x-2", className)} aria-label={people.map((p) => p.name).join(", ")}>
      {shown.map((p, i) => (
        <MemberAvatar key={`${p.name}-${i}`} person={p} size={size} ring />
      ))}
      {extra > 0 && (
        <span
          className={cn(
            "inline-flex items-center justify-center rounded-full bg-surface-2 font-semibold text-muted-foreground ring-2 ring-background",
            sizes[size],
          )}
        >
          +{extra}
        </span>
      )}
    </div>
  )
}
