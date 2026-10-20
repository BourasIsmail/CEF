"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { Locale } from "../types";
import { interpolate, formatDate, formatDateTime, formatNumber } from "./format";
import { messages, type MessageKey } from "./messages";

export const LOCALE_COOKIE = "cef_locale";
export type { MessageKey };

interface I18n {
  locale: Locale;
  dir: "rtl" | "ltr";
  setLocale: (l: Locale) => void;
  t: (key: MessageKey, vars?: Record<string, string | number>) => string;
  /** Libellé d'une valeur énumérée du backend (ex. enumLabel("EnrollmentStatus", "ENROLLED")). */
  label: (group: string, value?: string | null) => string;
  /** Nom localisé d'une entité bilingue (nameAr / nameFr) ; retombe sur l'autre langue si l'une est vide. */
  name: (o: { nameAr?: string | null; nameFr?: string | null } | null | undefined) => string;
  date: (v?: string | null) => string;
  dateTime: (v?: string | null) => string;
  num: (n: number, digits?: number) => string;
}

const Ctx = createContext<I18n | null>(null);

export function LocaleProvider({ initial, children }: { initial: Locale; children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(initial);

  const setLocale = useCallback((l: Locale) => {
    setLocaleState(l);
    document.cookie = `${LOCALE_COOKIE}=${l}; Path=/; Max-Age=31536000; SameSite=Lax`;
  }, []);

  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = locale === "ar" ? "rtl" : "ltr";
  }, [locale]);

  const value = useMemo<I18n>(
    () => ({
      locale,
      dir: locale === "ar" ? "rtl" : "ltr",
      setLocale,
      t: (key, vars) => interpolate(messages[locale][key] ?? key, vars),
      label: (group, value) => (value ? (messages[locale][`enum.${group}.${value}` as MessageKey] ?? value) : "—"),
      name: (o) => (o ? (locale === "ar" ? o.nameAr || o.nameFr : o.nameFr || o.nameAr) ?? "" : ""),
      date: (v) => formatDate(v, locale),
      dateTime: (v) => formatDateTime(v, locale),
      num: (n, d) => formatNumber(n, locale, d),
    }),
    [locale, setLocale],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useI18n(): I18n {
  const v = useContext(Ctx);
  if (!v) throw new Error("useI18n hors LocaleProvider");
  return v;
}

export const useT = () => useI18n().t;
