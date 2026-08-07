import { PlatformService } from '@/lib/dashboard-mapper';
import { cn } from '@/lib/utils';
import { Server, Database, Globe, Box, Activity } from 'lucide-react';

interface SystemHealthProps {
  services: PlatformService[];
}

export function SystemHealth({ services }: SystemHealthProps) {
  return (
    <div className="bg-card border border-border rounded-xl p-6 shadow-sm h-full flex flex-col">
      <div className="mb-6">
        <h3 className="text-lg font-semibold text-white tracking-tight">System Health</h3>
        <p className="text-sm text-muted-foreground">Platform services and latency</p>
      </div>

      <div className="space-y-4">
        {services.map((service, index) => (
          <div key={index} className="flex items-center justify-between p-3 rounded-lg bg-background/50 border border-border/50 hover:bg-white/[0.02] transition-colors">
            <div className="flex items-center gap-3">
              <div className={cn(
                "w-8 h-8 rounded-md flex items-center justify-center",
                service.status === 'HEALTHY' ? "bg-status-success/10 text-status-success" : 
                service.status === 'DEGRADED' ? "bg-status-warning/10 text-status-warning" : 
                "bg-status-failed/10 text-status-failed"
              )}>
                <ServiceIcon name={service.name} />
              </div>
              <div>
                <p className="text-sm font-medium text-white">{service.name}</p>
                <div className="flex items-center gap-2 mt-0.5">
                  <span className={cn(
                    "w-1.5 h-1.5 rounded-full",
                    service.status === 'HEALTHY' ? "bg-status-success" : 
                    service.status === 'DEGRADED' ? "bg-status-warning" : 
                    "bg-status-failed"
                  )} />
                  <span className="text-xs text-muted-foreground capitalize">
                    {service.status.toLowerCase()}
                  </span>
                </div>
              </div>
            </div>
            
            <div className="text-right">
              <span className="text-sm font-medium font-mono text-muted-foreground">
                {service.latencyMs}ms
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function ServiceIcon({ name }: { name: string }) {
  const lowercaseName = name.toLowerCase();
  if (lowercaseName.includes('api')) return <Globe className="w-4 h-4" />;
  if (lowercaseName.includes('db') || lowercaseName.includes('sql')) return <Database className="w-4 h-4" />;
  if (lowercaseName.includes('rabbit') || lowercaseName.includes('queue')) return <Box className="w-4 h-4" />;
  if (lowercaseName.includes('storage') || lowercaseName.includes('blob')) return <Server className="w-4 h-4" />;
  return <Activity className="w-4 h-4" />;
}
