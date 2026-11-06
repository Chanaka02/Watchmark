import { useState } from 'react'

import { formatMonth } from '../../lib/calendar'
import { formatDate } from '../../lib/dates'
import type { Stats } from './types'
import { formatMinutes } from './useStats'

type Point = Stats['activity']['points'][number]

/**
 * Viewings per day or month as single-series bars (accent colour, no legend:
 * the heading names the series). Bars are decorative for assistive tech and
 * show a tooltip on mouse hover; keyboard and screen reader users get the
 * same numbers as a table, plus a one-line summary.
 */
export function ActivityChart({ activity }: { activity: Stats['activity'] }) {
  const [active, setActive] = useState<number | null>(null)
  const { points, unit } = activity
  const max = Math.max(1, ...points.map((p) => p.watches))
  const label = (p: Point) => (unit === 'DAY' ? formatDate(p.period) : formatMonth(p.period))
  const total = points.reduce((sum, p) => sum + p.watches, 0)
  const busiest = points.reduce<Point | null>((best, p) => (p.watches > (best?.watches ?? 0) ? p : best), null)
  // Label only the ends of the axis (and the middle for long ranges), never every bar.
  const ticks = [...new Set([0, ...(points.length > 10 ? [Math.floor((points.length - 1) / 2)] : []), points.length - 1])]

  if (points.length === 0) {
    return <p className="text-sm text-ink-muted">No viewings yet.</p>
  }

  return (
    <div className="space-y-3">
      <div
        role="img"
        aria-label={`${total} viewings${busiest ? `; most on ${label(busiest)} (${busiest.watches})` : ''}. Exact numbers are in the table below.`}
        className="relative"
      >
        <div className="flex h-40 items-end gap-0.5 border-b border-line" onMouseLeave={() => setActive(null)}>
          {points.map((point, i) => (
            <div
              key={point.period}
              className="flex h-full min-w-0 flex-1 items-end"
              onMouseEnter={() => setActive(i)}
              aria-hidden="true"
            >
              <div
                className={`w-full rounded-t ${point.watches === 0 ? '' : active === i ? 'bg-accent' : 'bg-accent/80'}`}
                style={{ height: point.watches === 0 ? 0 : `max(4px, ${(point.watches / max) * 100}%)` }}
              />
            </div>
          ))}
        </div>
        {active != null && (
          <div
            aria-hidden="true"
            className="pointer-events-none absolute -top-2 z-10 -translate-x-1/2 -translate-y-full rounded-md border border-line bg-surface-raised px-2 py-1 text-xs whitespace-nowrap shadow"
            style={{ left: `${((active + 0.5) / points.length) * 100}%` }}
          >
            <span className="font-medium text-ink">{label(points[active])}</span>
            <span className="block text-ink-muted">
              {points[active].watches} viewing{points[active].watches === 1 ? '' : 's'} ·{' '}
              {formatMinutes(points[active].minutes)}
            </span>
          </div>
        )}
        <div aria-hidden="true" className="mt-1 flex justify-between gap-2 text-xs text-ink-muted">
          {ticks.map((i) => (
            <span key={i} className="whitespace-nowrap">
              {label(points[i])}
            </span>
          ))}
        </div>
      </div>

      <details className="text-sm">
        <summary className="cursor-pointer text-ink-muted hover:text-ink">Show as a table</summary>
        <table className="mt-2 w-full text-left">
          <caption className="sr-only">Viewings per {unit === 'DAY' ? 'day' : 'month'}</caption>
          <thead>
            <tr className="text-xs text-ink-muted">
              <th scope="col" className="py-1 font-medium">
                {unit === 'DAY' ? 'Day' : 'Month'}
              </th>
              <th scope="col" className="py-1 text-right font-medium">
                Viewings
              </th>
              <th scope="col" className="py-1 text-right font-medium">
                Time
              </th>
            </tr>
          </thead>
          <tbody>
            {points.map((point) => (
              <tr key={point.period} className="border-t border-line">
                <th scope="row" className="py-1 font-normal">
                  {label(point)}
                </th>
                <td className="py-1 text-right tabular-nums">{point.watches}</td>
                <td className="py-1 text-right tabular-nums">{formatMinutes(point.minutes)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  )
}
