'use client';

import { Eye, EyeOff, ShieldCheck, Sparkles } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { SluiceBrand } from '@/components/brand/SluiceBrand';
import { Input } from '@/components/ui/input';

export function AuthShell({
  eyebrow,
  title,
  subtitle,
  children,
}: {
  eyebrow: string;
  title: string;
  subtitle: string;
  children: ReactNode;
}) {
  return (
    <main className="relative grid min-h-screen overflow-hidden bg-background lg:grid-cols-[minmax(0,1fr)_minmax(28rem,0.9fr)]">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_80%_0%,rgb(35_149_255_/_0.16),transparent_28rem)]" />
      <section className="relative hidden border-r border-border/70 px-10 py-10 lg:flex lg:flex-col xl:px-[10vw]">
        <SluiceBrand href="/" size="medium" priority />
        <div className="my-auto max-w-xl pb-14 pt-24">
          <span className="inline-flex items-center gap-2 rounded-full border border-primary/25 bg-primary/10 px-3 py-1 text-xs font-semibold tracking-wide text-primary">
            <Sparkles className="size-3.5" aria-hidden="true" />
            MEDIA INFRASTRUCTURE, SIMPLIFIED
          </span>
          <h2 className="mt-7 text-4xl font-semibold leading-tight tracking-tight xl:text-5xl">Build reliable media pipelines your application can call.</h2>
          <p className="mt-5 max-w-lg text-base leading-7 text-muted-foreground">Create versioned processing flows, inspect every run, and give your team one clear control plane.</p>
          <div className="mt-10 rounded-2xl border border-border bg-card/80 p-5 shadow-[0_24px_80px_rgb(0_0_0_/_0.22)] backdrop-blur">
            <div className="flex items-center gap-3">
              <div className="grid size-9 place-items-center rounded-xl bg-primary/15 text-primary"><ShieldCheck className="size-5" aria-hidden="true" /></div>
              <div>
                <p className="text-sm font-semibold">Developer-first by design</p>
                <p className="mt-0.5 text-sm text-muted-foreground">Secure dashboard sessions and project-scoped API access.</p>
              </div>
            </div>
          </div>
        </div>
        <p className="text-xs text-muted-foreground">Sluice developer platform</p>
      </section>

      <section className="relative flex items-center justify-center px-5 py-8 sm:px-8 lg:px-12">
        <div className="w-full max-w-md">
          <SluiceBrand href="/" size="small" priority className="mb-12 lg:hidden" />
          <p className="text-sm font-semibold text-primary">{eyebrow}</p>
          <h1 id="auth-heading" className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
          <p className="mt-3 text-sm leading-6 text-muted-foreground sm:text-base">{subtitle}</p>
          {children}
        </div>
      </section>
    </main>
  );
}

export function PasswordInput({
  id,
  name = 'password',
  autoComplete,
  maxLength,
  minLength,
  invalid,
  describedBy,
  onChange,
}: {
  id: string;
  name?: string;
  autoComplete: string;
  maxLength: number;
  minLength?: number;
  invalid: boolean;
  describedBy: string;
  onChange: () => void;
}) {
  const [visible, setVisible] = useState(false);

  return (
    <div className="relative">
      <Input
        id={id}
        name={name}
        type={visible ? 'text' : 'password'}
        autoComplete={autoComplete}
        minLength={minLength}
        maxLength={maxLength}
        aria-invalid={invalid}
        aria-describedby={describedBy}
        onChange={onChange}
        className="h-11 pr-12"
      />
      <button
        type="button"
        onClick={() => setVisible((current) => !current)}
        className="absolute inset-y-0 right-0 grid w-11 place-items-center rounded-r-lg text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        aria-label={visible ? 'Hide password' : 'Show password'}
      >
        {visible ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
      </button>
    </div>
  );
}
