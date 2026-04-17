import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merge Tailwind class names with clsx conditionals + tailwind-merge dedup.
 * Lifted from shadcn/ui; required by every primitive that accepts `className`.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
