"use client";

import type { ReactNode } from "react";

/** Navigation par ancres des sections numérotées d'un formulaire (« 1. المعطيات الشخصية »). */
export function SectionNav({ sections, numbered = true }: { sections: { id: string; title: string }[]; numbered?: boolean }) {
  return (
    <nav aria-label="Sections" className="flex flex-wrap gap-2">
      {sections.map((s, i) => (
        <a key={s.id} href={`#${s.id}`} className="rounded-full border border-line bg-white px-4 py-2.5 text-sm font-medium hover:border-brand-500">
          {numbered && `${i + 1}. `}{s.title}
        </a>
      ))}
    </nav>
  );
}

export function NumberedSection({ id, num, title, banner, tone = "info", numbered = true, children }: {
  id: string; num: number; numbered?: boolean; title: string; banner?: string; tone?: "info" | "muted" | "warn"; children: ReactNode;
}) {
  const tones = { info: "bg-brand-50 text-brand-900", muted: "bg-surface text-muted", warn: "bg-warn-50 text-warn" } as const;
  return (
    <section id={id} className="scroll-mt-20 space-y-5 rounded-xl border border-line bg-white p-6">
      <h2 className="flex items-center gap-3 text-lg font-semibold">
        {numbered && <span className="grid size-8 place-items-center rounded-full bg-brand-600 text-[15px] text-white">{num}</span>}
        {title}
      </h2>
      {children}
      {banner && <div className={`rounded-lg px-4 py-3 text-sm leading-7 ${tones[tone]}`}>{banner}</div>}
    </section>
  );
}
