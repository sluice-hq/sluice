'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { LayoutDashboard, FileVideo, Activity, Play, Shield, Settings, LogOut, Boxes } from 'lucide-react';
import { cn } from '@/lib/utils';
import Image from 'next/image';
import { csrfFetch } from '@/lib/csrf';

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
  if (isLoading || !session) return <div className="min-h-screen grid place-items-center bg-background text-muted-foreground">Loading Sluice…</div>;

  async function selectProject(projectId: string) {
    await csrfFetch('/api/session/project', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ projectId }),
    });
    window.location.reload();
  }

  async function logout() {
    await csrfFetch('/api/session/logout', { method: 'POST' });
    router.replace('/login'); router.refresh();
  }

  return (
    <div className="flex h-screen bg-background text-foreground antialiased selection:bg-primary/30">
      {/* Sidebar with Glassmorphism */}
      <div className="hidden md:flex md:w-[228px] md:flex-col md:fixed md:inset-y-0 z-20">
        <div className="flex-1 flex flex-col min-h-0 bg-sidebar border-r border-sidebar-border">
          <div className="flex items-center h-40 flex-shrink-0 px-6 border-b border-sidebar-border overflow-hidden">
            <div className="w-32 h-32 flex items-center justify-center flex-shrink-0 ml-5 z-10">
              <Image src="/logo-3.png" alt="Sluice Logo" width={128} height={128} className="w-full h-full object-contain" priority />
            </div>
          </div>
          <div className="flex-1 flex flex-col overflow-y-auto">
            <nav className="flex-1 px-4 py-6 space-y-1">
              {navigation.map((item) => {
                const isActive = pathname === item.href || (pathname.startsWith(item.href) && item.href !== '/');
                return (
                  <Link
                    key={item.name}
                    href={item.href}
                    className={cn(
                      'group flex items-center px-3 py-2.5 text-sm font-medium rounded-lg transition-colors',
                      isActive
                        ? 'bg-primary text-primary-foreground'
                        : 'text-sidebar-foreground hover:text-white',
                    )}
                  >
                    <item.icon
                      className={cn(
                        'mr-3 flex-shrink-0 h-5 w-5',
                        isActive ? 'text-primary-foreground' : 'text-sidebar-foreground/70 group-hover:text-white'
                      )}
                      aria-hidden="true"
                    />
                    {item.name}
                  </Link>
                );
              })}
            </nav>
            {/* User Profile */}
            <div className="p-4 border-t border-sidebar-border mt-auto">
              <select value={session.selectedProjectId || ''} onChange={(event) => selectProject(event.target.value)}
                className="mb-4 w-full rounded-md border border-sidebar-border bg-sidebar px-2 py-2 text-xs text-white">
                {session.projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
              </select>
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center text-primary-foreground font-semibold text-sm">
                  {session.user.email.slice(0, 2).toUpperCase()}
                </div>
                <div className="flex flex-col min-w-0 flex-1">
                  <span className="text-sm font-medium text-white truncate">{session.user.email}</span>
                  <span className="text-xs text-muted-foreground">{session.projects.find((project) => project.id === session.selectedProjectId)?.role}</span>
                </div>
                <button onClick={logout} title="Sign out" className="text-muted-foreground hover:text-white"><LogOut className="h-4 w-4" /></button>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content Area */}
      <div className="flex flex-col flex-1 md:pl-64 h-screen relative">

        {/* Mobile TopNav */}
        <div className="sticky top-0 z-30 flex-shrink-0 flex h-40 bg-sidebar border-b border-sidebar-border md:hidden overflow-hidden">
          <div className="flex-1 flex justify-between px-4">
            <div className="flex-1 flex items-center">
              <div className="w-32 h-32 flex items-center justify-center flex-shrink-0 z-10 ml-2">
                <Image src="/logo-3.png" alt="Sluice Logo" width={128} height={128} className="w-full h-full object-contain" priority />
              </div>
            </div>
          </div>
        </div>

        {/* Desktop TopNav */}
        <div className="hidden md:flex items-center justify-between h-16 px-8 border-b border-border bg-background z-10 sticky top-0">
          <div className="flex-1 flex items-center">
            {/* Page Title (injected by page.tsx normally, but we keep header flexible) */}
          </div>
          <div className="flex items-center gap-6">
            <Link href="/pipelines" className="bg-primary text-primary-foreground px-4 py-1.5 rounded-md text-sm font-medium flex items-center gap-2 hover:bg-primary/90 transition-colors shadow-[0_0_15px_rgba(0,144,255,0.3)]">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
              </svg>
              Build Pipeline
            </Link>
          </div>
        </div>

        <main className="flex-1 overflow-y-auto z-10 bg-background">
          <div className="max-w-[1500px] mx-auto py-8 px-4 sm:px-6 md:px-8">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}

interface Session {
  user: { id: string; email: string; createdAt: string };
  projects: Array<{ id: string; name: string; role: string }>;
  selectedProjectId?: string;
}
