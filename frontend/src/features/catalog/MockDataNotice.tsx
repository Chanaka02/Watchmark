import { FlaskConical } from 'lucide-react'

import { useCatalogSource } from './useCatalog'

/** Makes clear when the catalogue is fictional development data. */
export function MockDataNotice() {
  const source = useCatalogSource()
  if (!source.data?.mock) {
    return null
  }
  return (
    <p className="flex items-center gap-2 rounded-lg border border-accent/40 bg-accent/10 px-3 py-2 text-sm">
      <FlaskConical aria-hidden="true" className="size-4 shrink-0 text-accent" />
      <span>
        <strong>Mock data:</strong> these shows are fictional, for development. Configure a TMDB token to see real
        series.
      </span>
    </p>
  )
}
