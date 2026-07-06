import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * Validates Cameroon phone numbers.
 * Supports: 6XXXXXXXX, 2376XXXXXXXX, +2376XXXXXXXX
 */
export function validateCameroonPhone(phone: string): boolean {
  const cleanPhone = phone.replace(/\s/g, "");
  const phoneRegex = /^(?:\+237|237)?6[25-9]\d{7}$/;
  return phoneRegex.test(cleanPhone);
}

export function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
