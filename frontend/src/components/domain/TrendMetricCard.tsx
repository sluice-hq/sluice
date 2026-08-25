import { TrendMetric } from '@/lib/dashboard-mapper';

interface TrendMetricCardProps {
  metric: TrendMetric;
}

export function TrendMetricCard({ metric }: TrendMetricCardProps) {
  return (
    <div className="bg-card border border-border rounded-xl p-5 shadow-sm hover:shadow-md transition-shadow relative overflow-hidden group h-full flex flex-col justify-between">
      {/* Subtle background glow effect on hover */}
      <div className="absolute inset-0 bg-gradient-to-br from-primary/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none" />
      
      <div className="flex justify-between items-start mb-4">
        <h3 className="text-sm font-medium text-muted-foreground">{metric.title}</h3>
        {/* Simple icon could go here based on title */}
      </div>
      
      <div>
        <div className="text-3xl font-bold tracking-tight text-white mb-1">{metric.value}</div>
        <p className="text-xs text-muted-foreground">{metric.description}</p>
      </div>
    </div>
  );
}
