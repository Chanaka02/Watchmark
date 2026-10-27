import { useCatalogSource } from './useCatalog'

/**
 * Data-source attribution required by TMDB's terms: their logo (less prominent
 * than Watchmark's own branding) and the exact notice text.
 */
export function Attribution() {
  const source = useCatalogSource()
  if (source.data?.provider !== 'tmdb') {
    return null
  }
  return (
    <div className="flex flex-col items-center gap-2 sm:flex-row sm:justify-center">
      <a href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer" className="rounded">
        <img src="/tmdb-logo.svg" alt="TMDB" width={92} height={12} className="h-3 w-auto" />
      </a>
      <p>This product uses the TMDB API but is not endorsed or certified by TMDB.</p>
    </div>
  )
}
