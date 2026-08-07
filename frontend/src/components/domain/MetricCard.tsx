import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { LucideIcon } from 'lucide-react';

interface MetricCardProps {
  title: string;
  value: string | number;
  icon: LucideIcon;
  description?: string;
}

export function MetricCard({ title, value, icon: Icon, description }: MetricCardProps) {
  return (
    <Card className="group relative overflow-hidden bg-card/40 backdrop-blur-sm border-white/5 transition-all duration-300 hover:scale-[1.02] hover:bg-card/60 hover:shadow-lg hover:shadow-primary/5">
      {/* Subtle top gradient line */}
      <div className="absolute top-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-white/10 to-transparent group-hover:via-primary/50 transition-all duration-500" />
      
      {/* Background glow on hover */}
      <div className="absolute -inset-0.5 bg-gradient-to-br from-primary/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500 blur-xl z-0 pointer-events-none" />

      <CardHeader className="relative z-10 flex flex-row items-center justify-between pb-2 space-y-0">
        <CardTitle className="text-sm font-medium text-muted-foreground group-hover:text-foreground/80 transition-colors">{title}</CardTitle>
        <div className="p-2 rounded-md bg-white/5 group-hover:bg-primary/10 group-hover:scale-110 transition-all duration-300">
            <Icon className="w-4 h-4 text-muted-foreground group-hover:text-primary transition-colors" />
        </div>
      </CardHeader>
      <CardContent className="relative z-10">
        <div className="text-2xl font-bold bg-clip-text text-transparent bg-gradient-to-br from-white to-white/70">{value}</div>
        {description && <p className="text-xs text-muted-foreground mt-1">{description}</p>}
      </CardContent>
    </Card>
  );
}
