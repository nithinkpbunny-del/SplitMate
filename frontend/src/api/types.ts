// Mirrors the backend DTOs. Money is always an integer number of minor units (cents).

export type User = {
  id: string
  name: string
  email: string
  avatarColor: string
  createdAt: string
}

export type SplitStatus = "OPEN" | "SETTLED" | "ARCHIVED"
export type MemberRole = "OWNER" | "MEMBER"

export type Period = { start: string; end: string }

export type Member = {
  userId: string
  name: string
  email: string
  avatarColor: string
  role: MemberRole
  joinedAt: string
  active: boolean
}

export type Split = {
  id: string
  name: string
  description: string | null
  emoji: string | null
  currency: string
  status: SplitStatus
  ownerId: string
  myRole: MemberRole
  members: Member[]
  inviteCode: string
  inviteUrl: string
  inviteEnabled: boolean
  period: Period | null
  totalSpendMinor: number
  expenseCount: number
  myNetMinor: number
  currencyLocked: boolean
  lastCalculatedAt: string | null
  calculationStale: boolean
  settlementVersion: number
  createdAt: string
  updatedAt: string
}

export type SplitSummary = {
  id: string
  name: string
  description: string | null
  emoji: string | null
  currency: string
  status: SplitStatus
  period: Period | null
  myRole: MemberRole
  members: Member[]
  memberCount: number
  totalSpendMinor: number
  expenseCount: number
  myNetMinor: number
  calculationStale: boolean
  lastActivityAt: string
  createdAt: string
}

export type InvitePreview = {
  code: string
  splitName: string
  emoji: string | null
  description: string | null
  ownerName: string
  memberCount: number
  members: { firstName: string; avatarColor: string }[]
  status: SplitStatus
  joinable: boolean
}

export type ActivityType =
  | "SPLIT_CREATED"
  | "SPLIT_UPDATED"
  | "SPLIT_ARCHIVED"
  | "SPLIT_REOPENED"
  | "MEMBER_JOINED"
  | "MEMBER_LEFT"
  | "MEMBER_REMOVED"
  | "INVITE_REGENERATED"
  | "INVITE_ENABLED"
  | "INVITE_DISABLED"
  | "EXPENSE_ADDED"
  | "EXPENSE_UPDATED"
  | "EXPENSE_DELETED"
  | "SPLIT_CALCULATED"
  | "PAYMENT_RECORDED"
  | "PAYMENT_DELETED"
  | "REPORT_GENERATED"
  | "SPLIT_SETTLED"

export type Activity = {
  id: string
  splitId: string
  actorId: string | null
  actorName: string
  type: ActivityType
  message: string
  metadata: Record<string, unknown>
  createdAt: string
}

export type Category = {
  id: string
  key: string
  name: string
  icon: string
  color: string
  splitId: string | null
  isDefault: boolean
  sortOrder: number
}

export type Expense = {
  id: string
  splitId: string
  description: string
  amountMinor: number
  currency: string
  categoryId: string
  paidBy: string
  splitType: "EQUAL"
  participants: string[]
  shares: { userId: string; amountMinor: number }[]
  date: string
  notes: string | null
  createdBy: string
  canEdit: boolean
  createdAt: string
  updatedAt: string
}

export type ExpensePage = {
  items: Expense[]
  page: number
  size: number
  totalItems: number
  totalPages: number
  totalAmountMinor: number
}

export type MemberSummary = {
  userId: string
  name: string
  avatarColor: string
  paidMinor: number
  shareMinor: number
  paymentsSentMinor: number
  paymentsReceivedMinor: number
  netMinor: number
}

export type TransferItem = {
  fromUserId: string
  fromName: string
  toUserId: string
  toName: string
  amountMinor: number
}

export type Settlement = {
  id: string
  splitId: string
  version: number
  calculatedBy: string
  calculatedByName: string
  calculatedAt: string
  currency: string
  totalSpendMinor: number
  expenseCount: number
  expenseIds: string[]
  memberSummaries: MemberSummary[]
  transfers: TransferItem[]
  allSettled: boolean
}

export type SettlementHistoryItem = {
  id: string
  version: number
  calculatedAt: string
  calculatedByName: string
  totalSpendMinor: number
  expenseCount: number
  transferCount: number
}

export type Payment = {
  id: string
  splitId: string
  fromUserId: string
  toUserId: string
  amountMinor: number
  note: string | null
  settlementId: string | null
  recordedBy: string
  createdAt: string
  warning: string | null
}
