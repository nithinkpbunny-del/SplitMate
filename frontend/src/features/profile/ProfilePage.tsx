import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { motion } from "framer-motion"
import { Check, Loader2, Monitor, Moon, Sun } from "lucide-react"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"

import { usersApi } from "@/api/auth"
import { ApiError } from "@/api/client"
import type { User } from "@/api/types"
import { FormField, inputClass } from "@/components/FormField"
import { MemberAvatar } from "@/components/MemberAvatar"
import { PasswordStrength } from "@/components/PasswordStrength"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { meKey, useMe } from "@/features/auth/useAuth"
import { useTheme, type ThemeChoice } from "@/hooks/useTheme"
import { cn } from "@/lib/utils"

const passwordSchema = z.object({
  currentPassword: z.string().min(1, "Enter your current password"),
  newPassword: z
    .string()
    .min(8, "Password needs at least 8 characters, including a letter and a number")
    .regex(/\p{L}/u, "Password needs at least 8 characters, including a letter and a number")
    .regex(/\d/, "Password needs at least 8 characters, including a letter and a number"),
})

const nameSchema = z.object({ name: z.string().trim().min(2, "Name must be 2 to 50 characters").max(50, "Name must be 2 to 50 characters") })

export function ProfilePage() {
  const { data: me } = useMe()
  if (!me) return null
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center gap-4">
        <MemberAvatar person={me} size="xl" />
        <div className="min-w-0">
          <h1 className="truncate font-heading text-3xl font-bold">{me.name}</h1>
          <p className="truncate text-muted-foreground">{me.email}</p>
        </div>
      </div>
      <ProfileCard me={me} />
      <ThemeCard />
      <PasswordCard />
    </div>
  )
}

function Section({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return (
    <section className="rounded-3xl border border-hairline bg-surface p-5 sm:p-6">
      <h2 className="font-heading text-lg font-bold">{title}</h2>
      <p className="mt-0.5 text-sm text-muted-foreground">{description}</p>
      <div className="mt-5">{children}</div>
    </section>
  )
}

function ProfileCard({ me }: { me: User }) {
  const queryClient = useQueryClient()
  const colors = useQuery({ queryKey: ["avatar-colors"], queryFn: usersApi.avatarColors, staleTime: Infinity })
  const form = useForm<{ name: string }>({ resolver: zodResolver(nameSchema), values: { name: me.name } })

  const update = useMutation({
    mutationFn: usersApi.updateProfile,
    onSuccess: (user) => {
      queryClient.setQueryData(meKey, user)
      queryClient.invalidateQueries({ queryKey: ["splits"] })
      toast.success("Profile updated")
    },
  })

  return (
    <Section title="Profile" description="How you appear to your flatmates in every split.">
      <form onSubmit={form.handleSubmit((v) => update.mutate({ name: v.name }))} className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <FormField id="profile-name" label="Name" error={form.formState.errors.name?.message} className="flex-1">
          <Input id="profile-name" className={inputClass} aria-invalid={!!form.formState.errors.name} {...form.register("name")} />
        </FormField>
        <Button type="submit" size="xl" disabled={update.isPending || !form.formState.isDirty}>
          {update.isPending ? <Loader2 className="animate-spin" /> : "Save"}
        </Button>
      </form>
      <p className="mb-2 mt-6 text-[13px] font-medium text-muted-foreground">Avatar colour</p>
      <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Avatar colour">
        {(colors.data ?? []).map((color) => {
          const selected = color.toUpperCase() === me.avatarColor.toUpperCase()
          return (
            <motion.button
              key={color}
              type="button"
              role="radio"
              aria-checked={selected}
              aria-label={`Colour ${color}`}
              whileTap={{ scale: 0.9 }}
              onClick={() => !selected && update.mutate({ avatarColor: color })}
              className={cn(
                "relative inline-flex size-10 items-center justify-center rounded-full ring-offset-2 ring-offset-surface transition-shadow",
                selected && "ring-2 ring-foreground",
              )}
              style={{ backgroundColor: color }}
            >
              {selected && <Check className="size-4 text-[#0A0C10]" strokeWidth={3} />}
            </motion.button>
          )
        })}
      </div>
    </Section>
  )
}

const themes: { value: ThemeChoice; label: string; icon: typeof Moon }[] = [
  { value: "dark", label: "Midnight", icon: Moon },
  { value: "light", label: "Light", icon: Sun },
  { value: "system", label: "System", icon: Monitor },
]

function ThemeCard() {
  const { theme, setTheme } = useTheme()
  return (
    <Section title="Theme" description="Midnight is the house style. System follows your device.">
      <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label="Theme">
        {themes.map((t) => (
          <button
            key={t.value}
            type="button"
            role="radio"
            aria-checked={theme === t.value}
            onClick={() => setTheme(t.value)}
            className={cn(
              "flex flex-col items-center gap-2 rounded-2xl border border-hairline bg-surface-2/50 px-3 py-4 text-sm font-medium transition-colors hover:bg-surface-2",
              theme === t.value && "border-lime-ink bg-primary/10 text-foreground",
            )}
          >
            <t.icon className="size-5" />
            {t.label}
          </button>
        ))}
      </div>
    </Section>
  )
}

function PasswordCard() {
  const form = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: "", newPassword: "" },
  })
  const { errors } = form.formState
  const change = useMutation({
    mutationFn: usersApi.changePassword,
    meta: { silent: true },
    onSuccess: () => {
      form.reset()
      toast.success("Password changed")
    },
    onError: (err) => {
      if (err instanceof ApiError && err.message.toLowerCase().includes("current")) {
        form.setError("currentPassword", { message: err.message })
      } else {
        form.setError("newPassword", { message: err.message })
      }
    },
  })
  return (
    <Section title="Password" description="At least 8 characters with a letter and a number.">
      <form onSubmit={form.handleSubmit((v) => change.mutate(v))} className="space-y-4" noValidate>
        <FormField id="current-password" label="Current password" error={errors.currentPassword?.message}>
          <Input
            id="current-password"
            type="password"
            autoComplete="current-password"
            className={inputClass}
            aria-invalid={!!errors.currentPassword}
            {...form.register("currentPassword")}
          />
        </FormField>
        <FormField id="new-password" label="New password" error={errors.newPassword?.message}>
          <Input
            id="new-password"
            type="password"
            autoComplete="new-password"
            className={inputClass}
            aria-invalid={!!errors.newPassword}
            {...form.register("newPassword")}
          />
          <div className="pt-1">
            <PasswordStrength password={form.watch("newPassword")} />
          </div>
        </FormField>
        <Button type="submit" size="xl" disabled={change.isPending}>
          {change.isPending ? <Loader2 className="animate-spin" /> : "Change password"}
        </Button>
      </form>
    </Section>
  )
}
