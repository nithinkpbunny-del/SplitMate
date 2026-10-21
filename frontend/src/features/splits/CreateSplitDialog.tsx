import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { AnimatePresence, motion } from "framer-motion"
import { Loader2, PartyPopper } from "lucide-react"
import { useState } from "react"
import { Controller, useForm, useWatch } from "react-hook-form"
import { z } from "zod"

import { ApiError } from "@/api/client"
import { splitKeys, splitsApi } from "@/api/splits"
import type { Period, Split } from "@/api/types"
import { FormField, inputClass } from "@/components/FormField"
import { ResponsiveSheet } from "@/components/ResponsiveSheet"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { monthPeriod, parseLocalDate } from "@/lib/format"

import { CurrencySelect, EmojiPicker, MonthPicker } from "./SplitFormFields"
import { ShareInvite } from "./ShareInvite"

const schema = z.object({
  name: z.string().trim().min(3, "Name must be 3 to 60 characters").max(60, "Name must be 3 to 60 characters"),
  description: z.string().trim().max(140, "Keep it under 140 characters"),
  emoji: z.string(),
  currency: z.string().length(3),
  period: z.object({ start: z.string(), end: z.string() }).nullable(),
})

type Values = z.infer<typeof schema>

function suggestedName(period: Period | null): string {
  if (!period) return "October month split"
  const month = parseLocalDate(period.start).toLocaleString("en-GB", { month: "long" })
  return `${month} month split`
}

export function CreateSplitDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const queryClient = useQueryClient()
  const [created, setCreated] = useState<Split | null>(null)

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { name: "", description: "", emoji: "🏠", currency: "EUR", period: monthPeriod() },
  })
  const period = useWatch({ control: form.control, name: "period" })
  const { errors } = form.formState

  const create = useMutation({
    mutationFn: splitsApi.create,
    meta: { silent: true },
    onSuccess: (split) => {
      queryClient.invalidateQueries({ queryKey: splitKeys.all })
      queryClient.invalidateQueries({ queryKey: splitKeys.recentActivity })
      queryClient.setQueryData(splitKeys.detail(split.id), split)
      setCreated(split)
    },
    onError: (err) => {
      if (err instanceof ApiError && err.fieldErrors.length) {
        err.fieldErrors.forEach((fe) => form.setError(fe.field as keyof Values, { message: fe.message }))
      } else {
        form.setError("name", { message: err.message })
      }
    },
  })

  const close = (next: boolean) => {
    onOpenChange(next)
    if (!next) {
      // reset after the closing animation
      window.setTimeout(() => {
        setCreated(null)
        form.reset()
      }, 250)
    }
  }

  const onSubmit = form.handleSubmit((v) =>
    create.mutate({
      name: v.name,
      description: v.description || undefined,
      emoji: v.emoji,
      currency: v.currency,
      period: v.period ?? undefined,
    }),
  )

  return (
    <ResponsiveSheet
      open={open}
      onOpenChange={close}
      title={created ? "Your split is ready" : "New split"}
      description={created ? "Send the invite link to your flatmates. Anyone with the link can join." : "One split per month works well for a shared flat."}
    >
      <AnimatePresence mode="wait" initial={false}>
        {created ? (
          <motion.div key="share" initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0 }} transition={{ duration: 0.25 }}>
            <div className="mb-5 flex items-center gap-3 rounded-2xl bg-primary/10 p-3.5">
              <span className="flex size-11 items-center justify-center rounded-xl bg-surface text-2xl">{created.emoji ?? "🏠"}</span>
              <div className="min-w-0 flex-1">
                <p className="truncate font-heading font-bold">{created.name}</p>
                <p className="text-xs text-muted-foreground">You're the owner</p>
              </div>
              <PartyPopper className="size-5 text-lime-ink" />
            </div>
            <ShareInvite
              splitId={created.id}
              splitName={created.name}
              inviteUrl={created.inviteUrl}
              inviteEnabled={created.inviteEnabled}
              showGoToSplit
              onDone={() => close(false)}
            />
          </motion.div>
        ) : (
          <motion.form key="form" onSubmit={onSubmit} exit={{ opacity: 0, x: -24 }} className="space-y-5" noValidate>
            <FormField id="split-name" label="Name" error={errors.name?.message}>
              <Input
                id="split-name"
                autoFocus
                placeholder={suggestedName(period)}
                className={inputClass}
                aria-invalid={!!errors.name}
                aria-describedby={errors.name ? "split-name-error" : undefined}
                {...form.register("name")}
              />
            </FormField>
            <FormField id="split-emoji" label="Cover">
              <Controller control={form.control} name="emoji" render={({ field }) => <EmojiPicker value={field.value} onChange={field.onChange} />} />
            </FormField>
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField id="split-period" label="Month">
                <Controller control={form.control} name="period" render={({ field }) => <MonthPicker value={field.value} onChange={field.onChange} />} />
              </FormField>
              <FormField id="split-currency" label="Currency">
                <Controller control={form.control} name="currency" render={({ field }) => <CurrencySelect value={field.value} onChange={field.onChange} />} />
              </FormField>
            </div>
            <FormField id="split-description" label="Description (optional)" error={errors.description?.message}>
              <Input id="split-description" placeholder="Flat 4B, Berlin" className={inputClass} {...form.register("description")} />
            </FormField>
            <Button type="submit" size="xl" className="w-full" disabled={create.isPending}>
              {create.isPending ? <Loader2 className="animate-spin" /> : "Create split"}
            </Button>
          </motion.form>
        )}
      </AnimatePresence>
    </ResponsiveSheet>
  )
}
