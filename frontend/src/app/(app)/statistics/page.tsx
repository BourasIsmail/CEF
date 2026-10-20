"use client";

import { useQuery } from "@tanstack/react-query";
import { FileSpreadsheet, FileText } from "lucide-react";
import { Fragment, useState } from "react";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/card";
import { Select } from "@/components/ui/form";
import { ErrorState, Spinner } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api, download, type Query } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { errorText } from "@/lib/errors";
import { useI18n } from "@/lib/i18n";
import { useCentersOptions, useFilieresOptions, useProvinces, useRegions, useSeasons } from "@/lib/queries";
import type { Cell, Metrics, StatisticsReport } from "@/lib/types";

const METRICS = [
  ["registered", "dashboard.registered"], ["departed", "dashboard.departed"], ["graduates", "dashboard.graduates"],
  ["failed", "dashboard.failed"], ["absent", "dashboard.absent"],
] as const;

export default function StatisticsPage() {
  const me = useMe();
  const { t, name, num } = useI18n();
  const toast = useToast();
  const [seasonId, setSeasonId] = useState("");
  const [regionId, setRegionId] = useState("");
  const [provinceId, setProvinceId] = useState("");
  const [centerId, setCenterId] = useState("");
  const [filiereId, setFiliereId] = useState("");
  const [busy, setBusy] = useState<"xlsx" | "pdf" | null>(null);

  const seasons = useSeasons();
  const regions = useRegions();
  const provinces = useProvinces(regionId ? Number(regionId) : undefined);
  const centers = useCentersOptions(provinceId ? Number(provinceId) : undefined);
  const filieres = useFilieresOptions();

  const query: Query = { seasonId, regionId, provinceId, centerId, filiereId };
  const report = useQuery({ queryKey: ["statistics", query], queryFn: () => api<StatisticsReport>("/statistics/report", { query }), placeholderData: (p) => p });

  async function exportAs(kind: "xlsx" | "pdf") {
    setBusy(kind);
    try {
      await download(`/statistics/report.${kind}`, { ...query, lang: document.documentElement.lang === "fr" ? "fr" : "ar" }, `statistiques-cef-${report.data?.season?.label ?? ""}.${kind}`);
    } catch (e) {
      toast.error(errorText(e, t));
    } finally {
      setBusy(null);
    }
  }

  const cells = (m: Metrics, bold = false) => (
    <>
      {METRICS.map(([k]) => {
        const c: Cell = m[k];
        return (
          <Fragment key={k}>
            <td className={`px-2 py-2 text-center tabular-nums ${bold ? "font-semibold" : ""}`}>{num(c.male)}</td>
            <td className={`px-2 py-2 text-center tabular-nums ${bold ? "font-semibold" : ""}`}>{num(c.female)}</td>
            <td className={`px-2 py-2 text-center tabular-nums font-semibold`}>{num(c.total)}</td>
          </Fragment>
        );
      })}
      <td className="px-2 py-2 text-center tabular-nums font-semibold">{m.passRatePercent == null ? "—" : `${num(m.passRatePercent, 1)}%`}</td>
    </>
  );

  const d = report.data;
  return (
    <>
      <PageHeader title={t("stats.title")} subtitle={t("stats.subtitle")}
        actions={<>
          <Button variant="secondary" icon={<FileSpreadsheet className="size-4" />} loading={busy === "xlsx"} onClick={() => exportAs("xlsx")}>{t("stats.exportExcel")}</Button>
          <Button variant="secondary" icon={<FileText className="size-4" />} loading={busy === "pdf"} onClick={() => exportAs("pdf")}>{t("stats.exportPdf")}</Button>
        </>} />

      <div className="mb-4 grid gap-3 rounded-xl border border-line bg-white p-4 sm:grid-cols-2 lg:grid-cols-5">
        <Select value={seasonId} onChange={(e) => setSeasonId(e.target.value)} aria-label={t("ben.filter.season")}>
          <option value="">{t("ben.filter.season")} — {t("ben.enroll.currentSeason")}</option>
          {seasons.data?.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
        </Select>
        {me.role === "ADMIN" && (
          <Select value={regionId} onChange={(e) => { setRegionId(e.target.value); setProvinceId(""); setCenterId(""); }} aria-label={t("stats.filter.region")}>
            <option value="">{t("stats.filter.region")} — {t("common.all")}</option>
            {regions.data?.map((r) => <option key={r.id} value={r.id}>{name(r)}</option>)}
          </Select>
        )}
        {(me.role === "ADMIN" || me.role === "REGIONAL") && (
          <Select value={provinceId} onChange={(e) => { setProvinceId(e.target.value); setCenterId(""); }} aria-label={t("stats.filter.province")}>
            <option value="">{t("stats.filter.province")} — {t("common.all")}</option>
            {provinces.data?.map((p) => <option key={p.id} value={p.id}>{name(p)}</option>)}
          </Select>
        )}
        <Select value={centerId} onChange={(e) => setCenterId(e.target.value)} aria-label={t("stats.filter.center")}>
          <option value="">{t("stats.filter.center")} — {t("common.all")}</option>
          {centers.data?.map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
        </Select>
        <Select value={filiereId} onChange={(e) => setFiliereId(e.target.value)} aria-label={t("stats.filter.filiere")}>
          <option value="">{t("stats.filter.filiere")} — {t("common.all")}</option>
          {filieres.data?.map((f) => <option key={f.id} value={f.id}>{name(f)}</option>)}
        </Select>
      </div>

      {report.isPending && <Spinner />}
      {report.isError && <ErrorState error={report.error} />}
      {d && (
        <div className="overflow-x-auto rounded-xl border border-line bg-white">
          <table className="w-full min-w-[1200px] border-collapse text-sm">
            <thead className="bg-surface text-muted">
              <tr>
                <th rowSpan={2} className="px-3 py-2 text-start font-semibold">{t("stats.col.region")}</th>
                <th rowSpan={2} className="px-3 py-2 text-start font-semibold">{t("stats.col.province")}</th>
                <th rowSpan={2} className="px-3 py-2 text-start font-semibold">{t("stats.col.filiere")}</th>
                <th rowSpan={2} className="px-3 py-2 text-start font-semibold">{t("stats.col.category")}</th>
                {METRICS.map(([k, key]) => <th key={k} colSpan={3} className="border-s border-line px-2 py-2 text-center font-semibold">{t(key)}</th>)}
                <th rowSpan={2} className="border-s border-line px-2 py-2 text-center font-semibold">{t("stats.col.passRate")}</th>
              </tr>
              <tr>
                {METRICS.map(([k]) => (
                  <Fragment key={k}>
                    <th className="border-s border-line px-2 py-1 text-center text-xs font-medium">{t("stats.male")}</th>
                    <th className="px-2 py-1 text-center text-xs font-medium">{t("stats.female")}</th>
                    <th className="px-2 py-1 text-center text-xs font-medium">{t("stats.total")}</th>
                  </Fragment>
                ))}
              </tr>
            </thead>
            <tbody>
              {d.regions.length === 0 && <tr><td colSpan={20} className="p-8 text-center text-muted">{t("stats.empty")}</td></tr>}
              {d.regions.map((region) => {
                const regionRows = region.provinces.reduce((s, p) => s + p.filieres.reduce((x, f) => x + f.categories.length + 1, 0) + 1, 0) + 1;
                return (
                  <Fragment key={region.id}>
                    {region.provinces.map((province, pi) => {
                      const provinceRows = province.filieres.reduce((x, f) => x + f.categories.length + 1, 0) + 1;
                      return (
                        <Fragment key={province.id}>
                          {province.filieres.map((f, fi) => (
                            <Fragment key={f.id}>
                              {f.categories.map((c, ci) => (
                                <tr key={c.category + ci} className="border-t border-line">
                                  {pi === 0 && fi === 0 && ci === 0 && <td rowSpan={regionRows} className="border-e border-line px-3 py-2 align-top font-semibold">{name(region)}</td>}
                                  {fi === 0 && ci === 0 && <td rowSpan={provinceRows} className="border-e border-line px-3 py-2 align-top font-medium">{name(province)}</td>}
                                  {ci === 0 && <td rowSpan={f.categories.length + 1} className="border-e border-line px-3 py-2 align-top">{name(f)}</td>}
                                  <td className="px-3 py-2">{t(`category.${c.category}`)}{c.specification ? ` (${c.specification})` : ""}</td>
                                  {cells(c.metrics)}
                                </tr>
                              ))}
                              <tr className="border-t border-line bg-surface/60 font-semibold">
                                <td className="px-3 py-2">{t("stats.total")}</td>{cells(f.total, true)}
                              </tr>
                            </Fragment>
                          ))}
                          <tr className="border-t border-line bg-brand-50/60 font-semibold">
                            <td className="px-3 py-2" colSpan={2}>{t("stats.totalProvince")}</td>{cells(province.total, true)}
                          </tr>
                        </Fragment>
                      );
                    })}
                    <tr className="border-t border-line bg-brand-100/60 font-bold">
                      <td className="px-3 py-2" colSpan={3}>{t("stats.totalRegion")}</td>{cells(region.total, true)}
                    </tr>
                  </Fragment>
                );
              })}
              {d.regions.length > 0 && (
                <tr className="border-t-2 border-brand-600 bg-brand-100 font-bold">
                  <td className="px-3 py-3" colSpan={4}>{t("stats.totalAll")}</td>{cells(d.grandTotal, true)}
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
      <p className="mt-4 text-xs text-muted">{t("stats.note")}</p>
    </>
  );
}
