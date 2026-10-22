"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { Card, PageHeader } from "@/components/ui/card";
import { Select } from "@/components/ui/form";
import { ErrorState, Spinner } from "@/components/ui/states";
import { api } from "@/lib/api";
import { useI18n } from "@/lib/i18n";
import { useSeasons } from "@/lib/queries";
import type { BeneficiarySummary, Cell, Dashboard, PageResponse } from "@/lib/types";

const CATEGORY_COLOR = { MIGRANT: "#0c4797", DISABLED: "#b1b3b7", OTHER: "#b5483a", REMAINING: "#7f9cc6" } as const;
const CATEGORY_ORDER = ["MIGRANT", "DISABLED", "OTHER", "REMAINING"] as const;

function Kpi({ label, value, sub, color }: { label: string; value: string; sub: string; color: string }) {
  return (
    <div className="flex flex-col gap-1.5 rounded-xl border border-line bg-white p-5">
      <div className="text-sm font-medium text-muted">{label}</div>
      <div className="text-4xl font-bold leading-tight" style={{ color }}>{value}</div>
      <div className="text-[13px] text-muted">{sub}</div>
    </div>
  );
}

export default function DashboardPage() {
  const { t, name, num, date } = useI18n();
  const seasons = useSeasons();
  const [seasonId, setSeasonId] = useState("");
  const q = useQuery({
    queryKey: ["dashboard", seasonId],
    queryFn: () => api<Dashboard>("/statistics/dashboard", { query: { seasonId } }),
    placeholderData: (p) => p,
  });
  const recent = useQuery({
    queryKey: ["beneficiaries", "recent", seasonId],
    queryFn: () => api<PageResponse<BeneficiarySummary>>("/beneficiaries", { query: { seasonId, size: 5, sort: "registrationDate,desc" } }),
    placeholderData: (p) => p,
  });
  const current = seasonId || String(q.data?.season?.id ?? "");
  const sub = (c: Cell) => `${t("dashboard.males", { n: num(c.male) })} · ${t("dashboard.females", { n: num(c.female) })}`;

  return (
    <>
      <PageHeader
        title={t("dashboard.title")}
        subtitle={<>{t("dashboard.sub")}{q.data?.season && <> · {q.data.season.label}</>}</>}
        actions={(
          <label className="flex items-center gap-2.5 text-sm font-medium">
            <span>{t("dashboard.seasonSelect")}</span>
            <Select className="!w-auto" value={current} onChange={(e) => setSeasonId(e.target.value)}>
              {seasons.data?.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
            </Select>
          </label>
        )}
      />
      {q.isPending && <Spinner />}
      {q.isError && <ErrorState error={q.error} />}
      {q.data && (() => {
        const d = q.data;
        const empty = d.totals.registered.total === 0;
        const maxF = Math.max(1, ...d.byFiliere.map((f) => f.registered));
        const totalReg = d.totals.registered.total;
        const cats = CATEGORY_ORDER.map((c) => ({ category: c, n: d.byCategory.find((x) => x.category === c)?.registered ?? 0 }));
        const todo = [
          { n: d.todo.examsWithoutExamNumber, text: t("dashboard.todoText.exam") },
          { n: d.todo.graduatesWithoutDiploma, text: t("dashboard.todoText.diploma") },
          { n: d.todo.departedWithoutReason, text: t("dashboard.todoText.reason") },
        ].filter((x) => x.n > 0);
        return (
          <div className="space-y-6">
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <Kpi label={t("dashboard.registered")} value={num(d.totals.registered.total)} sub={sub(d.totals.registered)} color="#0c4797" />
              <Kpi label={t("dashboard.departed")} value={num(d.totals.departed.total)} sub={sub(d.totals.departed)} color="#7a4e00" />
              <Kpi label={t("dashboard.graduates")} value={num(d.totals.graduates.total)} sub={sub(d.totals.graduates)} color="#1c6b3a" />
              <Kpi label={t("dashboard.passRate")} value={d.totals.passRatePercent == null ? "—" : `${num(d.totals.passRatePercent, 1)}%`}
                sub={t("dashboard.passRate.note")} color="#16202e" />
            </div>

            {empty ? <Card><p className="text-center text-muted">{t("dashboard.noData")}</p></Card> : (
              <div className="grid gap-4 lg:grid-cols-5">
                <section className="min-w-0 space-y-4 rounded-xl border border-line bg-white p-6 lg:col-span-3">
                  <div className="flex flex-wrap justify-between gap-2">
                    <h2 className="text-lg font-semibold">{t("dashboard.byFiliere")}</h2>
                    <div className="flex gap-4 text-[13px] text-muted">
                      <span className="flex items-center gap-1.5"><span className="size-3 rounded-sm bg-brand-600" />{t("dashboard.registered")}</span>
                      <span className="flex items-center gap-1.5"><span className="size-3 rounded-sm bg-accent" />{t("dashboard.graduates")}</span>
                    </div>
                  </div>
                  {d.byFiliere.map((f) => (
                    <div key={f.id} className="space-y-1.5">
                      <div className="text-sm font-medium">{name(f)}</div>
                      {([["registered", "bg-brand-600"], ["graduates", "bg-accent"]] as const).map(([k, tone]) => (
                        <div key={k} className="flex items-center gap-2.5">
                          <div className={`h-4 rounded ${tone}`} style={{ width: `${(f[k] / maxF) * 100}%`, minWidth: f[k] > 0 ? 4 : 0 }} />
                          <span className="text-[13px] font-semibold tabular-nums">{num(f[k])}</span>
                        </div>
                      ))}
                    </div>
                  ))}
                </section>

                <section className="min-w-0 space-y-4 rounded-xl border border-line bg-white p-6 lg:col-span-2">
                  <h2 className="text-lg font-semibold">{t("dashboard.byCategory")}</h2>
                  <div className="flex h-[22px] overflow-hidden rounded-md bg-[#e3e6ea]">
                    {cats.map((c) => <div key={c.category} className="h-full" style={{ background: CATEGORY_COLOR[c.category], width: `${totalReg > 0 ? (c.n / totalReg) * 100 : 0}%` }} />)}
                  </div>
                  {cats.map((c) => (
                    <div key={c.category} className="flex items-center justify-between gap-2 border-b border-line pb-2.5 text-sm">
                      <span className="flex items-center gap-2"><span className="size-3 rounded-sm" style={{ background: CATEGORY_COLOR[c.category] }} />{t(`category.${c.category}`)}</span>
                      <strong>{num(c.n)}</strong>
                    </div>
                  ))}
                  <p className="text-xs leading-relaxed text-muted">{t("dashboard.remainingNote")}</p>
                </section>
              </div>
            )}

            <div className="grid gap-4 lg:grid-cols-5">
              <section className="min-w-0 space-y-3.5 rounded-xl border border-line bg-white p-6 lg:col-span-3">
                <div className="flex items-center justify-between gap-2">
                  <h2 className="text-lg font-semibold">{t("dashboard.recent")}</h2>
                  <Link href="/beneficiaries" className="text-sm font-semibold text-brand-600 hover:underline">{t("dashboard.viewAll")}</Link>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-surface text-muted">
                      <tr>{[t("ben.col.reg"), t("ben.col.name"), t("ben.col.center"), t("ben.col.filiere"), t("ben.field.regDate")].map((h) => (
                        <th key={h} className="whitespace-nowrap px-3 py-3 text-start font-semibold">{h}</th>
                      ))}</tr>
                    </thead>
                    <tbody>
                      {recent.data?.content.map((r) => (
                        <tr key={r.id} className="border-t border-line">
                          <td className="ltr-field px-3 py-3 text-xs">{r.registrationNumber}</td>
                          <td className="px-3 py-3"><Link href={`/beneficiaries/${r.id}`} className="hover:underline">{r.fullNameAr}</Link></td>
                          <td className="px-3 py-3">{r.centerNameAr}</td>
                          <td className="px-3 py-3">{r.filiereNameAr ?? "—"}</td>
                          <td className="px-3 py-3 tabular-nums">{date(r.registrationDate)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>

              <section className="min-w-0 space-y-3.5 rounded-xl border border-line bg-white p-6 lg:col-span-2">
                <h2 className="text-lg font-semibold">{t("dashboard.todo")}</h2>
                {todo.length === 0 ? <p className="text-sm text-muted">{t("dashboard.todo.none")}</p> : todo.map((x) => (
                  <div key={x.text} className="flex items-center gap-3 rounded-lg bg-warn-50 p-3 text-sm leading-relaxed text-[#5e3d00]">
                    <strong className="min-w-8 text-[22px]">{num(x.n)}</strong><span>{x.text}</span>
                  </div>
                ))}
              </section>
            </div>
          </div>
        );
      })()}
    </>
  );
}
