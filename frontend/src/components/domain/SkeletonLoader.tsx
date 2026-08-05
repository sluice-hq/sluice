export function TableSkeleton() {
  return (
    <div className="rounded-md border bg-white">
      <div className="border-b px-4 py-3">
        <div className="h-4 w-[250px] bg-gray-200 rounded animate-pulse"></div>
      </div>
      <div className="p-4 space-y-4">
        {[1, 2, 3, 4, 5].map((i) => (
          <div key={i} className="flex space-x-4">
            <div className="h-4 w-full bg-gray-100 rounded animate-pulse"></div>
            <div className="h-4 w-full bg-gray-100 rounded animate-pulse"></div>
            <div className="h-4 w-full bg-gray-100 rounded animate-pulse"></div>
          </div>
        ))}
      </div>
    </div>
  );
}

export function MetricsSkeleton() {
  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
      {[1, 2, 3, 4].map((i) => (
        <div key={i} className="rounded-xl border bg-card text-card-foreground shadow-sm h-32 animate-pulse bg-white">
          <div className="p-6 flex flex-row items-center justify-between pb-2 space-y-0">
             <div className="h-4 w-[100px] bg-gray-200 rounded"></div>
             <div className="h-4 w-4 bg-gray-200 rounded"></div>
          </div>
          <div className="p-6 pt-0">
             <div className="h-8 w-[60px] bg-gray-200 rounded mt-2"></div>
          </div>
        </div>
      ))}
    </div>
  );
}
