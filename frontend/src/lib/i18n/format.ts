import type { Locale } from "../types";

const tag = (locale: Locale) => (locale === "ar" ? "ar-MA-u-nu-latn" : "fr-FR");

/** Remplace {variable} dans un texte. */
export function interpolate(text: string, vars?: Record<string, string | number>): string {
  if (!vars) return text;
  return text.replace(/\{(\w+)\}/g, (_, k) => (k in vars ? String(vars[k]) : `{${k}}`));
}

export function formatNumber(n: number, locale: Locale, digits = 0): string {
  return new Intl.NumberFormat(tag(locale), { maximumFractionDigits: digits }).format(n);
}

export function formatDate(value: string | undefined | null, locale: Locale): string {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "—";
  return new Intl.DateTimeFormat(tag(locale), { year: "numeric", month: "2-digit", day: "2-digit" }).format(d);
}

export function formatDateTime(value: string | undefined | null, locale: Locale): string {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "—";
  return new Intl.DateTimeFormat(tag(locale), {
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
  }).format(d);
}
