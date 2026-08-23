'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { LayoutDashboard, FileVideo, Activity, Play, Shield, Settings, LogOut, Boxes } from 'lucide-react';
import { cn } from '@/lib/utils';
import Image from 'next/image';

const navigation = [
  { name: 'Overview', href: '/', icon: LayoutDashboard },
  { name: 'Assets', href: '/assets', icon: FileVideo },
  { name: 'Jobs', href: '/jobs', icon: Activity },
  { name: 'Pipelines', href: '/pipelines', icon: Play },
  { name: 'Processor Market', href: '/processors', icon: Boxes },
  { name: 'Governance', href: '/governance', icon: Shield, disabled: true },
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
    await fetch('/api/session/project', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ projectId }),
    });
    window.location.reload();
  }

  async function logout() {
    await fetch('/api/session/logout', { method: 'POST' });
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
                    href={item.disabled ? '#' : item.href}
                    className={cn(
                      'group flex items-center px-3 py-2.5 text-sm font-medium rounded-lg transition-colors',
                      isActive
                        ? 'bg-primary text-primary-foreground'
                        : 'text-sidebar-foreground hover:text-white',
                      item.disabled && 'opacity-40 cursor-not-allowed pointer-events-none'
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
                    {item.disabled && (
                      <span className="ml-auto inline-block py-0.5 px-2 text-[10px] font-medium tracking-wide text-white/50 bg-white/5 rounded-full border border-white/10">
                        Soon
                      </span>
                    )}
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
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <svg className="h-4 w-4 text-muted-foreground" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
              </div>
              <input type="text" placeholder="Search assets, jobs, pipelines..." className="bg-card border border-border text-sm rounded-md pl-10 pr-12 py-1.5 focus:outline-none focus:ring-1 focus:ring-primary w-[300px] text-white" />
              <div className="absolute inset-y-0 right-0 pr-2 flex items-center pointer-events-none">
                <span className="text-xs text-muted-foreground bg-white/5 px-1.5 rounded border border-white/10 font-mono">⌘K</span>
              </div>
            </div>

            <button className="relative text-muted-foreground hover:text-white transition-colors">
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
              </svg>
              <span className="absolute -top-1 -right-1 w-4 h-4 bg-primary text-[9px] font-bold text-white rounded-full flex items-center justify-center">3</span>
            </button>

            <Link href="/assets/upload" className="bg-primary text-primary-foreground px-4 py-1.5 rounded-md text-sm font-medium flex items-center gap-2 hover:bg-primary/90 transition-colors shadow-[0_0_15px_rgba(0,144,255,0.3)]">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
              </svg>
              Upload Asset
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
