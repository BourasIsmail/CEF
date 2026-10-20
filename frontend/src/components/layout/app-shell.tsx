"use client";

import { useQuery } from "@tanstack/react-query";
import clsx from "clsx";
import { Menu, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import { api } from "@/lib/api";
import { useAuth, useMe } from "@/lib/auth";
import { useI18n, type MessageKey } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import { useProvinces } from "@/lib/queries";
import type { CenterSummary, Me, PageResponse } from "@/lib/types";
import { LanguageSwitch } from "./language-switch";

interface NavItem { href: string; label: MessageKey; show?: (u: Me) => boolean }

/** Ordre et libellés de la maquette ; les écrans d'administration viennent après. */
const NAV: NavItem[] = [
  { href: "/", label: "nav.dashboard" },
  { href: "/centers", label: "nav.centers" },
  { href: "/beneficiaries", label: "nav.beneficiaries" },
  { href: "/employees", label: "nav.employees" },
  { href: "/filieres", label: "nav.filieres" },
  { href: "/requests", label: "nav.requests" },
  { href: "/statistics", label: "nav.statistics" },
  { href: "/seasons", label: "nav.seasons", show: can.admin },
  { href: "/users", label: "nav.users", show: can.manageUsers },
];

export function scopeLabel(u: Me, name: (o: { nameAr?: string }) => string, national: string): string {
  if (u.provinceNameAr) return u.provinceNameAr;
  if (u.regionNameAr) return u.regionNameAr;
  return national;
}

export function AppShell({ children }: { children: ReactNode }) {
  const user = useMe();
  const { logout } = useAuth();
  const { t, name } = useI18n();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const provinces = useProvinces();
  const centersCount = useQuery({
    queryKey: ["centers", "count"],
    queryFn: () => api<PageResponse<CenterSummary>>("/centers", { query: { size: 1 } }).then((p) => p.totalElements),
    staleTime: 5 * 60_000,
  });

  const active = (href: string) => (href === "/" ? pathname === "/" : pathname.startsWith(href));
  const items = NAV.filter((i) => !i.show || i.show(user));

  const sidebar = (
    <nav className="flex h-full flex-col bg-brand-900 text-white">
      <div className="flex items-center gap-3 px-4 pt-6 pb-7">
        <span className="grid size-10 shrink-0 place-items-center rounded-[10px] bg-accent text-[15px] font-bold text-brand-900">CEF</span>
        <div>
          <div className="text-[17px] font-bold">{t("app.short")}</div>
          <div className="text-xs leading-normal text-brand-100">{t("app.tagline")}</div>
        </div>
      </div>
      <ul className="flex-1 space-y-1 px-4">
        {items.map(({ href, label }) => (
          <li key={href}>
            <Link href={href} onClick={() => setOpen(false)} aria-current={active(href) ? "page" : undefined}
              className={clsx("block rounded-lg px-3.5 py-3 text-[15px] transition-colors",
                active(href) ? "bg-[#1f6f6b] font-semibold text-white" : "font-medium text-[#d3e4e2] hover:bg-white/10")}>
              {t(label)}
            </Link>
          </li>
        ))}
      </ul>
      <div className="mx-4 mb-6 border-t border-[#2b5e5c] pt-4 text-[13px] leading-7 text-brand-100">
        <Link href="/account" onClick={() => setOpen(false)} aria-current={active("/account") ? "page" : undefined} className="block hover:text-white">
          <span className="block truncate text-sm font-semibold text-white">{user.fullName}</span>
          <span className="block truncate">{t(`role.${user.role}`)}</span>
        </Link>
        <button type="button" onClick={() => logout()} className="mt-2 inline-block font-medium text-accent hover:underline">
          {t("nav.logout")}
        </button>
      </div>
    </nav>
  );

  return (
    <div className="min-h-screen lg:ps-64">
      <aside className="fixed inset-y-0 start-0 z-30 hidden w-64 lg:block">{sidebar}</aside>

      {open && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-ink/50" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 start-0 w-64">{sidebar}</aside>
        </div>
      )}

      <header className="sticky top-0 z-20 flex items-center justify-between gap-3 border-b border-line bg-white px-4 py-3.5 lg:px-8">
        <div className="flex items-center gap-3">
          <button type="button" className="rounded p-1.5 hover:bg-surface lg:hidden" aria-label="Menu" onClick={() => setOpen((v) => !v)}>
            {open ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
          <p className="text-sm text-[#3c4d55]">
            {t("dashboard.scope", { scope: "" })}
            <strong className="text-brand-600">{scopeLabel(user, name, t("dashboard.scope.national"))}</strong>
            {provinces.data && centersCount.data != null && (
              <> · {t("dashboard.scope.counts", { provinces: provinces.data.length, centers: centersCount.data })}</>
            )}
          </p>
        </div>
        <LanguageSwitch />
      </header>

      <main className="mx-auto max-w-7xl p-4 lg:p-8">{children}</main>
    </div>
  );
}
