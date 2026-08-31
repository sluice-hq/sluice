import { CircleAlert, CircleCheck, LoaderCircle } from 'lucide-react';
import { cn } from '@/lib/utils';

export type ActionStatusKind = 'pending' | 'success' | 'error';

export function ActionStatus({
  kind,
  message,
  className,
}: {
  kind: ActionStatusKind;
  message: string;
  className?: string;
}) {
  const Icon = kind === 'error' ? CircleAlert : kind === 'success' ? CircleCheck : LoaderCircle;

  return (
    <div
      role={kind === 'error' ? 'alert' : 'status'}
      aria-live={kind === 'error' ? 'assertive' : 'polite'}
      aria-atomic="true"
      data-state={kind}
      className={cn(
        'flex min-h-5 items-center gap-2 text-sm',
        kind === 'error' && 'text-destructive',
        kind === 'success' && 'text-status-success',
        kind === 'pending' && 'text-muted-foreground',
        className,
      )}
    >
      <Icon className={cn('size-4 shrink-0', kind === 'pending' && 'sluice-delayed-spinner')} aria-hidden="true" />
      <span>{message}</span>
      {kind === 'pending' && (
        <span className="sluice-flow-mark" aria-hidden="true">
          <span />
          <span />
          <span />
        </span>
      )}
    </div>
  );
}
