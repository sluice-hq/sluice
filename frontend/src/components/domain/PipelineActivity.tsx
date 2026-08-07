import { PipelineStage } from '@/lib/dashboard-mapper';
import { cn } from '@/lib/utils';
import { CheckCircle2, CircleDashed, Loader2, XCircle } from 'lucide-react';

interface PipelineActivityProps {
  stages: PipelineStage[];
}

export function PipelineActivity({ stages }: PipelineActivityProps) {
  return (
    <div className="bg-card border border-border rounded-xl p-6 shadow-sm h-full flex flex-col">
      <div className="mb-6 flex justify-between items-center">
        <div>
          <h3 className="text-lg font-semibold text-white tracking-tight">Pipeline Activity</h3>
          <p className="text-sm text-muted-foreground">Live processor chain execution</p>
        </div>
        <span className="flex h-2 w-2 relative">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-status-running opacity-75"></span>
          <span className="relative inline-flex rounded-full h-2 w-2 bg-status-running"></span>
        </span>
      </div>

      <div className="flex-1 relative pt-2">
        <div className="absolute left-4 top-2 bottom-6 w-px bg-border z-0" />
        
        <div className="space-y-6 relative z-10">
          {stages.map((stage, index) => (
            <div key={index} className="flex gap-4">
              <div className="mt-1 flex-shrink-0 bg-card">
                <StageIcon status={stage.status} />
              </div>
              <div className="flex-1 flex justify-between items-start">
                <div>
                  <p className={cn(
                    "text-sm font-medium",
                    stage.status === 'PENDING' ? "text-muted-foreground" : "text-white"
                  )}>
                    {stage.name}
                  </p>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {stage.assetsProcessed.toLocaleString()} assets
                  </p>
                </div>
                {stage.status === 'RUNNING' && (
                  <span className="text-[10px] uppercase font-bold tracking-wider text-status-running bg-status-running/10 px-2 py-0.5 rounded-full border border-status-running/20">
                    Running
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function StageIcon({ status }: { status: PipelineStage['status'] }) {
  switch (status) {
    case 'COMPLETED':
      return <CheckCircle2 className="w-5 h-5 text-status-success" />;
    case 'RUNNING':
      return <Loader2 className="w-5 h-5 text-status-running animate-spin" />;
    case 'FAILED':
      return <XCircle className="w-5 h-5 text-status-failed" />;
    case 'PENDING':
    default:
      return <CircleDashed className="w-5 h-5 text-muted-foreground" />;
  }
}
