'use client';

import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { AuthShell, PasswordInput } from '@/components/auth/AuthShell';

type LoginFields = {
  email: string;
  password: string;
};

type FieldErrors = Partial<Record<keyof LoginFields, string>>;

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validate(fields: LoginFields): FieldErrors {
  const errors: FieldErrors = {};

  if (!fields.email) {
    errors.email = 'Enter your email address.';
  } else if (!emailPattern.test(fields.email)) {
    errors.email = 'Enter a valid email address.';
  }

  if (!fields.password) {
    errors.password = 'Enter your password.';
  } else if (fields.password.length > 128) {
    errors.password = 'Use no more than 128 characters.';
  }

  return errors;
}

async function responseBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return undefined;

  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

function formAlert(response: Response, body: unknown): string {
  if (response.status === 401) return 'The email or password is incorrect.';
  if (response.status >= 500) return 'Sign in is temporarily unavailable. Please try again in a moment.';
  if (body && typeof body === 'object' && 'detail' in body && typeof body.detail === 'string') {
    return body.detail;
  }
  return 'We could not sign you in. Check your details and try again.';
}

export default function LoginPage() {
  const router = useRouter();
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [alert, setAlert] = useState('');
  const [busy, setBusy] = useState(false);

  function clearFieldError(field: keyof LoginFields) {
    setFieldErrors((errors) => {
      if (!errors[field]) return errors;
      const next = { ...errors };
      delete next[field];
      return next;
    });
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;

    const data = new FormData(event.currentTarget);
    const fields: LoginFields = {
      email: String(data.get('email') ?? '').trim(),
      password: String(data.get('password') ?? ''),
    };
    const errors = validate(fields);

    setFieldErrors(errors);
    setAlert('');
    if (Object.keys(errors).length > 0) return;

    setBusy(true);
    try {
      const response = await fetch('/api/session/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(fields),
      });
      const body = await responseBody(response);

      if (!response.ok) {
        setAlert(formAlert(response, body));
        return;
      }

      router.replace('/');
      router.refresh();
    } catch {
      setAlert('We could not reach Sluice. Check your connection and try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell eyebrow="WELCOME BACK" title="Sign in to Sluice" subtitle="Manage your media pipelines, runs, and developer access in one place.">
      <form noValidate onSubmit={submit} className="mt-9 space-y-5">
        {alert && (
          <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2.5 text-sm text-destructive">
            {alert}
          </div>
        )}

        <div className="space-y-2">
          <label htmlFor="email" className="text-sm font-medium">Email address</label>
          <Input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            aria-invalid={Boolean(fieldErrors.email)}
            aria-describedby={fieldErrors.email ? 'email-help email-error' : 'email-help'}
            onChange={() => clearFieldError('email')}
            className="h-11"
          />
          <p id="email-help" className="text-xs text-muted-foreground">Use the email associated with your Sluice account.</p>
          {fieldErrors.email && <p id="email-error" className="text-sm text-destructive">{fieldErrors.email}</p>}
        </div>

        <div className="space-y-2">
          <label htmlFor="password" className="text-sm font-medium">Password</label>
          <PasswordInput
            id="password"
            autoComplete="current-password"
            maxLength={128}
            invalid={Boolean(fieldErrors.password)}
            describedBy={fieldErrors.password ? 'password-help password-error' : 'password-help'}
            onChange={() => clearFieldError('password')}
          />
          <p id="password-help" className="text-xs text-muted-foreground">Passwords are case-sensitive.</p>
          {fieldErrors.password && <p id="password-error" className="text-sm text-destructive">{fieldErrors.password}</p>}
        </div>

        <Button type="submit" className="h-11 w-full text-sm font-semibold" disabled={busy} aria-busy={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </Button>
        <p className="text-center text-sm text-muted-foreground">New to Sluice? <Link className="text-primary underline-offset-4 hover:underline" href="/signup">Create an account</Link></p>
      </form>
    </AuthShell>
  );
}
