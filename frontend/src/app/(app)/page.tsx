"use client";

import { useQuery } from "@tanstack/react-query";
import { useMe } from "@/lib/auth";
import { Card, PageHeader } from "@/components/ui/card";
import { ErrorState, Spinner } from "@/components/ui/states";
import { api } from "@/lib/api";
import { useI18n } from "@/lib/i18n";
import type { Cell, Dashboard } from "@/lib/types";

function Stat({ label, cell, value }: { label: string; cell?: Cell; value?: number }) {
  const { t, num } = useI18n();
  return (
    <div className="rounded-xl border border-line bg-white p-5">
      <div className="text-sm text-muted">{label}</div>
      <div className="mt-1 text-3xl font-bold text-brand-700">{num(cell?.total ?? value ?? 0)}</div>
      {cell && (
        <div className="mt-1 text-xs text-muted">{t("dashboard.males", { n: num(cell.male) })} · {t("dashboard.females", { n: num(cell.female) })}</div>
      )}
    </div>
  );
}

function Bar({ label, value, max, tone = "bg-brand-600" }: { label: string; value: number; max: number; tone?: string }) {
  const { num } = useI18n();
  return (
    <div className="flex items-center gap-3 text-sm">
      <span className="w-40 shrink-0 truncate text-muted sm:w-56" title={label}>{label}</span>
      <div className="h-3 flex-1 overflow-hidden rounded bg-surface">
        <div className={`h-full rounded ${tone}`} style={{ width: `${max > 0 ? Math.max(2, (value / max) * 100) : 0}%` }} />
      </div>
      <span className="w-14 text-end font-medium tabular-nums">{num(value)}</span>
    </div>
  );
}

export default function DashboardPage() {
  const user = useMe();
  const { t, name, num } = useI18n();
  const q = useQuery({ queryKey: ["dashboard"], queryFn: () => api<Dashboard>("/statistics/dashboard") });

  return (
    <>
      <PageHeader
        title={t("dashboard.title")}
        subtitle={<>{t("dashboard.welcome", { name: user.fullName })}{q.data?.season && <> · {t("dashboard.season", { season: q.data.season.label })}</>}</>}
      />
      {q.isPending && <Spinner />}
      {q.isError && <ErrorState error={q.error} />}
      {q.data && (() => {
        const d = q.data;
        const empty = d.totals.registered.total === 0;
        const maxF = Math.max(0, ...d.byFiliere.map((f) => f.registered));
        const maxC = Math.max(0, ...d.byCategory.map((c) => c.registered));
        const todoTotal = d.todo.graduatesWithoutDiploma + d.todo.examsWithoutExamNumber + d.todo.departedWithoutReason;
        return (
          <div className="space-y-6">
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <Stat label={t("dashboard.registered")} cell={d.totals.registered} />
              <Stat label={t("dashboard.inProgress")} value={d.inProgress} />
              <Stat label={t("dashboard.graduates")} cell={d.totals.graduates} />
              <Stat label={t("dashboard.departed")} cell={d.totals.departed} />
            </div>

            <Card>
              <div className="flex flex-wrap items-end justify-between gap-4">
                <div>
                  <div className="text-sm text-muted">{t("dashboard.passRate")}</div>
                  <div className="text-4xl font-bold text-brand-700">
                    {d.totals.passRatePercent == null ? "—" : `${num(d.totals.passRatePercent, 1)}%`}
                  </div>
                  <p className="mt-1 text-xs text-muted">{t("dashboard.passRate.note")}</p>
                </div>
                <div className="flex gap-6 text-sm text-muted">
                  <span>{t("dashboard.failed")}: <b className="text-ink">{num(d.totals.failed.total)}</b></span>
                  <span>{t("dashboard.absent")}: <b className="text-ink">{num(d.totals.absent.total)}</b></span>
                </div>
              </div>
            </Card>

            {empty ? <Card><p className="text-center text-muted">{t("dashboard.noData")}</p></Card> : (
              <div className="grid gap-6 lg:grid-cols-2">
                <Card title={t("dashboard.byFiliere")}>
                  <div className="space-y-3">
                    {d.byFiliere.map((f) => (
                      <div key={f.id} className="space-y-1">
                        <Bar label={name(f)} value={f.registered} max={maxF} />
                        <Bar label={t("dashboard.graduates")} value={f.graduates} max={maxF} tone="bg-accent" />
                      </div>
                    ))}
                  </div>
                </Card>
                <Card title={t("dashboard.byCategory")}>
                  <div className="space-y-3">
                    {d.byCategory.map((c) => <Bar key={c.category} label={t(`category.${c.category}`)} value={c.registered} max={maxC} />)}
                  </div>
                </Card>
              </div>
            )}

            <Card title={t("dashboard.todo")}>
              {todoTotal === 0 ? <p className="text-sm text-muted">{t("dashboard.todo.none")}</p> : (
                <ul className="list-disc space-y-1 ps-5 text-sm">
                  {d.todo.graduatesWithoutDiploma > 0 && <li>{t("dashboard.todo.noDiploma", { n: num(d.todo.graduatesWithoutDiploma) })}</li>}
                  {d.todo.examsWithoutExamNumber > 0 && <li>{t("dashboard.todo.noExamNumber", { n: num(d.todo.examsWithoutExamNumber) })}</li>}
                  {d.todo.departedWithoutReason > 0 && <li>{t("dashboard.todo.noReason", { n: num(d.todo.departedWithoutReason) })}</li>}
                </ul>
              )}
            </Card>
          </div>
        );
      })()}
    </>
  );
}
