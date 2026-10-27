import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'

import { Button } from '../components/Button'
import { ErrorMessage } from '../components/ErrorMessage'
import { TextField } from '../components/TextField'
import { ApiError } from '../lib/api'
import { safeNextPath } from '../features/auth/redirects'
import { browserTimeZone, PASSWORD_MIN_LENGTH, registerSchema, type RegisterForm } from '../features/auth/schemas'
import { applyServerError } from '../features/auth/serverErrors'
import { useRegister } from '../features/auth/useAuth'
import { AuthCard } from './AuthCard'

export function RegisterPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNextPath(params.get('next'))
  const registerMutation = useRegister()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RegisterForm>({ resolver: zodResolver(registerSchema) })

  const onSubmit = handleSubmit((values) =>
    registerMutation.mutateAsync({ ...values, timeZone: browserTimeZone() }).then(
      () => navigate(next, { replace: true }),
      (error) => {
        if (error instanceof ApiError && error.type === 'email-taken') {
          setError('email', { type: 'server', message: 'An account with this email already exists.' })
          return
        }
        applyServerError(error, setError, ['displayName', 'email', 'password'])
      },
    ),
  )

  return (
    <AuthCard
      title="Create your account"
      footer={
        <>
          Already have an account?{' '}
          <Link to={next === '/' ? '/login' : `/login?next=${encodeURIComponent(next)}`} className="text-accent hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form noValidate onSubmit={onSubmit} className="space-y-5">
        {errors.root?.server && <ErrorMessage title={errors.root.server.message ?? 'Registration failed.'} />}
        <TextField
          label="Display name"
          autoComplete="nickname"
          maxLength={50}
          error={errors.displayName?.message}
          {...register('displayName')}
        />
        <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          hint={`At least ${PASSWORD_MIN_LENGTH} characters. A short sentence works well.`}
          error={errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" pending={registerMutation.isPending} className="w-full">
          Create account
        </Button>
      </form>
    </AuthCard>
  )
}
