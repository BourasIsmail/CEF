"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useI18n } from "@/lib/i18n";
import type { AuditEntry } from "@/lib/types";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/card";
import { ErrorState, Spinner } from "@/components/ui/states";

const fmt = (v: unknown) => (v === undefined || v === null || v === "" ? "—" : typeof v === "object" ? JSON.stringify(v) : String(v));

/** Journal d'audit d'une fiche : qui a changé quoi et quand (valeurs avant → après). */
export function AuditHistory({ path, title }: { path: string; title: string }) {
  const { t, dateTime } = useI18n();
  const q = useQuery({ queryKey: ["history", path], queryFn: () => api<AuditEntry[]>(path) });
  return (
    <Card title={title}>
      {q.isPending && <Spinner />}
      {q.isError && <ErrorState error={q.error} />}
      {q.data?.length === 0 && <p className="text-sm text-muted">{t("ben.history.empty")}</p>}
      <ol className="space-y-4">
        {q.data?.map((e) => (
          <li key={e.id} className="rounded-lg border border-line p-3 text-sm">
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <Badge tone={e.action === "DELETE" ? "danger" : e.action === "CREATE" ? "ok" : "brand"}>{t(`ben.history.action.${e.action}`)}</Badge>
              <span className="ltr-field text-xs text-muted">{e.entityType}</span>
              <span className="text-xs text-muted">{t("ben.history.by", { user: e.username, date: dateTime(e.occurredAt) })}</span>
            </div>
            <dl className="grid gap-x-4 gap-y-1 sm:grid-cols-[auto_1fr]">
              {Object.entries(e.changes ?? {}).map(([field, c]) => (
                <div key={field} className="contents">
                  <dt className="ltr-field text-muted">{field}</dt>
                  <dd className="ltr-field break-words"><span className="text-danger line-through">{fmt(c.old)}</span> → <span className="text-ok">{fmt(c.new)}</span></dd>
                </div>
              ))}
            </dl>
          </li>
        ))}
      </ol>
    </Card>
  );
}
