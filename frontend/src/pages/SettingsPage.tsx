import { zodResolver } from '@hookform/resolvers/zod'
import { Trash2 } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'

import { Button } from '../components/Button'
import { Dialog } from '../components/Dialog'
import { ErrorMessage } from '../components/ErrorMessage'
import { PageLoader } from '../components/PageLoader'
import { TextField } from '../components/TextField'
import { useChangePassword, useDeleteAccount, useUpdateProfile } from '../features/account/useAccount'
import type { User } from '../features/auth/api'
import {
  changePasswordSchema,
  PASSWORD_MIN_LENGTH,
  profileSchema,
  timeZoneOptions,
  type ChangePasswordForm,
  type ProfileForm,
} from '../features/auth/schemas'
import { applyServerError } from '../features/auth/serverErrors'
import { useCurrentUser } from '../features/auth/useAuth'
import { ApiError } from '../lib/api'

export function SettingsPage() {
  const currentUser = useCurrentUser()

  if (currentUser.isPending || !currentUser.data) {
    return <PageLoader />
  }
  return (
    <div className="max-w-2xl space-y-10">
      <h1 className="text-3xl font-bold tracking-tight">Settings</h1>
      <ProfileSection user={currentUser.data} />
      <PasswordSection />
      <DeleteAccountSection />
    </div>
  )
}

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="space-y-4 rounded-card border border-line bg-surface p-4 sm:p-6">
      <h2 id={id} className="text-xl font-semibold">
        {title}
      </h2>
      {children}
    </section>
  )
}

/** Polite confirmation next to a form's button. */
function Saved({ message }: { message: string | null }) {
  return (
    <p role="status" className="text-sm text-success">
      {message}
    </p>
  )
}

function ProfileSection({ user }: { user: User }) {
  const update = useUpdateProfile()
  const [saved, setSaved] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isDirty },
    reset,
  } = useForm<ProfileForm>({
    resolver: zodResolver(profileSchema),
    defaultValues: { displayName: user.displayName, timeZone: user.timeZone },
  })

  const onSubmit = handleSubmit((values) => {
    setSaved(null)
    return update.mutateAsync(values).then(
      (updated) => {
        reset({ displayName: updated.displayName, timeZone: updated.timeZone })
        setSaved('Profile saved.')
      },
      (error) => applyServerError(error, setError, ['displayName', 'timeZone']),
    )
  })

  return (
    <Section id="profile-heading" title="Profile">
      <form noValidate onSubmit={onSubmit} className="space-y-5">
        {errors.root?.server && <ErrorMessage title={errors.root.server.message ?? 'Could not save your profile.'} />}
        <p className="text-sm text-ink-muted">Signed in as {user.email}.</p>
        <TextField label="Display name" autoComplete="nickname" maxLength={50} error={errors.displayName?.message} {...register('displayName')} />
        <div className="space-y-1.5">
          <label htmlFor="time-zone" className="block text-sm font-medium">
            Time zone
          </label>
          <select
            id="time-zone"
            aria-describedby="time-zone-hint"
            className="h-11 w-full rounded-lg border border-line bg-surface px-3 text-ink"
            {...register('timeZone')}
          >
            {timeZoneOptions(user.timeZone).map((zone) => (
              <option key={zone} value={zone}>
                {zone.replaceAll('_', ' ')}
              </option>
            ))}
          </select>
          <p id="time-zone-hint" className="text-xs text-ink-muted">
            Decides which day is "today" for air dates, progress and your history.
          </p>
          {errors.timeZone && <p className="text-sm text-danger">{errors.timeZone.message}</p>}
        </div>
        <div className="flex flex-wrap items-center gap-4">
          <Button type="submit" pending={update.isPending} disabled={!isDirty}>
            Save profile
          </Button>
          <Saved message={saved} />
        </div>
      </form>
    </Section>
  )
}

function PasswordSection() {
  const change = useChangePassword()
  const [saved, setSaved] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<ChangePasswordForm>({ resolver: zodResolver(changePasswordSchema) })

  const onSubmit = handleSubmit((values) => {
    setSaved(null)
    return change.mutateAsync(values).then(
      () => {
        reset()
        setSaved('Password changed. Other devices were signed out.')
      },
      (error) => {
        if (error instanceof ApiError && error.type === 'password-incorrect') {
          setError('currentPassword', { type: 'server', message: 'This password is incorrect.' })
          return
        }
        applyServerError(error, setError, ['currentPassword', 'newPassword'])
      },
    )
  })

  return (
    <Section id="password-heading" title="Password">
      <form noValidate onSubmit={onSubmit} className="space-y-5">
        {errors.root?.server && <ErrorMessage title={errors.root.server.message ?? 'Could not change your password.'} />}
        <TextField
          label="Current password"
          type="password"
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          {...register('currentPassword')}
        />
        <TextField
          label="New password"
          type="password"
          autoComplete="new-password"
          hint={`At least ${PASSWORD_MIN_LENGTH} characters. A short sentence works well.`}
          error={errors.newPassword?.message}
          {...register('newPassword')}
        />
        <div className="flex flex-wrap items-center gap-4">
          <Button type="submit" pending={change.isPending}>
            Change password
          </Button>
          <Saved message={saved} />
        </div>
      </form>
    </Section>
  )
}

function DeleteAccountSection() {
  const [open, setOpen] = useState(false)
  return (
    <Section id="delete-heading" title="Delete account">
      <p className="text-sm text-ink-muted">
        Permanently deletes your account, library, ratings, notes and viewing history. This cannot be undone.
      </p>
      <Button variant="secondary" onClick={() => setOpen(true)}>
        <Trash2 aria-hidden="true" className="size-4" />
        Delete my account
      </Button>
      <DeleteAccountDialog open={open} onClose={() => setOpen(false)} />
    </Section>
  )
}

function DeleteAccountDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate()
  const remove = useDeleteAccount()
  const [password, setPassword] = useState('')
  const incorrect = remove.error instanceof ApiError && remove.error.type === 'password-incorrect'

  function close() {
    setPassword('')
    remove.reset()
    onClose()
  }

  return (
    <Dialog
      open={open}
      onClose={close}
      title="Delete your account permanently?"
      actions={
        <>
          <Button variant="secondary" onClick={close}>
            Cancel
          </Button>
          <Button
            pending={remove.isPending}
            disabled={password.length === 0}
            onClick={() => remove.mutate(password, { onSuccess: () => navigate('/', { replace: true }) })}
          >
            Delete account
          </Button>
        </>
      }
    >
      <p>Everything in your account is deleted immediately and every device is signed out.</p>
      <TextField
        label="Enter your password to confirm"
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        error={incorrect ? 'This password is incorrect.' : undefined}
      />
      {remove.isError && !incorrect && <ErrorMessage title="Could not delete your account">{remove.error.message}</ErrorMessage>}
    </Dialog>
  )
}
