import { FileQuestion } from 'lucide-react';

interface EmptyStateProps {
  title: string;
  description: string;
  action?: React.ReactNode;
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center p-8 text-center bg-card/20 backdrop-blur-md border border-white/5 border-dashed rounded-xl h-64 shadow-lg shadow-black/20 relative overflow-hidden group">
      <div className="absolute inset-0 bg-gradient-to-br from-primary/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none" />
      <div className="flex items-center justify-center w-12 h-12 mb-4 rounded-full bg-white/5 border border-white/10 group-hover:scale-110 group-hover:bg-primary/10 transition-all duration-300">
        <FileQuestion className="w-6 h-6 text-muted-foreground group-hover:text-primary transition-colors" />
      </div>
      <h3 className="text-sm font-medium text-foreground tracking-tight">{title}</h3>
      <p className="mt-1 text-sm text-muted-foreground max-w-sm">{description}</p>
      {action && <div className="mt-6 relative z-10">{action}</div>}
    </div>
  );
}
