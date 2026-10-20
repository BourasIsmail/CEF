"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useMemo, type ReactNode } from "react";
import { api, ApiError, setUnauthorizedHandler } from "./api";
import { useI18n } from "./i18n";
import type { Me } from "./types";

interface AuthApi {
  user: Me | undefined;
  status: "loading" | "authenticated" | "anonymous";
  login: (username: string, password: string, rememberMe?: boolean) => Promise<Me>;
  logout: () => Promise<void>;
  /** Recharge le profil (après changement de langue, de mot de passe…). */
  setUser: (u: Me) => void;
}

const Ctx = createContext<AuthApi | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const router = useRouter();
  const { setLocale } = useI18n();

  const me = useQuery({
    queryKey: ["me"],
    queryFn: () => api<Me>("/auth/me"),
    retry: false,
    staleTime: 5 * 60_000,
  });

  // la langue préférée du compte s'applique à la connexion
  useEffect(() => {
    if (me.data?.preferredLocale) setLocale(me.data.preferredLocale);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [me.data?.id]);

  // session irrécupérable : retour à la connexion, sans données en cache
  useEffect(() => {
    setUnauthorizedHandler(() => {
      qc.clear();
      router.replace("/login");
    });
    return () => setUnauthorizedHandler(null);
  }, [qc, router]);

  const login = useCallback(async (username: string, password: string, rememberMe = true) => {
    const user = await api<Me>("/auth/login", { method: "POST", body: { username, password, rememberMe } });
    qc.setQueryData(["me"], user);
    return user;
  }, [qc]);

  const logout = useCallback(async () => {
    try {
      await api("/auth/logout", { method: "POST" });
    } finally {
      qc.clear();
      router.replace("/login");
    }
  }, [qc, router]);

  const setUser = useCallback((u: Me) => qc.setQueryData(["me"], u), [qc]);

  const status: AuthApi["status"] = me.isPending ? "loading"
    : me.data ? "authenticated"
    : "anonymous";

  const value = useMemo(() => ({ user: me.data, status, login, logout, setUser }), [me.data, status, login, logout, setUser]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthApi {
  const v = useContext(Ctx);
  if (!v) throw new Error("useAuth hors AuthProvider");
  return v;
}

/** Profil connecté ; à n'utiliser que dans les pages protégées. */
export function useMe(): Me {
  const { user } = useAuth();
  if (!user) throw new Error("Utilisateur non connecté");
  return user;
}

export function isUnauthorized(e: unknown): boolean {
  return e instanceof ApiError && e.status === 401;
}
