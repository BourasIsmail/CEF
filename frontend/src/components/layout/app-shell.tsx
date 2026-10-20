"use client";

import clsx from "clsx";
import { BarChart3, BookOpen, Briefcase, FileText, Building2, GraduationCap, CalendarDays, LayoutDashboard, LogOut, Menu, UserRound, UsersRound, X, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import { useAuth, useMe } from "@/lib/auth";
import { useI18n, type MessageKey } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import type { Me } from "@/lib/types";
import { LanguageSwitch } from "./language-switch";

interface NavItem { href: string; label: MessageKey; icon: LucideIcon; show?: (u: Me) => boolean }

const NAV: NavItem[] = [
  { href: "/", label: "nav.dashboard", icon: LayoutDashboard },
  { href: "/beneficiaries", label: "nav.beneficiaries", icon: GraduationCap },
  { href: "/centers", label: "nav.centers", icon: Building2 },
  { href: "/employees", label: "nav.employees", icon: Briefcase },
  { href: "/filieres", label: "nav.filieres", icon: BookOpen },
  { href: "/requests", label: "nav.requests", icon: FileText },
  { href: "/statistics", label: "nav.statistics", icon: BarChart3 },
  { href: "/seasons", label: "nav.seasons", icon: CalendarDays, show: can.admin },
  { href: "/users", label: "nav.users", icon: UsersRound, show: can.manageUsers },
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

  const active = (href: string) => (href === "/" ? pathname === "/" : pathname.startsWith(href));
  const items = NAV.filter((i) => !i.show || i.show(user));

  const sidebar = (
    <nav className="flex h-full flex-col bg-brand-900 text-white">
      <div className="flex items-center justify-between gap-3 px-5 py-5">
        <div>
          <div className="text-lg font-bold">{t("app.short")}</div>
          <div className="text-xs text-white/70">{t("app.org")}</div>
        </div>
        <span className="grid size-9 place-items-center rounded-lg bg-accent text-sm font-bold text-brand-900">CEF</span>
      </div>
      <ul className="flex-1 space-y-1 px-3">
        {items.map(({ href, label, icon: Icon }) => (
          <li key={href}>
            <Link href={href} onClick={() => setOpen(false)} aria-current={active(href) ? "page" : undefined}
              className={clsx("flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                active(href) ? "bg-brand-600 text-white" : "text-white/80 hover:bg-white/10")}>
              <Icon className="size-4" aria-hidden /> {t(label)}
            </Link>
          </li>
        ))}
      </ul>
      <div className="border-t border-white/10 p-3">
        <Link href="/account" onClick={() => setOpen(false)}
          className={clsx("flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm", active("/account") ? "bg-brand-600" : "hover:bg-white/10")}>
          <UserRound className="size-4" aria-hidden />
          <span className="min-w-0">
            <span className="block truncate font-medium">{user.fullName}</span>
            <span className="block truncate text-xs text-white/70">{t(`role.${user.role}`)}</span>
          </span>
        </Link>
        <button type="button" onClick={() => logout()}
          className="mt-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-white/80 hover:bg-white/10">
          <LogOut className="size-4" aria-hidden /> {t("nav.logout")}
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

      <header className="sticky top-0 z-20 flex items-center justify-between gap-3 border-b border-line bg-white/90 px-4 py-3 backdrop-blur lg:px-8">
        <div className="flex items-center gap-3">
          <button type="button" className="rounded p-1.5 hover:bg-surface lg:hidden" aria-label="Menu" onClick={() => setOpen((v) => !v)}>
            {open ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
          <p className="text-sm text-muted">
            {t("dashboard.scope", { scope: scopeLabel(user, name, t("dashboard.scope.national")) })}
          </p>
        </div>
        <LanguageSwitch />
      </header>

      <main className="mx-auto max-w-7xl p-4 lg:p-8">{children}</main>
    </div>
  );
}

