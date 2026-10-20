"use client";

import { AlertTriangle, Inbox, Loader2 } from "lucide-react";
import { useT } from "@/lib/i18n";
import { errorKey } from "@/lib/errors";

export function Spinner({ label }: { label?: string }) {
  const t = useT();
  return (
    <div role="status" className="flex items-center justify-center gap-2 p-10 text-muted">
      <Loader2 className="size-5 animate-spin" aria-hidden /> {label ?? t("common.loading")}
    </div>
  );
}

export function Empty({ message }: { message?: string }) {
  const t = useT();
  return (
    <div className="flex flex-col items-center gap-2 p-10 text-muted">
      <Inbox className="size-8" aria-hidden /> {message ?? t("common.empty")}
    </div>
  );
}

export function ErrorState({ error }: { error: unknown }) {
  const t = useT();
  return (
    <div role="alert" className="flex items-center gap-2 rounded-lg bg-danger-50 p-4 text-sm text-danger">
      <AlertTriangle className="size-4 shrink-0" aria-hidden /> {t(errorKey(error))}
    </div>
  );
}
