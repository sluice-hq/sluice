'use client';

import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { AuthShell, PasswordInput } from '@/components/auth/AuthShell';

type SignupFields = {
  email: string;
  password: string;
  projectName: string;
};

type FieldErrors = Partial<Record<keyof SignupFields, string>>;

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validate(fields: SignupFields): FieldErrors {
  const errors: FieldErrors = {};

  if (!fields.email) {
    errors.email = 'Enter your email address.';
  } else if (!emailPattern.test(fields.email)) {
    errors.email = 'Enter a valid email address.';
  }

  if (!fields.password) {
    errors.password = 'Create a password.';
  } else if (fields.password.length < 12) {
    errors.password = 'Use at least 12 characters.';
  } else if (fields.password.length > 128) {
    errors.password = 'Use no more than 128 characters.';
  }

  if (!fields.projectName) {
    errors.projectName = 'Name your first project.';
  } else if (fields.projectName.length > 100) {
    errors.projectName = 'Use no more than 100 characters.';
  }

  return errors;
}

function formAlert(response: Response, body: unknown): string {
  if (response.status === 409) {
    return 'An account with this email already exists. Sign in instead, or use a different email.';
  }

  if (response.status >= 500) {
    return 'We could not create your account right now. Please try again in a moment.';
  }

  if (body && typeof body === 'object' && 'detail' in body && typeof body.detail === 'string') {
    return body.detail;
  }

  return 'We could not create your account. Check the details above and try again.';
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

export default function SignupPage() {
  const router = useRouter();
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [alert, setAlert] = useState('');
  const [busy, setBusy] = useState(false);

  function clearFieldError(field: keyof SignupFields) {
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
    const fields: SignupFields = {
      email: String(data.get('email') ?? '').trim(),
      password: String(data.get('password') ?? ''),
      projectName: String(data.get('projectName') ?? '').trim(),
    };
    const errors = validate(fields);

    setFieldErrors(errors);
    setAlert('');
    if (Object.keys(errors).length > 0) return;

    setBusy(true);
    try {
      const response = await fetch('/api/session/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(fields),
      });
      const body = await responseBody(response);

      if (!response.ok) {
        setAlert(formAlert(response, body));
        return;
      }

      router.replace(`/verify-email?email=${encodeURIComponent(fields.email)}`);
      router.refresh();
    } catch {
      setAlert('We could not reach Sluice. Check your connection and try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell eyebrow="START BUILDING" title="Create your workspace" subtitle="Set up your first project for media-processing APIs and pipelines.">
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
            <p id="email-help" className="text-xs text-muted-foreground">Use an address you can access for account updates.</p>
            {fieldErrors.email && <p id="email-error" className="text-sm text-destructive">{fieldErrors.email}</p>}
          </div>

          <div className="space-y-2">
            <label htmlFor="password" className="text-sm font-medium">Password</label>
            <PasswordInput
              id="password"
              autoComplete="new-password"
              minLength={12}
              maxLength={128}
              invalid={Boolean(fieldErrors.password)}
              describedBy={fieldErrors.password ? 'password-help password-error' : 'password-help'}
              onChange={() => clearFieldError('password')}
            />
            <p id="password-help" className="text-xs text-muted-foreground">Use 12 to 128 characters.</p>
            {fieldErrors.password && <p id="password-error" className="text-sm text-destructive">{fieldErrors.password}</p>}
          </div>

          <div className="space-y-2">
            <label htmlFor="projectName" className="text-sm font-medium">First project name</label>
            <Input
              id="projectName"
              name="projectName"
              maxLength={100}
              placeholder="Product media"
              aria-invalid={Boolean(fieldErrors.projectName)}
              aria-describedby={fieldErrors.projectName ? 'project-name-help project-name-error' : 'project-name-help'}
              onChange={() => clearFieldError('projectName')}
              className="h-11"
            />
            <p id="project-name-help" className="text-xs text-muted-foreground">You can create more projects later. Use up to 100 characters.</p>
            {fieldErrors.projectName && <p id="project-name-error" className="text-sm text-destructive">{fieldErrors.projectName}</p>}
          </div>

          <Button type="submit" className="h-11 w-full text-sm font-semibold" disabled={busy} aria-busy={busy}>
            {busy ? 'Creating account…' : 'Create account'}
          </Button>
          <p className="text-center text-sm text-muted-foreground">Already registered? <Link className="text-primary underline-offset-4 hover:underline" href="/login">Sign in</Link></p>
        </form>
    </AuthShell>
  );
}
