import { Clapperboard } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'

import { EmptyState } from '../components/EmptyState'
import { MockDataNotice } from '../features/catalog/MockDataNotice'
import { ShowResults } from '../features/catalog/ShowResults'
import { discoverLists, type DiscoverListSlug } from '../features/catalog/types'
import { useDiscover } from '../features/catalog/useCatalog'

function listFrom(value: string | null): DiscoverListSlug {
  return discoverLists.find((list) => list.slug === value)?.slug ?? 'trending'
}

export function DiscoverPage() {
  const [params] = useSearchParams()
  const list = listFrom(params.get('list'))
  const query = useDiscover(list)

  return (
    <section className="space-y-6">
      <h1 className="text-3xl font-bold tracking-tight">Discover</h1>
      <MockDataNotice />
      <nav aria-label="Lists" className="flex gap-2 overflow-x-auto pb-1">
        {discoverLists.map((item) => (
          <Link
            key={item.slug}
            to={`/discover?list=${item.slug}`}
            aria-current={item.slug === list ? 'page' : undefined}
            className={`shrink-0 rounded-full border px-4 py-1.5 text-sm font-medium transition-colors ${
              item.slug === list
                ? 'border-accent bg-accent text-accent-ink'
                : 'border-line text-ink-muted hover:border-ink-muted hover:text-ink'
            }`}
          >
            {item.label}
          </Link>
        ))}
      </nav>
      <ShowResults
        query={query}
        errorTitle="Could not load shows"
        empty={
          <EmptyState icon={Clapperboard} title="Nothing here right now">
            This list is empty at the moment. Try another list.
          </EmptyState>
        }
      />
    </section>
  )
}
