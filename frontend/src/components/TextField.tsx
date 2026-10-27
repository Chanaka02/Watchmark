import { Eye, EyeOff } from 'lucide-react'
import { useId, useState, type InputHTMLAttributes, type Ref } from 'react'

type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string
  error?: string
  hint?: string
  ref?: Ref<HTMLInputElement>
}

/** Labelled input with accessible error and hint text. */
export function TextField({ label, error, hint, id, type = 'text', ...props }: TextFieldProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const hintId = `${inputId}-hint`
  const errorId = `${inputId}-error`
  const [revealed, setRevealed] = useState(false)
  const isPassword = type === 'password'

  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ') || undefined

  return (
    <div className="space-y-1.5">
      <label htmlFor={inputId} className="block text-sm font-medium">
        {label}
      </label>
      <div className="relative">
        <input
          {...props}
          id={inputId}
          type={isPassword && revealed ? 'text' : type}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`h-11 w-full rounded-lg border bg-surface px-3 text-ink placeholder:text-ink-muted/60 ${
            error ? 'border-danger' : 'border-line'
          } ${isPassword ? 'pr-11' : ''}`}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setRevealed((value) => !value)}
            aria-label={revealed ? 'Hide password' : 'Show password'}
            aria-pressed={revealed}
            className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-lg text-ink-muted hover:text-ink"
          >
            {revealed ? <EyeOff aria-hidden="true" className="size-4" /> : <Eye aria-hidden="true" className="size-4" />}
          </button>
        )}
      </div>
      {hint && !error && (
        <p id={hintId} className="text-xs text-ink-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
