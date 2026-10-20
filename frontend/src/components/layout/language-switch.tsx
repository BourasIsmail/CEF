"use client";

import clsx from "clsx";
import { useI18n } from "@/lib/i18n";
import { useAuth } from "@/lib/auth";
import { api } from "@/lib/api";
import type { Locale } from "@/lib/types";

/** Bascule arabe / français ; si l'utilisateur est connecté, la préférence est enregistrée sur son compte. */
export function LanguageSwitch({ className }: { className?: string }) {
  const { locale, setLocale, t } = useI18n();
  const { status, setUser } = useAuth();

  async function choose(l: Locale) {
    if (l === locale) return;
    setLocale(l);
    if (status === "authenticated") {
      try {
        setUser(await api("/auth/me/locale", { method: "PUT", body: { preferredLocale: l } }));
      } catch {
        /* la langue reste appliquée localement même si l'enregistrement échoue */
      }
    }
  }

  return (
    <div role="group" className={clsx("inline-flex overflow-hidden rounded-lg border border-line bg-surface text-sm", className)}>
      {(["fr", "ar"] as const).map((l) => (
        <button key={l} type="button" lang={l} aria-pressed={locale === l} onClick={() => choose(l)}
          className={clsx("px-3 py-1.5 font-medium", locale === l ? "bg-white text-brand-700 shadow-sm" : "text-muted hover:text-ink")}>
          {t(`lang.${l}`)}
        </button>
      ))}
    </div>
  );
}
