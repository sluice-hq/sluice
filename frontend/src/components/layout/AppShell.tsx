'use client';

import Image from 'next/image';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Activity,
  Boxes,
  FileVideo,
  LayoutDashboard,
  LogOut,
  Menu,
  Play,
  Settings,
  Shield,
  X,
} from 'lucide-react';
import { csrfFetch } from '@/lib/csrf';
import { cn } from '@/lib/utils';

const navigation = [
  { name: 'Overview', href: '/', icon: LayoutDashboard },
  { name: 'Assets', href: '/assets', icon: FileVideo },
  { name: 'Runs', href: '/jobs', icon: Activity },
  { name: 'Pipelines', href: '/pipelines', icon: Play },
  { name: 'Processor Market', href: '/processors', icon: Boxes },
  { name: 'Governance', href: '/governance', icon: Shield },
  { name: 'Settings', href: '/settings', icon: Settings },
];

export default function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const publicRoute = pathname === '/login' || pathname === '/signup';
  const { data: session, isLoading, isError } = useQuery<Session>({
    queryKey: ['session'],
    queryFn: async () => {
      const response = await fetch('/api/session', { cache: 'no-store' });
      if (!response.ok) throw new Error('Not signed in');
      return response.json();
    },
    enabled: !publicRoute,
    retry: false,
    staleTime: 30_000,
  });

  useEffect(() => {
    if (!publicRoute && isError) router.replace('/login');
  }, [isError, publicRoute, router]);

  if (publicRoute) return children;
  if (isLoading || !session) {
    return <div className="grid min-h-screen place-items-center bg-background text-sm text-muted-foreground">Loading Sluice…</div>;
  }

  const selectedProject = session.projects.find((project) => project.id === session.selectedProjectId);

  async function selectProject(projectId: string) {
    await csrfFetch('/api/session/project', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ projectId }),
    });
    window.location.reload();
  }

  async function logout() {
    await csrfFetch('/api/session/logout', { method: 'POST' });
    router.replace('/login');
    router.refresh();
  }

  const navigationItems = navigation.map((item) => {
    const isActive = pathname === item.href || (pathname.startsWith(item.href) && item.href !== '/');
    return (
      <Link
        key={item.name}
        href={item.href}
        onClick={() => setMobileMenuOpen(false)}
        className={cn(
          'group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring focus-visible:ring-offset-2 focus-visible:ring-offset-sidebar',
          isActive
            ? 'bg-sidebar-primary text-sidebar-primary-foreground shadow-[0_8px_24px_rgb(35_149_255_/_0.22)]'
            : 'text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
        )}
      >
        <item.icon className="size-4 shrink-0" aria-hidden="true" />
        {item.name}
      </Link>
    );
  });

  return (
    <div className="min-h-screen bg-background text-foreground antialiased selection:bg-primary/30">
      <a
        href="#main-content"
        className="sr-only fixed left-4 top-4 z-50 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground shadow-lg focus:not-sr-only focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 focus:ring-offset-background"
      >
        Skip to main content
      </a>
      <aside className="fixed inset-y-0 z-30 hidden w-60 flex-col border-r border-sidebar-border bg-sidebar md:flex">
        <Brand />
        <nav aria-label="Main navigation" className="flex-1 space-y-1 overflow-y-auto px-3 py-5">
          {navigationItems}
        </nav>
        <SessionPanel session={session} selectedProject={selectedProject} onProjectChange={selectProject} onLogout={logout} />
      </aside>

      <div className="min-h-screen md:pl-60">
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-border/80 bg-background/90 px-4 backdrop-blur-xl md:px-8">
          <Link href="/" className="flex items-center gap-2 md:hidden" aria-label="Sluice overview">
            <Image src="/logo-3.png" alt="" width={32} height={32} className="size-8 object-contain" priority />
            <span className="font-semibold tracking-tight">Sluice</span>
          </Link>
          <div className="hidden flex-1 md:block" />
          <div className="flex items-center gap-2">
            <Link
              href="/pipelines"
              className="inline-flex items-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground shadow-[0_8px_22px_rgb(35_149_255_/_0.26)] transition-colors hover:bg-primary/85 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background sm:px-3.5"
            >
              <Play className="size-4" aria-hidden="true" />
              <span className="sm:hidden">Build</span>
              <span className="hidden sm:inline">Build pipeline</span>
            </Link>
            <button
              type="button"
              className="inline-grid size-10 place-items-center rounded-lg border border-border bg-card text-foreground transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring md:hidden"
              aria-label={mobileMenuOpen ? 'Close navigation menu' : 'Open navigation menu'}
              aria-expanded={mobileMenuOpen}
              onClick={() => setMobileMenuOpen((open) => !open)}
            >
              {mobileMenuOpen ? <X className="size-5" /> : <Menu className="size-5" />}
            </button>
          </div>
        </header>

        {mobileMenuOpen && (
          <div className="fixed inset-x-0 top-16 z-20 border-b border-border bg-sidebar px-4 pb-4 pt-3 shadow-2xl md:hidden">
            <nav aria-label="Mobile navigation" className="space-y-1">{navigationItems}</nav>
            <SessionPanel session={session} selectedProject={selectedProject} onProjectChange={selectProject} onLogout={logout} compact />
          </div>
        )}

        <main id="main-content" tabIndex={-1} className="mx-auto w-full max-w-[1500px] px-4 py-6 focus:outline-none sm:px-6 md:px-8 md:py-8">{children}</main>
      </div>
    </div>
  );
}

