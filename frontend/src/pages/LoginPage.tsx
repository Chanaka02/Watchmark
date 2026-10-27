import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'

import { Button } from '../components/Button'
import { ErrorMessage } from '../components/ErrorMessage'
import { TextField } from '../components/TextField'
import { safeNextPath } from '../features/auth/redirects'
import { loginSchema, type LoginForm } from '../features/auth/schemas'
import { applyServerError } from '../features/auth/serverErrors'
import { useLogin } from '../features/auth/useAuth'
import { AuthCard } from './AuthCard'

export function LoginPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNextPath(params.get('next'))
  const loginMutation = useLogin()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) })

  const onSubmit = handleSubmit((values) =>
    loginMutation.mutateAsync(values).then(
      () => navigate(next, { replace: true }),
      (error) => applyServerError(error, setError, ['email', 'password']),
    ),
  )

  return (
    <AuthCard
      title="Sign in"
      footer={
        <>
          New to Watchmark?{' '}
          <Link to={next === '/' ? '/register' : `/register?next=${encodeURIComponent(next)}`} className="text-accent hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form noValidate onSubmit={onSubmit} className="space-y-5">
        {errors.root?.server && <ErrorMessage title={errors.root.server.message ?? 'Sign-in failed.'} />}
        <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" pending={loginMutation.isPending} className="w-full">
          Sign in
        </Button>
      </form>
    </AuthCard>
  )
}
