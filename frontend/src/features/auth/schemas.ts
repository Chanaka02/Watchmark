import { z } from 'zod'

/**
 * Mirrors the backend PasswordPolicy so users get instant feedback. The backend
 * remains the authority: these checks are only a convenience.
 */
export const PASSWORD_MIN_LENGTH = 15
export const PASSWORD_MAX_BYTES = 72

const utf8 = new TextEncoder()

const newPassword = z
  .string()
  // Count characters (code points), not UTF-16 units, like the backend does.
  .refine((value) => [...value].length >= PASSWORD_MIN_LENGTH, `Use at least ${PASSWORD_MIN_LENGTH} characters.`)
  .refine((value) => utf8.encode(value).length <= PASSWORD_MAX_BYTES, 'This password is too long.')

const email = z.string().trim().min(1, 'Enter your email address.').pipe(z.email('Enter a valid email address.'))

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Enter your email address.'),
  password: z.string().min(1, 'Enter your password.'),
})

export const registerSchema = z.object({
  displayName: z.string().trim().min(1, 'Enter a display name.').max(50, 'Use at most 50 characters.'),
  email,
  password: newPassword,
})

export type LoginForm = z.infer<typeof loginSchema>
export type RegisterForm = z.infer<typeof registerSchema>

/** The browser's IANA time zone, used as the account default. */
export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}
