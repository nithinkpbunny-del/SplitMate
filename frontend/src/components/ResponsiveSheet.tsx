import type { ReactNode } from "react"

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Drawer, DrawerContent, DrawerDescription, DrawerHeader, DrawerTitle } from "@/components/ui/drawer"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { useIsDesktop } from "@/hooks/useMediaQuery"
import { cn } from "@/lib/utils"

/**
 * One API for overlays: on phones it is always a bottom sheet (thumb-friendly); on desktop it is a
 * centred dialog or a right-side drawer.
 */
export function ResponsiveSheet({
  open,
  onOpenChange,
  title,
  description,
  children,
  desktop = "dialog",
  className,
  hideHeader = false,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: ReactNode
  children: ReactNode
  desktop?: "dialog" | "drawer"
  className?: string
  hideHeader?: boolean
}) {
  const isDesktop = useIsDesktop()

  if (!isDesktop) {
    return (
      <Drawer open={open} onOpenChange={onOpenChange} repositionInputs={false}>
        <DrawerContent className={cn("max-h-[94dvh] bg-popover", className)}>
          <DrawerHeader className={cn("text-left", hideHeader && "sr-only")}>
            <DrawerTitle className="font-heading text-xl font-bold">{title}</DrawerTitle>
            {description && <DrawerDescription>{description}</DrawerDescription>}
          </DrawerHeader>
          <div className="overflow-y-auto px-4 pb-[max(1rem,env(safe-area-inset-bottom))]">{children}</div>
        </DrawerContent>
      </Drawer>
    )
  }

  if (desktop === "drawer") {
    return (
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent side="right" className={cn("w-full gap-0 border-hairline sm:max-w-[480px]", className)}>
          <SheetHeader className={cn("px-6 pt-6", hideHeader && "sr-only")}>
            <SheetTitle className="font-heading text-2xl font-bold">{title}</SheetTitle>
            {description && <SheetDescription>{description}</SheetDescription>}
          </SheetHeader>
          <div className="flex-1 overflow-y-auto px-6 pb-6 pt-4">{children}</div>
        </SheetContent>
      </Sheet>
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className={cn("max-h-[90dvh] gap-0 overflow-y-auto rounded-3xl border border-hairline p-6 sm:max-w-[480px]", className)}>
        <DialogHeader className={cn("mb-5 text-left", hideHeader && "sr-only")}>
          <DialogTitle className="font-heading text-2xl font-bold">{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        {children}
      </DialogContent>
    </Dialog>
  )
}
