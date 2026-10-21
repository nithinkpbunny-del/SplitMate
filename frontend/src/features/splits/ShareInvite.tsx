import { AnimatePresence, motion } from "framer-motion"
import { ArrowRight, Check, Copy, Link2Off, MessageCircle, Share2 } from "lucide-react"
import { QRCodeSVG } from "qrcode.react"
import { useState } from "react"
import { Link } from "react-router"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"

/**
 * The invite share screen: link in a mono field, copy with a checkmark, native share sheet,
 * WhatsApp, and a QR code for the person standing next to you.
 */
export function ShareInvite({
  splitId,
  splitName,
  inviteUrl,
  inviteEnabled,
  showGoToSplit = false,
  onDone,
}: {
  splitId: string
  splitName: string
  inviteUrl: string
  inviteEnabled: boolean
  showGoToSplit?: boolean
  onDone?: () => void
}) {
  const [copied, setCopied] = useState(false)
  const message = `Join "${splitName}" on SplitMate so we can split our expenses: ${inviteUrl}`
  const canShare = typeof navigator !== "undefined" && typeof navigator.share === "function"

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(inviteUrl)
    } catch {
      // Clipboard API blocked (e.g. insecure context): fall back to a selection copy.
      const input = document.getElementById("invite-link") as HTMLInputElement | null
      input?.select()
      document.execCommand("copy")
    }
    setCopied(true)
    toast.success("Invite link copied")
    window.setTimeout(() => setCopied(false), 1800)
  }

  const share = async () => {
    try {
      await navigator.share({ title: `Join ${splitName} on SplitMate`, text: message, url: inviteUrl })
    } catch {
      // the user closed the share sheet
    }
  }

  if (!inviteEnabled) {
    return (
      <div className="flex items-start gap-3 rounded-2xl border border-hairline bg-surface-2/60 p-4 text-sm">
        <Link2Off className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
        <p className="text-muted-foreground">The invite link is turned off. The owner can turn it back on from the split menu.</p>
      </div>
    )
  }

  return (
    <div className="space-y-5">
      <div>
        <label htmlFor="invite-link" className="text-[13px] font-medium text-muted-foreground">
          Invite link
        </label>
        <div className="mt-1.5 flex items-stretch gap-2">
          <input
            id="invite-link"
            readOnly
            value={inviteUrl}
            onFocus={(e) => e.currentTarget.select()}
            className="h-12 min-w-0 flex-1 rounded-xl border border-input bg-surface-2/60 px-3.5 font-money text-[13px] text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring/40"
          />
          <Button onClick={copy} size="xl" className="h-12 w-[104px]" aria-live="polite">
            <AnimatePresence mode="wait" initial={false}>
              {copied ? (
                <motion.span key="done" initial={{ scale: 0.4, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ opacity: 0 }} className="inline-flex items-center gap-1.5">
                  <Check strokeWidth={3} /> Copied
                </motion.span>
              ) : (
                <motion.span key="copy" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="inline-flex items-center gap-1.5">
                  <Copy /> Copy
                </motion.span>
              )}
            </AnimatePresence>
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2">
        {canShare && (
          <Button variant="outline" size="xl" onClick={share}>
            <Share2 /> Share
          </Button>
        )}
        <Button variant="outline" size="xl" asChild className={canShare ? "" : "col-span-2"}>
          <a href={`https://wa.me/?text=${encodeURIComponent(message)}`} target="_blank" rel="noreferrer">
            <MessageCircle /> Send on WhatsApp
          </a>
        </Button>
      </div>

      <div className="flex items-center gap-4 rounded-2xl border border-hairline bg-surface-2/40 p-4">
        <div className="rounded-xl bg-white p-2.5">
          <QRCodeSVG value={inviteUrl} size={104} bgColor="#FFFFFF" fgColor="#0A0C10" level="M" aria-label="QR code of the invite link" />
        </div>
        <p className="text-sm text-muted-foreground">
          Flatmate right next to you? They can scan this with their phone camera to open the invite.
        </p>
      </div>

      {showGoToSplit && (
        <Button asChild size="xl" className="w-full">
          <Link to={`/app/splits/${splitId}`} onClick={onDone}>
            Go to split <ArrowRight />
          </Link>
        </Button>
      )}
    </div>
  )
}
