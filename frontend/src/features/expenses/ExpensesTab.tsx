import { useInfiniteQuery } from "@tanstack/react-query"
import { AnimatePresence, LayoutGroup, motion } from "framer-motion"
import { Pencil, Plus, Receipt, Search, ShoppingCart, Trash2, Users, X } from "lucide-react"
import { useEffect, useMemo, useState, type RefObject } from "react"

import { expenseKeys, expensesApi, type ExpenseFilters } from "@/api/expenses"
import type { Category, Expense, Split } from "@/api/types"
import { CategoryIcon } from "@/components/CategoryIcon"
import { EmptyState } from "@/components/EmptyState"
import { MemberAvatar } from "@/components/MemberAvatar"
import { MoneyText } from "@/components/MoneyText"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { firstName, formatLongDay, plural } from "@/lib/format"
import { formatMoney } from "@/lib/money"
import { cn } from "@/lib/utils"

const ALL = "all"

export function ExpensesTab({
  split,
  me,
  categories,
  searchRef,
  onAdd,
  onOpen,
  onDelete,
}: {
  split: Split
  me: string
  categories: Category[]
  searchRef: RefObject<HTMLInputElement | null>
  onAdd: () => void
  onOpen: (expense: Expense) => void
  onDelete: (expense: Expense) => void
}) {
  const [search, setSearch] = useState("")
  const [q, setQ] = useState("")
  const [category, setCategory] = useState<string>()
  const [paidBy, setPaidBy] = useState<string>()
  const [participant, setParticipant] = useState<string>()
  const [sort, setSort] = useState<NonNullable<ExpenseFilters["sort"]>>("date_desc")
  const [expanded, setExpanded] = useState<string | null>(null)

  // Debounce the search box so typing doesn't fire a request per key.
  useEffect(() => {
    const id = window.setTimeout(() => setQ(search.trim()), 250)
    return () => window.clearTimeout(id)
  }, [search])

  const filters: ExpenseFilters = { category, paidBy, participant, q: q || undefined, sort }
  const filtered = Boolean(category || paidBy || participant || q)

  const query = useInfiniteQuery({
    queryKey: expenseKeys.list(split.id, filters),
    queryFn: ({ pageParam }) => expensesApi.list(split.id, filters, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    placeholderData: (prev) => prev,
  })

  const items = useMemo(() => query.data?.pages.flatMap((p) => p.items) ?? [], [query.data])
  const first = query.data?.pages[0]
  const members = split.members
  const memberById = useMemo(() => new Map(members.map((m) => [m.userId, m])), [members])
  const categoryById = useMemo(() => new Map(categories.map((c) => [c.id, c])), [categories])
  const active = members.filter((m) => m.active)

  const groups = useMemo(() => {
    if (sort !== "date_desc" && sort !== "date_asc") return [{ date: null as string | null, items, total: 0 }]
    const map = new Map<string, Expense[]>()
    for (const e of items) map.set(e.date, [...(map.get(e.date) ?? []), e])
    return [...map.entries()].map(([date, list]) => ({ date: date as string | null, items: list, total: list.reduce((s, e) => s + e.amountMinor, 0) }))
  }, [items, sort])

  const clear = () => {
    setSearch("")
    setCategory(undefined)
    setPaidBy(undefined)
    setParticipant(undefined)
  }

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="space-y-3">
        <div className="flex flex-col gap-2 sm:flex-row">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <input
              ref={searchRef}
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search expenses"
              aria-label="Search expenses"
              className="h-11 w-full rounded-xl border border-input bg-surface pl-10 pr-10 text-[15px] outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring/40 dark:bg-surface-2/60"
            />
            <kbd className="pointer-events-none absolute right-3 top-1/2 hidden -translate-y-1/2 rounded border border-hairline px-1.5 font-mono text-[11px] text-muted-foreground sm:block">/</kbd>
          </div>
          <div className="grid grid-cols-3 gap-2 sm:flex">
            <FilterSelect label="payer" value={paidBy} onChange={setPaidBy} options={active.map((m) => ({ value: m.userId, label: m.userId === me ? "Me" : firstName(m.name) }))} />
            <FilterSelect label="member" value={participant} onChange={setParticipant} options={active.map((m) => ({ value: m.userId, label: m.userId === me ? "Me" : firstName(m.name) }))} />
            <Select value={sort} onValueChange={(v) => setSort(v as typeof sort)}>
              <SelectTrigger className="h-11 rounded-xl bg-surface data-[size=default]:h-11 sm:w-36 dark:bg-surface-2/60" aria-label="Sort">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="date_desc">Newest</SelectItem>
                <SelectItem value="date_asc">Oldest</SelectItem>
                <SelectItem value="amount_desc">Highest</SelectItem>
                <SelectItem value="amount_asc">Lowest</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <div className="scrollbar-none -mx-4 flex gap-1.5 overflow-x-auto px-4 sm:mx-0 sm:flex-wrap sm:px-0" role="group" aria-label="Filter by category">
          <CategoryChip selected={!category} onClick={() => setCategory(undefined)}>
            All
          </CategoryChip>
          {categories.map((c) => (
            <CategoryChip key={c.id} selected={category === c.id} onClick={() => setCategory(category === c.id ? undefined : c.id)} color={c.color}>
              {c.name}
            </CategoryChip>
          ))}
        </div>
      </div>

      {/* Totals for the current filter */}
      {first && (
        <div className="flex items-center justify-between rounded-2xl bg-surface-2/50 px-4 py-2.5 text-sm">
          <span className="text-muted-foreground">
            {plural(first.totalItems, "expense")}
            {filtered && " match"}
          </span>
          <span className="flex items-center gap-3">
            <MoneyText minor={first.totalAmountMinor} currency={split.currency} className="font-semibold" />
            {filtered && (
              <button type="button" onClick={clear} className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground">
                <X className="size-3" /> Clear
              </button>
            )}
          </span>
        </div>
      )}

      {/* List */}
      {query.isPending ? (
        <ExpenseListSkeleton />
      ) : items.length === 0 ? (
        filtered ? (
          <EmptyState
            icons={[Receipt, Search, ShoppingCart]}
            title="Nothing matches"
            description="No expenses match these filters."
            action={
              <Button variant="outline" size="xl" onClick={clear}>
                Clear filters
              </Button>
            }
          />
        ) : (
          <EmptyState
            icons={[ShoppingCart, Receipt, Users]}
            title="No expenses yet"
            description="Add rent, groceries, a dinner out. Pick who shares each one and SplitMate does the maths."
            action={
              split.status === "OPEN" ? (
                <Button size="xl" onClick={onAdd}>
                  <Plus /> Add the first expense
                </Button>
              ) : undefined
            }
          />
        )
      ) : (
        <LayoutGroup>
          <div className={cn("space-y-5 transition-opacity", query.isPlaceholderData && "opacity-60")}>
            {groups.map((group) => (
              <section key={group.date ?? "all"} aria-label={group.date ? formatLongDay(group.date) : "Expenses"}>
                {group.date && (
                  <div className="mb-2 flex items-baseline justify-between px-1">
                    <h3 className="font-heading text-sm font-bold">{formatLongDay(group.date)}</h3>
                    <span className="font-money text-xs text-muted-foreground">{formatMoney(group.total, split.currency)}</span>
                  </div>
                )}
                <ul className="overflow-hidden rounded-3xl border border-hairline bg-surface">
                  <AnimatePresence initial={false}>
                    {group.items.map((e, i) => (
                      <ExpenseRow
                        key={e.id}
                        expense={e}
                        index={i}
                        me={me}
                        currency={split.currency}
                        category={categoryById.get(e.categoryId)}
                        memberById={memberById}
                        expanded={expanded === e.id}
                        onToggle={() => setExpanded(expanded === e.id ? null : e.id)}
                        onEdit={() => onOpen(e)}
                        onDelete={() => onDelete(e)}
                        editable={split.status === "OPEN" && e.canEdit}
                      />
                    ))}
                  </AnimatePresence>
                </ul>
              </section>
            ))}
          </div>
        </LayoutGroup>
      )}

      {query.hasNextPage && (
        <div className="flex justify-center">
          <Button variant="outline" size="xl" onClick={() => query.fetchNextPage()} disabled={query.isFetchingNextPage}>
            {query.isFetchingNextPage ? "Loading…" : "Load more"}
          </Button>
        </div>
      )}
    </div>
  )
}

