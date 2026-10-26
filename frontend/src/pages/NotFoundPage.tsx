import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-bold">Page not found</h1>
      <Link to="/" className="text-accent underline-offset-4 hover:underline">
        Back to home
      </Link>
    </section>
  )
}
