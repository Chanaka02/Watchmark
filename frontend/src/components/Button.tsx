import { LoaderCircle } from 'lucide-react'
import type { ButtonHTMLAttributes } from 'react'

import { buttonClasses, type ButtonVariant } from './buttonStyles'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  /** Shows a spinner and disables the button while an action is running. */
  pending?: boolean
}

export function Button({ variant = 'primary', pending = false, disabled, className = '', children, ...props }: ButtonProps) {
  return (
    <button
      {...props}
      disabled={disabled || pending}
      aria-busy={pending || undefined}
      className={`${buttonClasses(variant)} disabled:cursor-not-allowed disabled:opacity-60 ${className}`}
    >
      {pending && <LoaderCircle aria-hidden="true" className="size-4 animate-spin" />}
      {children}
    </button>
  )
}