function ExpenseRow({
  expense,
  index,
  me,
  currency,
  category,
  memberById,
  expanded,
  onToggle,
  onEdit,
  onDelete,
  editable,
}: {
  expense: Expense
  index: number
  me: string
  currency: string
  category?: Category
  memberById: Map<string, Split["members"][number]>
  expanded: boolean
  onToggle: () => void
  onEdit: () => void
  onDelete: () => void
  editable: boolean
}) {
  const payer = memberById.get(expense.paidBy)
  const myShare = expense.shares.find((s) => s.userId === me)?.amountMinor
  const pendingSave = expense.id.startsWith("optimistic-")
  const n = expense.participants.length
  const sharedBy =
    n === 1
      ? `for ${expense.participants[0] === me ? "you" : firstName(memberById.get(expense.participants[0])?.name ?? "")}`
      : `split ${n} ways`

  return (
    <motion.li
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: pendingSave ? 0.6 : 1, y: 0 }}
      exit={{ opacity: 0, height: 0, transition: { duration: 0.2 } }}
      transition={{ delay: Math.min(index, 10) * 0.025, layout: { duration: 0.25 } }}
      className="border-b border-dashed border-hairline last:border-0"
    >
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={expanded}
        className="flex w-full items-center gap-3 px-4 py-3.5 text-left transition-colors hover:bg-surface-2/50 sm:px-5"
      >
        <motion.span layout="position">
          <CategoryIcon category={category} />
        </motion.span>
        <span className="min-w-0 flex-1">
          <span className="block truncate font-medium">{expense.description}</span>
          <span className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
            {payer && <MemberAvatar person={payer} size="xs" />}
            <span className="truncate">{expense.paidBy === me ? "You" : firstName(payer?.name ?? "Someone")} paid</span>
            <span className="shrink-0 rounded-full bg-surface-2 px-1.5 py-0.5 text-[10.5px] font-medium">{sharedBy}</span>
          </span>
        </span>
        <span className="text-right">
          <MoneyText minor={expense.amountMinor} currency={currency} animateValue={false} className="block font-semibold" />
          <span className="block text-[11px] text-muted-foreground">
            {myShare !== undefined ? `your share ${formatMoney(myShare, currency)}` : "not involved"}
          </span>
        </span>
      </button>

      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.22 }}
            className="overflow-hidden"
          >
            <div className="mx-4 mb-4 rounded-2xl bg-surface-2/50 p-4 sm:mx-5">
              <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Who shares it</p>
              <ul className="mt-2 grid gap-1.5 sm:grid-cols-2">
                {expense.shares.map((s) => {
                  const m = memberById.get(s.userId)
                  return (
                    <li key={s.userId} className="flex items-center justify-between gap-2 text-sm">
                      <span className="flex items-center gap-2">
                        {m && <MemberAvatar person={m} size="xs" />}
                        {s.userId === me ? "You" : firstName(m?.name ?? "Former member")}
                      </span>
                      <span className="font-money">{formatMoney(s.amountMinor, currency)}</span>
                    </li>
                  )
                })}
              </ul>
              {expense.notes && <p className="mt-3 border-t border-hairline pt-3 text-sm text-muted-foreground">“{expense.notes}”</p>}
              <div className="mt-3 flex items-center justify-between gap-2 border-t border-hairline pt-3">
                <span className="text-xs text-muted-foreground">
                  {category?.name} · added by {expense.createdBy === me ? "you" : firstName(memberById.get(expense.createdBy)?.name ?? "someone")}
                </span>
                <span className="flex gap-1.5">
                  {editable && !pendingSave && (
                    <>
                      <Button size="sm" variant="ghost" onClick={onDelete} className="text-negative" aria-label={`Delete ${expense.description}`}>
                        <Trash2 /> Delete
                      </Button>
                      <Button size="sm" variant="outline" onClick={onEdit}>
                        <Pencil /> Edit
                      </Button>
                    </>
                  )}
                  {!editable && !pendingSave && (
                    <Button size="sm" variant="ghost" onClick={onEdit}>
                      View
                    </Button>
                  )}
                </span>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.li>
  )
}

