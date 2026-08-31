import { Skeleton } from '@/components/ui/skeleton';

export function AppShellSkeleton() {
  return (
    <div className="min-h-screen bg-background" aria-busy="true">
      <aside className="fixed inset-y-0 hidden w-60 border-r border-sidebar-border bg-sidebar p-4 md:block">
        <Skeleton className="mx-2 h-12 w-36" />
        <div className="mt-8 space-y-3">
          {Array.from({ length: 6 }, (_, index) => <Skeleton key={index} className="h-10 w-full" />)}
        </div>
      </aside>
      <div className="md:pl-60">
        <div className="h-16 border-b border-border/80" />
        <main className="mx-auto w-full max-w-[1500px] px-4 py-6 sm:px-6 md:px-8 md:py-8">
          <Skeleton className="h-8 w-52" />
          <Skeleton className="mt-3 h-4 w-80 max-w-full" />
          <div className="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 3 }, (_, index) => <Skeleton key={index} className="h-36 w-full rounded-xl" />)}
          </div>
        </main>
      </div>
      <span className="sr-only" role="status" aria-live="polite">Loading Sluice</span>
    </div>
  );
}
