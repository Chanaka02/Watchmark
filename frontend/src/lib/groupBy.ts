/** Groups items by key, keeping first-seen order of keys and items. */
export function groupBy<T>(items: T[], key: (item: T) => string): Map<string, T[]> {
  const groups = new Map<string, T[]>()
  for (const item of items) {
    const group = groups.get(key(item))
    if (group) {
      group.push(item)
    } else {
      groups.set(key(item), [item])
    }
  }
  return groups
}