function CategoryChip({ selected, onClick, color, children }: { selected: boolean; onClick: () => void; color?: string; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      className={cn(
        "inline-flex shrink-0 items-center gap-1.5 rounded-full border border-hairline px-3 py-1.5 text-[13px] font-medium transition-colors hover:bg-surface-2",
        selected && "border-transparent bg-foreground text-background hover:bg-foreground",
      )}
    >
      {color && <span className="size-2 rounded-full" style={{ backgroundColor: color }} aria-hidden />}
      {children}
    </button>
  )
}

function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value?: string
  onChange: (value?: string) => void
  options: { value: string; label: string }[]
}) {
  return (
    <Select value={value ?? ALL} onValueChange={(v) => onChange(v === ALL ? undefined : v)}>
      <SelectTrigger className={cn("h-11 rounded-xl bg-surface data-[size=default]:h-11 sm:w-36 dark:bg-surface-2/60", value && "ring-2 ring-lime-ink")} aria-label={`Filter by ${label}`}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ALL}>Any {label}</SelectItem>
        {options.map((o) => (
          <SelectItem key={o.value} value={o.value}>
            {label === "payer" ? `${o.label} paid` : `With ${o.label}`}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

function ExpenseListSkeleton() {
  return (
    <div className="space-y-5" aria-hidden>
      {[3, 2].map((rows, g) => (
        <div key={g}>
          <Skeleton className="mb-2 h-4 w-32" />
          <div className="space-y-px overflow-hidden rounded-3xl border border-hairline bg-surface">
            {Array.from({ length: rows }, (_, i) => (
              <div key={i} className="flex items-center gap-3 px-5 py-4">
                <Skeleton className="size-10 rounded-xl" />
                <div className="flex-1 space-y-2">
                  <Skeleton className="h-4 w-2/5" />
                  <Skeleton className="h-3 w-1/4" />
                </div>
                <Skeleton className="h-5 w-16" />
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
