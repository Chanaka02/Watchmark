import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'

import { ApiError } from '../../lib/api'

/**
 * Shows a failed request on the form: backend field errors go next to their
 * inputs, everything else becomes one form-level message.
 */
export function applyServerError<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>, fields: Path<T>[]) {
  if (error instanceof ApiError) {
    const known = error.fieldErrors.filter((fieldError) => fields.includes(fieldError.field as Path<T>))
    if (known.length > 0) {
      for (const fieldError of known) {
        setError(fieldError.field as Path<T>, { type: 'server', message: capitalise(fieldError.message) })
      }
      return
    }
    setError('root.server', { type: error.type, message: describe(error) })
    return
  }
  setError('root.server', { type: 'unknown', message: 'Something went wrong. Please try again.' })
}

function describe(error: ApiError): string {
  if (error.type === 'rate-limited' && error.retryAfterSeconds) {
    const minutes = Math.ceil(error.retryAfterSeconds / 60)
    return `Too many attempts. Try again in ${minutes} minute${minutes === 1 ? '' : 's'}.`
  }
  return error.message
}

function capitalise(message: string): string {
  return message.charAt(0).toUpperCase() + message.slice(1) + (message.endsWith('.') ? '' : '.')
}
