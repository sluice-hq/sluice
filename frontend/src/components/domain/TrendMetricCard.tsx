import { TrendMetric } from '@/lib/dashboard-mapper';
import { LineChart, Line, ResponsiveContainer, YAxis } from 'recharts';

interface TrendMetricCardProps {
  metric: TrendMetric;
}

export function TrendMetricCard({ metric }: TrendMetricCardProps) {
  // Format data for Recharts
  const chartData = metric.sparklineData.map((val, i) => ({ value: val, index: i }));
  
  // Choose colors based on semantic rules
  const strokeColor = metric.trendUp ? 'var(--color-status-success)' : 'var(--color-status-failed)';
  
  return (
    <div className="bg-card border border-border rounded-xl p-5 shadow-sm hover:shadow-md transition-shadow relative overflow-hidden group h-full flex flex-col justify-between">
      {/* Subtle background glow effect on hover */}
      <div className="absolute inset-0 bg-gradient-to-br from-primary/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none" />
      
      <div className="flex justify-between items-start mb-4">
        <h3 className="text-sm font-medium text-muted-foreground">{metric.title}</h3>
        {/* Simple icon could go here based on title */}
      </div>
      
      <div className="flex items-end justify-between">
        <div>
          <div className="text-3xl font-bold tracking-tight text-white mb-1">{metric.value}</div>
          <div className={`text-xs font-medium flex items-center gap-1 ${metric.trendUp ? 'text-status-success' : 'text-status-failed'}`}>
            {metric.trendUp ? (
              <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
              </svg>
            ) : (
              <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 17h8m0 0V9m0 8l-8-8-4 4-6-6" />
              </svg>
            )}
            {metric.trend}
          </div>
        </div>
        
        <div className="h-12 w-24">
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData}>
                <YAxis domain={['dataMin - 2', 'dataMax + 2']} hide />
                <Line 
                  type="monotone" 
                  dataKey="value" 
                  stroke={strokeColor} 
                  strokeWidth={2} 
                  dot={false} 
                  isAnimationActive={false} 
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="w-full h-full flex items-center justify-center">
              <span className="text-[10px] text-muted-foreground/50 font-medium">No Data</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
