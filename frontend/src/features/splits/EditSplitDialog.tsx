import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Loader2 } from "lucide-react"
import { Controller, useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"

import { splitKeys, splitsApi } from "@/api/splits"
import type { Split } from "@/api/types"
import { FormField, inputClass } from "@/components/FormField"
import { ResponsiveSheet } from "@/components/ResponsiveSheet"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

import { CurrencySelect, EmojiPicker, MonthPicker } from "./SplitFormFields"

const schema = z.object({
  name: z.string().trim().min(3, "Name must be 3 to 60 characters").max(60, "Name must be 3 to 60 characters"),
  description: z.string().trim().max(140, "Keep it under 140 characters"),
  emoji: z.string(),
  currency: z.string().length(3),
  period: z.object({ start: z.string(), end: z.string() }).nullable(),
})

type Values = z.infer<typeof schema>

export function EditSplitDialog({ split, open, onOpenChange }: { split: Split; open: boolean; onOpenChange: (open: boolean) => void }) {
  const queryClient = useQueryClient()
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: {
      name: split.name,
      description: split.description ?? "",
      emoji: split.emoji ?? "🏠",
      currency: split.currency,
      period: split.period,
    },
  })
  const { errors } = form.formState

  const save = useMutation({
    mutationFn: (v: Values) =>
      splitsApi.update(split.id, {
        name: v.name,
        description: v.description,
        emoji: v.emoji,
        currency: v.currency,
        ...(v.period ? { period: v.period } : { clearPeriod: true }),
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(splitKeys.detail(split.id), updated)
      queryClient.invalidateQueries({ queryKey: splitKeys.all })
      queryClient.invalidateQueries({ queryKey: splitKeys.activity(split.id) })
      toast.success("Split updated")
      onOpenChange(false)
    },
  })

  return (
    <ResponsiveSheet open={open} onOpenChange={onOpenChange} title="Edit split">
      <form onSubmit={form.handleSubmit((v) => save.mutate(v))} className="space-y-5" noValidate>
        <FormField id="edit-name" label="Name" error={errors.name?.message}>
          <Input id="edit-name" className={inputClass} aria-invalid={!!errors.name} {...form.register("name")} />
        </FormField>
        <FormField id="edit-emoji" label="Cover">
          <Controller control={form.control} name="emoji" render={({ field }) => <EmojiPicker value={field.value} onChange={field.onChange} />} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="edit-period" label="Month">
            <Controller control={form.control} name="period" render={({ field }) => <MonthPicker value={field.value} onChange={field.onChange} />} />
          </FormField>
          <FormField id="edit-currency" label="Currency" hint={split.currencyLocked ? "Fixed once there are expenses" : undefined}>
            <Controller
              control={form.control}
              name="currency"
              render={({ field }) => <CurrencySelect value={field.value} onChange={field.onChange} disabled={split.currencyLocked} />}
            />
          </FormField>
        </div>
        <FormField id="edit-description" label="Description" error={errors.description?.message}>
          <Input id="edit-description" placeholder="Flat 4B, Berlin" className={inputClass} {...form.register("description")} />
        </FormField>
        <Button type="submit" size="xl" className="w-full" disabled={save.isPending}>
          {save.isPending ? <Loader2 className="animate-spin" /> : "Save changes"}
        </Button>
      </form>
    </ResponsiveSheet>
  )
}
