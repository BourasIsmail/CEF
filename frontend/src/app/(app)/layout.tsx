"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { Spinner } from "@/components/ui/states";
import { useAuth } from "@/lib/auth";

/** Garde d'accès : sans session valide, retour à la connexion en mémorisant la page demandée. */
export default function ProtectedLayout({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (status === "anonymous") {
      router.replace(pathname === "/" ? "/login" : `/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [status, router, pathname]);

  if (status !== "authenticated") return <Spinner />;
  return <AppShell>{children}</AppShell>;
}
