export type ButtonVariant = 'primary' | 'secondary' | 'ghost'

const base =
  'inline-flex h-10 items-center justify-center gap-2 rounded-lg px-4 text-sm font-semibold transition-colors'

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-accent-ink hover:bg-accent/90',
  secondary: 'border border-line bg-surface-raised text-ink hover:bg-line',
  ghost: 'text-ink-muted hover:bg-surface-raised hover:text-ink',
}

/** Classes shared by <Button> and links styled as buttons. */
export function buttonClasses(variant: ButtonVariant = 'primary') {
  return `${base} ${variants[variant]}`
}
