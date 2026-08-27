import Image from 'next/image';
import Link from 'next/link';
import { ArrowRight, BadgeCheck, Boxes, Braces, CheckCircle2, ShieldCheck, Sparkles, Workflow } from 'lucide-react';

const proof = [
  'Versioned pipelines and processor contracts',
  'Direct uploads with durable run results',
  'WebP compression and content-safety decisions',
];

export default function LandingPage() {
  return (
    <main className="min-h-screen overflow-hidden bg-background text-foreground">
      <header className="mx-auto flex max-w-7xl items-center justify-between px-5 py-5 sm:px-8">
        <Link href="/" className="flex items-center gap-2.5"><Image src="/logo-4.png" alt="" width={40} height={40} className="size-10" priority /><span className="text-xl font-semibold tracking-tight">Sluice</span></Link>
        <div className="flex items-center gap-3 text-sm font-semibold"><Link href="/login" className="text-muted-foreground hover:text-foreground">Sign in</Link><Link href="/signup" className="rounded-lg bg-primary px-4 py-2 text-primary-foreground hover:bg-primary/85">Start building</Link></div>
      </header>

      <section className="relative mx-auto grid max-w-7xl gap-12 px-5 pb-20 pt-16 sm:px-8 lg:grid-cols-[1.05fr_.95fr] lg:items-center lg:pb-32 lg:pt-24">
        <div>
          <p className="inline-flex items-center gap-2 rounded-full border border-primary/25 bg-primary/10 px-3 py-1 text-xs font-semibold tracking-wide text-primary"><Sparkles className="size-3.5" /> API-FIRST MEDIA PLATFORM</p>
          <h1 className="mt-6 max-w-3xl text-5xl font-semibold leading-[1.04] tracking-tight sm:text-6xl">Turn media uploads into <span className="text-primary">governed outputs.</span></h1>
          <p className="mt-6 max-w-2xl text-lg leading-8 text-muted-foreground">Sluice gives developer applications versioned media pipelines for validation, transformation, WebP compression, and policy checks—without rebuilding processing infrastructure.</p>
          <div className="mt-8 flex flex-wrap gap-3"><Link href="/signup" className="inline-flex items-center gap-2 rounded-lg bg-primary px-5 py-3 font-semibold text-primary-foreground shadow-[0_12px_30px_rgb(35_149_255_/_0.25)] hover:bg-primary/85">Create a workspace <ArrowRight className="size-4" /></Link><Link href="/app" className="rounded-lg border border-border bg-card px-5 py-3 font-semibold hover:bg-muted">Open dashboard</Link></div>
          <ul className="mt-9 space-y-3 text-sm text-muted-foreground">{proof.map((item) => <li key={item} className="flex items-center gap-3"><CheckCircle2 className="size-4 text-primary" />{item}</li>)}</ul>
        </div>
        <div className="rounded-2xl border border-border bg-card/90 p-5 shadow-[0_30px_100px_rgb(0_0_0_/_0.3)] backdrop-blur sm:p-7">
          <p className="text-xs font-semibold tracking-wide text-muted-foreground">A REAL SLUICE FLOW</p>
          <div className="mt-5 space-y-3 font-mono text-sm"><Flow label="POST /uploads" detail="Direct upload URL" /><Flow label="POST /runs" detail="product-images@stable" active /><Flow label="COMPLETED" detail="image/webp · bytes saved" success /><Flow label="ALLOW" detail="content-safety decision" success /></div>
          <p className="mt-6 border-t border-border pt-5 text-sm leading-6 text-muted-foreground">Every run preserves its pipeline version, processor facts, outputs, and governance decision for inspection or API polling.</p>
        </div>
      </section>

      <section className="border-y border-border bg-card/35"><div className="mx-auto grid max-w-7xl gap-5 px-5 py-16 sm:grid-cols-3 sm:px-8">{[
        [Workflow, 'Composable pipelines', 'Publish immutable versions your application invokes by slug.'],
        [Boxes, 'Curated processors', 'Use bounded, manifest-backed transforms with declared compatibility.'],
        [ShieldCheck, 'Governance included', 'Record ALLOW, REVIEW, or BLOCK decisions alongside run outcomes.'],
      ].map(([Icon, title, detail]) => { const IconComponent = Icon as typeof Workflow; return <article key={title as string} className="rounded-xl border border-border bg-background/60 p-6"><IconComponent className="size-5 text-primary" /><h2 className="mt-4 text-lg font-semibold">{title as string}</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">{detail as string}</p></article>; })}</div></section>
      <section className="mx-auto max-w-7xl px-5 py-20 text-center sm:px-8"><BadgeCheck className="mx-auto size-6 text-primary" /><h2 className="mt-4 text-3xl font-semibold tracking-tight">Build media infrastructure that your app can trust.</h2><p className="mx-auto mt-3 max-w-xl text-muted-foreground">Create a project, publish a pipeline, and exercise the developer API from the control plane.</p><Link href="/signup" className="mt-7 inline-flex items-center gap-2 rounded-lg bg-primary px-5 py-3 font-semibold text-primary-foreground hover:bg-primary/85">Get started <Braces className="size-4" /></Link></section>
    </main>
  );
}

function Flow({ label, detail, active = false, success = false }: { label: string; detail: string; active?: boolean; success?: boolean }) {
  return <div className={`flex items-center justify-between rounded-xl border px-4 py-3 ${active ? 'border-primary/50 bg-primary/10' : 'border-border bg-background/70'}`}><span className={success ? 'text-status-success' : 'text-foreground'}>{label}</span><span className="text-xs text-muted-foreground">{detail}</span></div>;
}
