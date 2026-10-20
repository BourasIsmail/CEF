"use client";

import clsx from "clsx";
import { Eye, EyeOff } from "lucide-react";
import { useState, type InputHTMLAttributes } from "react";
import { useT } from "@/lib/i18n";
import { passwordStrength } from "@/lib/form";

const bars = ["bg-line", "bg-danger", "bg-warn", "bg-brand-500", "bg-ok"];

export function PasswordInput({ invalid, strength, className, ...rest }: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean; strength?: string }) {
  const [shown, setShown] = useState(false);
  const t = useT();
  const level = strength === undefined ? 0 : passwordStrength(strength);
  return (
    <div>
      <div className="relative">
        <input
          type={shown ? "text" : "password"}
          aria-invalid={invalid || undefined}
          autoComplete="off"
          className={clsx("ltr-field w-full rounded-lg border border-line bg-white px-3 py-2 pe-10 text-sm focus:border-brand-500 focus:outline-none aria-[invalid=true]:border-danger", className)}
          {...rest}
        />
        <button type="button" onClick={() => setShown((v) => !v)} aria-label={shown ? t("common.hide") : t("common.show")}
          className="absolute inset-y-0 end-0 px-3 text-muted hover:text-ink">
          {shown ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </button>
      </div>
      {strength !== undefined && (
        <div className="mt-2 flex items-center gap-2" aria-hidden>
          <div className="flex flex-1 gap-1">
            {[1, 2, 3, 4].map((i) => <span key={i} className={clsx("h-1.5 flex-1 rounded", level >= i ? bars[level] : "bg-line")} />)}
          </div>
          <span className="text-xs text-muted">{t("account.password.strength")}</span>
        </div>
      )}
    </div>
  );
}