function Brand() {
  return (
    <Link href="/" className="flex h-20 items-center gap-3 border-b border-sidebar-border px-5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-sidebar-ring">
      <Image src="/logo-3.png" alt="" width={42} height={42} className="size-10 object-contain" priority />
      <span className="text-lg font-semibold tracking-tight text-foreground">Sluice</span>
    </Link>
  );
}

function SessionPanel({
  session,
  selectedProject,
  onProjectChange,
  onLogout,
  compact = false,
}: {
  session: Session;
  selectedProject?: Session['projects'][number];
  onProjectChange: (projectId: string) => Promise<void>;
  onLogout: () => Promise<void>;
  compact?: boolean;
}) {
  return (
    <div className={cn('border-sidebar-border', compact ? 'mt-4 border-t pt-4' : 'border-t p-4')}>
      <label className="sr-only" htmlFor={compact ? 'mobile-project' : 'desktop-project'}>Selected project</label>
      <select
        id={compact ? 'mobile-project' : 'desktop-project'}
        value={session.selectedProjectId || ''}
        onChange={(event) => onProjectChange(event.target.value)}
        className="w-full rounded-lg border border-sidebar-border bg-background px-3 py-2 text-sm text-foreground shadow-sm outline-none transition-colors focus:border-sidebar-ring focus:ring-2 focus:ring-sidebar-ring/30"
      >
        {session.projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
      </select>
      <div className="mt-3 flex items-center gap-3">
        <div className="grid size-9 shrink-0 place-items-center rounded-full bg-primary text-xs font-bold text-primary-foreground">
          {session.user.email.slice(0, 2).toUpperCase()}
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-foreground">{session.user.email}</p>
          <p className="text-xs text-muted-foreground">{selectedProject?.role ?? 'Member'}</p>
        </div>
        <button
          type="button"
          onClick={onLogout}
          title="Sign out"
          className="grid size-9 place-items-center rounded-lg text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring"
        >
          <LogOut className="size-4" aria-hidden="true" />
          <span className="sr-only">Sign out</span>
        </button>
      </div>
    </div>
  );
}

interface Session {
  user: { id: string; email: string; createdAt: string };
  projects: Array<{ id: string; name: string; role: string }>;
  selectedProjectId?: string;
}
