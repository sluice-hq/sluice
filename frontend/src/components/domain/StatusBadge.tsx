import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import { CheckCircle2, Clock, XCircle, Loader2 } from 'lucide-react';

interface StatusBadgeProps {
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'PENDING';
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const config = {
    QUEUED: { label: 'Queued', color: 'bg-gray-100 text-gray-800 border-gray-200', icon: Clock },
    PENDING: { label: 'Pending', color: 'bg-gray-100 text-gray-800 border-gray-200', icon: Clock },
    RUNNING: { label: 'Running', color: 'bg-blue-100 text-blue-800 border-blue-200', icon: Loader2 },
    COMPLETED: { label: 'Completed', color: 'bg-green-100 text-green-800 border-green-200', icon: CheckCircle2 },
    FAILED: { label: 'Failed', color: 'bg-red-100 text-red-800 border-red-200', icon: XCircle },
  };

  const { label, color, icon: Icon } = config[status] || config['QUEUED'];

  return (
    <Badge variant="outline" className={cn('gap-1.5 px-2.5 py-0.5 font-medium', color, className)}>
      <Icon className={cn('h-3.5 w-3.5', status === 'RUNNING' && 'animate-spin')} />
      {label}
    </Badge>
  );
}
