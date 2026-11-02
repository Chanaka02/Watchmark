/** "S2 E3" (or "Special 2" for season 0). */
export function episodeLabel(season: number, episode: number) {
  return season === 0 ? `Special ${episode}` : `S${season} E${episode}`
}

/** Message for a watch, season or up-to result. */
export function watchedMessage(result: { created: number; alreadyWatched: number; notEligible: number }, single?: string) {
  if (result.created === 0) {
    return result.notEligible > 0 && result.alreadyWatched === 0
      ? 'Nothing to mark: these episodes have not aired yet.'
      : 'Nothing to mark: every aired episode is already watched.'
  }
  const what = single ?? `${result.created} episode${result.created === 1 ? '' : 's'}`
  const skipped = [
    result.alreadyWatched > 0 ? `${result.alreadyWatched} already watched` : null,
    result.notEligible > 0 ? `${result.notEligible} not aired yet` : null,
  ].filter(Boolean)
  return `Marked ${what} watched.${skipped.length > 0 ? ` Skipped ${skipped.join(', ')}.` : ''}`
}
