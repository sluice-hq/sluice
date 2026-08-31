import Image from 'next/image';
import Link from 'next/link';
import { cn } from '@/lib/utils';

const sizes = {
  small: { image: 'size-8', text: 'text-base', gap: 'gap-2' },
  medium: { image: 'size-10', text: 'text-xl', gap: 'gap-2.5' },
  large: { image: 'size-10', text: 'text-[30px]', gap: 'gap-1' },
} as const;

export function SluiceBrand({
  href = '/',
  size = 'medium',
  className,
  priority = false,
}: {
  href?: string;
  size?: keyof typeof sizes;
  className?: string;
  priority?: boolean;
}) {
  const variant = sizes[size];

  return (
    <Link
      href={href}
      data-slot="sluice-brand"
      aria-label="Sluice"
      className={cn(
        'inline-flex w-fit items-center rounded-md transition-opacity hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background',
        variant.gap,
        className,
      )}
    >
      <Image
        src="/logo-4.png"
        alt=""
        width={42}
        height={42}
        className={cn('shrink-0 object-contain', variant.image)}
        priority={priority}
      />
      <span className={cn('font-semibold tracking-tight text-foreground', variant.text)}>Sluice</span>
    </Link>
  );
}
