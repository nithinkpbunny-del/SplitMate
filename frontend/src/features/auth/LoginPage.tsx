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
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { firstName } from "@/lib/format"
import { cn } from "@/lib/utils"

import { AuthLayout } from "./AuthLayout"
import { meKey, safeNext } from "./useAuth"

const schema = z.object({
  email: z.string().trim().min(1, "Enter your email").email("Enter a valid email address"),
  password: z.string().min(1, "Enter your password"),
})

type Values = z.infer<typeof schema>

export function LoginPage() {
  const [params] = useSearchParams()
  const next = safeNext(params.get("next"))
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [show, setShow] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: "", password: "" } })
  const { errors } = form.formState

  const login = useMutation({
    mutationFn: authApi.login,
    meta: { silent: true },
    onSuccess: (user) => {
      queryClient.setQueryData(meKey, user)
      toast.success(`Welcome back, ${firstName(user.name)}`)
      navigate(next, { replace: true })
    },
    onError: (err) => {
      if (err instanceof ApiError && err.fieldErrors.length) {
        err.fieldErrors.forEach((fe) => form.setError(fe.field as keyof Values, { message: fe.message }))
      } else {
        setFormError(err.message)
      }
    },
  })

  const onSubmit = form.handleSubmit((values) => {
    setFormError(null)
    login.mutate(values)
  })

  const nextQuery = params.get("next") ? `?next=${encodeURIComponent(next)}` : ""

  return (
    <AuthLayout
      title="Welcome back"
      subtitle={next.startsWith("/join/") ? "Log in to join the split you were invited to." : "Log in to see who owes whom."}
      footer={
        <>
          New to SplitMate?{" "}
          <Link to={`/register${nextQuery}`} className="font-semibold text-foreground underline-offset-4 hover:underline">
            Create an account
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
        <FormField id="email" label="Email" error={errors.email?.message}>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            autoFocus
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
              autoComplete="current-password"
              placeholder="Your password"
              className={cn(inputClass, "pr-11")}
              aria-invalid={!!errors.password}
              aria-describedby={errors.password ? "password-error" : undefined}
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
        </FormField>
        <Button type="submit" size="xl" className="w-full" disabled={login.isPending}>
          {login.isPending ? <Loader2 className="animate-spin" /> : <>Log in <ArrowRight /></>}
        </Button>
      </form>
    </AuthLayout>
  )
}
