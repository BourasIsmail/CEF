import type { Metadata } from "next";
import { cookies } from "next/headers";
import type { ReactNode } from "react";
import { LOCALE_COOKIE } from "@/lib/i18n";
import type { Locale } from "@/lib/types";
import { Providers } from "./providers";
import "./globals.css";

export const metadata: Metadata = {
  title: "CEF-Manager — منصة التتبع",
  description: "Plateforme de suivi de la formation qualifiante dans les Centres d'Éducation et de Formation",
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  const stored = (await cookies()).get(LOCALE_COOKIE)?.value;
  const locale: Locale = stored === "fr" ? "fr" : "ar";
  return (
    <html lang={locale} dir={locale === "ar" ? "rtl" : "ltr"}>
      <body>
        <Providers locale={locale}>{children}</Providers>
      </body>
    </html>
  );
}
