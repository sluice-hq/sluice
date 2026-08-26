import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import Providers from '@/components/providers';
import AppShell from '@/components/layout/AppShell';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: { default: 'Sluice | Media infrastructure for developers', template: '%s | Sluice' },
  description: 'Versioned, observable, policy-checked media pipelines for developer applications.',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark">
      <body className={inter.className}>
        <Providers>
          <AppShell>{children}</AppShell>
        </Providers>
      </body>
    </html>
  );
}
