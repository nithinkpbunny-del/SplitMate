import {
  CircleEllipsis,
  Clapperboard,
  HeartPulse,
  House,
  Plane,
  Repeat,
  ShoppingBag,
  ShoppingCart,
  SprayCan,
  Tag,
  TrainFront,
  Utensils,
  Zap,
  type LucideIcon,
} from "lucide-react"

import type { Category } from "@/api/types"
import { cn } from "@/lib/utils"

const icons: Record<string, LucideIcon> = {
  home: House,
  "shopping-cart": ShoppingCart,
  zap: Zap,
  utensils: Utensils,
  "train-front": TrainFront,
  "spray-can": SprayCan,
  clapperboard: Clapperboard,
  plane: Plane,
  "shopping-bag": ShoppingBag,
  "heart-pulse": HeartPulse,
  repeat: Repeat,
  "circle-ellipsis": CircleEllipsis,
}

/** Rounded chip with the category's icon tinted in its colour. */
export function CategoryIcon({ category, size = "md", className }: { category?: Category; size?: "sm" | "md" | "lg"; className?: string }) {
  const Icon = (category && icons[category.icon]) || Tag
  const color = category?.color ?? "#94A3B8"
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center justify-center",
        size === "sm" && "size-7 rounded-lg",
        size === "md" && "size-10 rounded-xl",
        size === "lg" && "size-12 rounded-2xl",
        className,
      )}
      style={{ backgroundColor: `color-mix(in oklab, ${color} 16%, transparent)`, color }}
      aria-hidden
    >
      <Icon className={size === "sm" ? "size-3.5" : size === "md" ? "size-[18px]" : "size-5"} />
    </span>
  )
}
