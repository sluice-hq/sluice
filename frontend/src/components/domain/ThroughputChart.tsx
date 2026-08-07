import { ThroughputData } from '@/lib/dashboard-mapper';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

interface ThroughputChartProps {
  data: ThroughputData[];
}

export function ThroughputChart({ data }: ThroughputChartProps) {
  return (
    <div className="bg-card border border-border rounded-xl p-6 shadow-sm flex flex-col h-full">
      <div className="mb-6">
        <h3 className="text-lg font-semibold text-white tracking-tight">Processing Throughput</h3>
        <p className="text-sm text-muted-foreground">Volume of assets processed across all active pipelines</p>
      </div>
      
      <div className="flex-1 w-full min-h-0">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
            <defs>
              <linearGradient id="colorCompleted" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--color-status-success)" stopOpacity={0.3}/>
                <stop offset="95%" stopColor="var(--color-status-success)" stopOpacity={0}/>
              </linearGradient>
              <linearGradient id="colorRunning" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--color-status-running)" stopOpacity={0.3}/>
                <stop offset="95%" stopColor="var(--color-status-running)" stopOpacity={0}/>
              </linearGradient>
              <linearGradient id="colorFailed" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--color-status-failed)" stopOpacity={0.3}/>
                <stop offset="95%" stopColor="var(--color-status-failed)" stopOpacity={0}/>
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--color-border)" opacity={0.5} />
            <XAxis 
              dataKey="timestamp" 
              stroke="var(--color-muted-foreground)" 
              fontSize={12} 
              tickLine={false}
              axisLine={false}
              dy={10}
            />
            <YAxis 
              stroke="var(--color-muted-foreground)" 
              fontSize={12} 
              tickLine={false}
              axisLine={false}
              tickFormatter={(value) => `${value}`}
            />
            <Tooltip 
              contentStyle={{ 
                backgroundColor: 'var(--color-card)', 
                borderColor: 'var(--color-border)',
                borderRadius: '8px',
                boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)'
              }}
              itemStyle={{ color: 'var(--color-foreground)' }}
              labelStyle={{ color: 'var(--color-muted-foreground)', marginBottom: '8px' }}
            />
            <Area 
              type="monotone" 
              dataKey="completed" 
              name="Completed"
              stroke="var(--color-status-success)" 
              fillOpacity={1} 
              fill="url(#colorCompleted)" 
              strokeWidth={2}
            />
            <Area 
              type="monotone" 
              dataKey="running" 
              name="Running"
              stroke="var(--color-status-running)" 
              fillOpacity={1} 
              fill="url(#colorRunning)" 
              strokeWidth={2}
            />
            <Area 
              type="monotone" 
              dataKey="failed" 
              name="Failed"
              stroke="var(--color-status-failed)" 
              fillOpacity={1} 
              fill="url(#colorFailed)" 
              strokeWidth={2}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
