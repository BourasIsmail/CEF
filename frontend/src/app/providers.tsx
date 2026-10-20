"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { AuthProvider } from "@/lib/auth";
import { LocaleProvider } from "@/lib/i18n";
import type { Locale } from "@/lib/types";
import { ToastProvider } from "@/components/ui/toast";

export function Providers({ locale, children }: { locale: Locale; children: ReactNode }) {
  const [client] = useState(() => new QueryClient({
    defaultOptions: { queries: { refetchOnWindowFocus: false, staleTime: 30_000 } },
  }));
  return (
    <QueryClientProvider client={client}>
      <LocaleProvider initial={locale}>
        <ToastProvider>
          <AuthProvider>{children}</AuthProvider>
        </ToastProvider>
      </LocaleProvider>
    </QueryClientProvider>
  );
}
