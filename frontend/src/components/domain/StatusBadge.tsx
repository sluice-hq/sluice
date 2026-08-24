import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import { CheckCircle2, Clock, XCircle, Loader2 } from 'lucide-react';

interface StatusBadgeProps {
  status: string;
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const getBadgeConfig = (status: string) => {
    switch (status) {
      case 'PENDING':
        return { color: 'bg-amber-500/10 text-amber-500 border-amber-500/20 shadow-[inset_0_0_8px_rgba(245,158,11,0.1)]', icon: Clock };
      case 'PROCESSING':
      case 'RUNNING':
        return { color: 'bg-blue-500/10 text-blue-400 border-blue-500/20 shadow-[inset_0_0_8px_rgba(59,130,246,0.1)]', icon: Loader2 };
      case 'QUEUED':
      case 'RETRY_WAIT':
        return { color: 'bg-amber-500/10 text-amber-400 border-amber-500/20', icon: Clock };
      case 'REVIEW_REQUIRED':
        return { color: 'bg-orange-500/10 text-orange-400 border-orange-500/20', icon: Clock };
      case 'COMPLETED':
        return { color: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20 shadow-[inset_0_0_8px_rgba(16,185,129,0.1)]', icon: CheckCircle2 };
      case 'FAILED':
        return { color: 'bg-red-500/10 text-red-400 border-red-500/20 shadow-[inset_0_0_8px_rgba(239,68,68,0.1)]', icon: XCircle };
      default:
        return { color: 'bg-gray-500/10 text-gray-400 border-gray-500/20', icon: Clock };
    }
  };

  const { color, icon: Icon } = getBadgeConfig(status);

  return (
    <Badge variant="outline" className={cn('gap-1.5 px-2.5 py-0.5 font-medium tracking-wide backdrop-blur-md rounded-md', color, className)}>
      <Icon className={cn('h-3.5 w-3.5', (status === 'RUNNING' || status === 'PROCESSING') && 'animate-spin')} />
      {status}
    </Badge>
  );
}
