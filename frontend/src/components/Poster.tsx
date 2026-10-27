import { Tv } from 'lucide-react'
import { useState } from 'react'

type PosterProps = {
  url: string | null
  /** Empty when the title is already shown next to the image. */
  alt: string
  title: string
  className?: string
}

/**
 * 2:3 poster with a neutral placeholder when the provider has no image or the
 * image fails to load. The placeholder never pretends to be artwork.
 */
export function Poster({ url, alt, title, className = '' }: PosterProps) {
  const [failed, setFailed] = useState(false)
  const showImage = url && !failed

  return (
    <div className={`relative aspect-[2/3] overflow-hidden rounded-card bg-surface-raised ${className}`}>
      {showImage ? (
        <img
          src={url}
          alt={alt}
          loading="lazy"
          decoding="async"
          onError={() => setFailed(true)}
          className="size-full object-cover"
        />
      ) : (
        <div
          role={alt ? 'img' : undefined}
          aria-label={alt || undefined}
          aria-hidden={alt ? undefined : true}
          className="flex size-full flex-col items-center justify-center gap-2 p-3 text-center text-ink-muted"
        >
          <Tv aria-hidden="true" className="size-8 opacity-60" />
          <span className="line-clamp-3 text-xs font-medium">{title}</span>
        </div>
      )}
    </div>
  )
}
