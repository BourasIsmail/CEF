"use client";

import Image from "next/image";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState, type FormEvent } from "react";
import { LanguageSwitch } from "@/components/layout/language-switch";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input } from "@/components/ui/form";
import { PasswordInput } from "@/components/ui/password-input";
import { useAuth } from "@/lib/auth";
import { errorKey } from "@/lib/errors";
import { useT } from "@/lib/i18n";

function LoginForm() {
  const t = useT();
  const { login, status } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string>();
  const [remember, setRemember] = useState(true);
  const [busy, setBusy] = useState(false);

  // uniquement des chemins internes : pas de redirection ouverte
  const raw = params.get("next") ?? "/";
  const next = raw.startsWith("/") && !raw.startsWith("//") ? raw : "/";

  useEffect(() => {
    if (status === "authenticated") router.replace(next);
  }, [status, next, router]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(undefined);
    try {
      await login(email.trim(), password, remember);
      router.replace(next);
    } catch (err) {
      setError(t(errorKey(err)));
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4 rounded-xl border border-line bg-white p-6">
      <Field label={t("login.email")}>
        <Input type="email" className="ltr-field" autoComplete="username" autoFocus required value={email} onChange={(e) => setEmail(e.target.value)} />
      </Field>
      <Field label={t("login.password")}>
        <PasswordInput autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
      </Field>
      <Checkbox label={t("login.remember")} checked={remember} onChange={(e) => setRemember(e.target.checked)} />
      {error && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{error}</p>}
      <Button type="submit" loading={busy} className="w-full py-2.5">{t("login.submit")}</Button>
    </form>
  );
}

export default function LoginPage() {
  const t = useT();
  return (
    <div className="grid min-h-screen lg:grid-cols-[1fr_1.1fr]">
      <aside className="relative hidden overflow-hidden bg-brand-600 lg:block">
        <Image src="/login-hero.webp" alt={t("login.heroAlt")} fill priority sizes="55vw" className="object-cover object-[50%_6%]" />
      </aside>

      <main className="flex flex-col justify-center px-6 py-10 sm:px-16">
        <div className="mx-auto w-full max-w-md">
          <div className="mb-8 flex items-center gap-4">
            <Image src="/logo.png" alt={t("app.org")} width={96} height={64} priority className="h-16 w-auto" />
            <p className="text-sm font-semibold leading-relaxed text-ink">{t("app.name")}</p>
          </div>
          <h1 className="mb-1 text-2xl font-bold">{t("login.title")}</h1>
          <p className="mb-6 text-sm text-muted">{t("login.intro")}</p>
          <Suspense><LoginForm /></Suspense>
          <div className="mt-6 flex items-center justify-between gap-3 text-xs text-muted">
            <span>{t("login.forgot")}</span>
            <LanguageSwitch />
          </div>
        </div>
      </main>
    </div>
  );
}
