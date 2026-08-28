import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatBytes(value: number | null | undefined): string {
  if (value == null) return 'Not available'
  if (value < 1024) return `${value.toLocaleString()} bytes`
  const units = ['KB', 'MB', 'GB']
  let amount = value / 1024
  let unit = 0
  while (amount >= 1024 && unit < units.length - 1) {
    amount /= 1024
    unit += 1
  }
  return `${amount.toFixed(amount >= 10 ? 1 : 2)} ${units[unit]}`
}

export function formatDuration(value: number | null | undefined): string {
  if (value == null) return 'Not available'
  if (value < 1000) return `${value.toLocaleString()} ms`
  return `${(value / 1000).toFixed(value >= 10_000 ? 1 : 2)} s`
}

export function formatFact(value: string | number | null | undefined): string {
  return value == null || value === '' ? 'Not available' : String(value)
}
