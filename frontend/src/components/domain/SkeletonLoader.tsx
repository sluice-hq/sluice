import { Skeleton } from '@/components/ui/skeleton';

export function MetricsSkeleton() {
  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-5">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="rounded-xl border border-white/5 bg-card/20 backdrop-blur-md p-6">
          <Skeleton className="h-5 w-24 mb-4 bg-white/5" />
          <Skeleton className="h-8 w-16 bg-white/5" />
        </div>
      ))}
    </div>
  );
}

export function TableSkeleton() {
  return (
    <div className="border border-white/5 rounded-xl bg-card/20 backdrop-blur-md p-4 space-y-4">
      <Skeleton className="h-8 w-full bg-white/5" />
      <Skeleton className="h-12 w-full bg-white/5" />
      <Skeleton className="h-12 w-full bg-white/5" />
      <Skeleton className="h-12 w-full bg-white/5" />
    </div>
  );
}

export function AssetDetailsSkeleton() {
  return (
    <div className="space-y-6">
      <div className="flex items-center space-x-4">
        <Skeleton className="h-12 w-12 rounded-full bg-white/5" />
        <div className="space-y-2">
          <Skeleton className="h-6 w-64 bg-white/5" />
          <Skeleton className="h-4 w-32 bg-white/5" />
        </div>
      </div>
      <Skeleton className="h-[400px] w-full rounded-xl bg-white/5" />
    </div>
  );
}
