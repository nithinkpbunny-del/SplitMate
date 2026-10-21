import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { ArrowRight, Eye, EyeOff, Loader2 } from "lucide-react"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { Link, useNavigate, useSearchParams } from "react-router"
import { toast } from "sonner"
import { z } from "zod"

import { authApi } from "@/api/auth"
import { ApiError } from "@/api/client"
import { FormField, inputClass } from "@/components/FormField"
import { PasswordStrength } from "@/components/PasswordStrength"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { firstName } from "@/lib/format"
import { cn } from "@/lib/utils"

import { AuthLayout } from "./AuthLayout"
import { meKey, safeNext } from "./useAuth"

// Mirrors the backend rules in RegisterRequest / PasswordRules.
const schema = z.object({
  name: z.string().trim().min(2, "Name must be 2 to 50 characters").max(50, "Name must be 2 to 50 characters"),
  email: z.string().trim().min(1, "Enter your email").email("Enter a valid email address"),
  password: z
    .string()
    .min(8, "Password needs at least 8 characters, including a letter and a number")
    .max(100, "Password is too long")
    .regex(/\p{L}/u, "Password needs at least 8 characters, including a letter and a number")
    .regex(/\d/, "Password needs at least 8 characters, including a letter and a number"),
})

type Values = z.infer<typeof schema>

export function RegisterPage() {
  const [params] = useSearchParams()
  const next = safeNext(params.get("next"))
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [show, setShow] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { name: "", email: "", password: "" } })
  const { errors } = form.formState
  const password = form.watch("password")

  const register = useMutation({
    mutationFn: authApi.register,
    meta: { silent: true },
    onSuccess: (user) => {
      queryClient.setQueryData(meKey, user)
      toast.success(`Welcome to SplitMate, ${firstName(user.name)}!`)
      navigate(next, { replace: true })
    },
    onError: (err) => {
      if (err instanceof ApiError && err.fieldErrors.length) {
        err.fieldErrors.forEach((fe) => form.setError(fe.field as keyof Values, { message: fe.message }))
      } else if (err instanceof ApiError && err.status === 409) {
        form.setError("email", { message: err.message })
      } else {
        setFormError(err.message)
      }
    },
  })

  const onSubmit = form.handleSubmit((values) => {
    setFormError(null)
    register.mutate(values)
  })

  const nextQuery = params.get("next") ? `?next=${encodeURIComponent(next)}` : ""

  return (
    <AuthLayout
      title="Create your account"
      subtitle={next.startsWith("/join/") ? "One quick step, then you're in the split." : "Takes 20 seconds. Then start your first split."}
      footer={
        <>
          Already have an account?{" "}
          <Link to={`/login${nextQuery}`} className="font-semibold text-foreground underline-offset-4 hover:underline">
            Log in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        {formError && (
          <div role="alert" className="rounded-xl border border-negative/30 bg-negative/10 px-3.5 py-3 text-sm text-negative">
            {formError}
          </div>
        )}
        <FormField id="name" label="Your name" error={errors.name?.message}>
          <Input
            id="name"
            autoComplete="name"
            autoFocus
            placeholder="Aarav Mehta"
            className={inputClass}
            aria-invalid={!!errors.name}
            aria-describedby={errors.name ? "name-error" : undefined}
            {...form.register("name")}
          />
        </FormField>
        <FormField id="email" label="Email" error={errors.email?.message}>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            className={inputClass}
            aria-invalid={!!errors.email}
            aria-describedby={errors.email ? "email-error" : undefined}
            {...form.register("email")}
          />
        </FormField>
        <FormField id="password" label="Password" error={errors.password?.message}>
          <div className="relative">
            <Input
              id="password"
              type={show ? "text" : "password"}
              autoComplete="new-password"
              placeholder="8+ characters, a letter and a number"
              className={cn(inputClass, "pr-11")}
              aria-invalid={!!errors.password}
              aria-describedby={errors.password ? "password-error" : "password-strength"}
              {...form.register("password")}
            />
            <button
              type="button"
              onClick={() => setShow((s) => !s)}
              className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-xl text-muted-foreground hover:text-foreground"
              aria-label={show ? "Hide password" : "Show password"}
            >
              {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
            </button>
          </div>
          <div id="password-strength" className="pt-1">
            <PasswordStrength password={password} />
          </div>
        </FormField>
        <Button type="submit" size="xl" className="w-full" disabled={register.isPending}>
          {register.isPending ? <Loader2 className="animate-spin" /> : <>Create account <ArrowRight /></>}
        </Button>
      </form>
    </AuthLayout>
  )
}
