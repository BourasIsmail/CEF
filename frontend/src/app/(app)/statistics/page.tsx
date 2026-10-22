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
  ["registered", "stats.metric.registered"], ["departed", "stats.metric.departed"], ["graduates", "stats.metric.graduates"],
  ["failed", "stats.metric.failed"], ["absent", "stats.metric.absent"],
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
  const [applied, setApplied] = useState<Query>({});
  const [busy, setBusy] = useState<"xlsx" | "pdf" | null>(null);

  const seasons = useSeasons();
  const regions = useRegions();
  const provinces = useProvinces(regionId ? Number(regionId) : undefined);
  const centers = useCentersOptions(provinceId ? Number(provinceId) : undefined);
  const filieres = useFilieresOptions();

  const query = applied;
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

  const cells = (m: Metrics) => (
    <>
      {METRICS.map(([k]) => {
        const c: Cell = m[k];
        return (
          <Fragment key={k}>
            <td className={TD}>{num(c.male)}</td>
            <td className={TD}>{num(c.female)}</td>
            <td className={TD}>{num(c.total)}</td>
          </Fragment>
        );
      })}
      <td className={TD}>{m.passRatePercent == null ? "—" : `${num(m.passRatePercent, 1)}%`}</td>
    </>
  );

  const d = report.data;
  const selectField = (label: string, value: string, onChange: (v: string) => void, options: { value: string | number; label: string }[], allLabel: string) => (
    <label className="flex min-w-[10.5rem] flex-1 flex-col gap-1.5 text-[13px] font-medium">
      <span>{label}</span>
      <Select value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{allLabel}</option>
        {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </Select>
    </label>
  );
  return (
    <>
      <PageHeader title={t("stats.title")} subtitle={t("stats.subtitle")}
        actions={<>
          <Button variant="secondary" icon={<FileText className="size-4" />} loading={busy === "pdf"} onClick={() => exportAs("pdf")}>{t("stats.exportPdf")}</Button>
          <Button icon={<FileSpreadsheet className="size-4" />} loading={busy === "xlsx"} onClick={() => exportAs("xlsx")}>{t("stats.exportExcel")}</Button>
        </>} />

      <form className="mb-5 flex flex-wrap items-end gap-3 rounded-xl border border-line bg-white p-5"
        onSubmit={(e) => { e.preventDefault(); setApplied({ seasonId, regionId, provinceId, centerId, filiereId }); }}>
        {selectField(t("ben.filter.season"), seasonId, setSeasonId, (seasons.data ?? []).map((s) => ({ value: s.id, label: s.label })), t("ben.enroll.currentSeason"))}
        {me.role === "ADMIN" && selectField(t("stats.filter.region"), regionId, (v) => { setRegionId(v); setProvinceId(""); setCenterId(""); },
          (regions.data ?? []).map((r) => ({ value: r.id, label: name(r) })), t("common.all"))}
        {(me.role === "ADMIN" || me.role === "REGIONAL") && selectField(t("stats.filter.province"), provinceId, (v) => { setProvinceId(v); setCenterId(""); },
          (provinces.data ?? []).map((p) => ({ value: p.id, label: name(p) })), t("common.all"))}
        {selectField(t("stats.filter.center"), centerId, setCenterId, (centers.data ?? []).map((c) => ({ value: c.id, label: name(c) })), t("common.all"))}
        {selectField(t("stats.filter.filiere"), filiereId, setFiliereId, (filieres.data ?? []).map((f) => ({ value: f.id, label: name(f) })), t("common.all"))}
        <Button type="submit" className="min-h-11 bg-ink px-6 hover:bg-ink/90">{t("stats.apply")}</Button>
      </form>

      <section className="space-y-3.5 rounded-xl border border-line bg-white p-5">
        <div className="space-y-1 text-center">
          <div className="text-[17px] font-bold">{t("stats.tableTitle")}</div>
          {d?.season && <div className="text-sm text-muted">{t("stats.seasonLine", { season: d.season.label })}</div>}
        </div>
        {report.isPending && <Spinner />}
        {report.isError && <ErrorState error={report.error} />}
        {d && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[960px] border-collapse text-xs">
              <thead>
                <tr>
                  {(["region", "province", "filiere", "category"] as const).map((k) => <th key={k} rowSpan={2} className={TH}>{t(`stats.col.${k}`)}</th>)}
                  {METRICS.map(([k, key]) => <th key={k} colSpan={3} className={TH}>{t(key)}</th>)}
                  <th rowSpan={2} className={TH}>{t("stats.col.passRate")}</th>
                </tr>
                <tr>
                  {METRICS.map(([k]) => (
                    <Fragment key={k}>
                      <th className={TH}>{t("stats.male")}</th><th className={TH}>{t("stats.female")}</th><th className={TH}>{t("stats.total")}</th>
                    </Fragment>
                  ))}
                </tr>
              </thead>
              <tbody>
                {d.regions.length === 0 && <tr><td colSpan={20} className="p-8 text-center text-muted">{t("stats.empty")}</td></tr>}
                {d.regions.map((region) => {
                  const provRows = (p: (typeof region.provinces)[number]) => p.filieres.reduce((x, f) => x + f.categories.length, 0) + 1;
                  const regionRows = region.provinces.reduce((x, p) => x + provRows(p), 0);
                  return (
                    <Fragment key={region.id}>
                      {region.provinces.map((province, pi) => (
                        <Fragment key={province.id}>
                          {province.filieres.map((f, fi) => f.categories.map((c, ci) => (
                            <tr key={`${f.id}-${c.category}${ci}`} className={c.category === "REMAINING" ? "bg-[#f8f9fb]" : "bg-white"}>
                              {pi === 0 && fi === 0 && ci === 0 && <td rowSpan={regionRows} className={TM}>{name(region)}</td>}
                              {fi === 0 && ci === 0 && <td rowSpan={provRows(province)} className={TM}>{name(province)}</td>}
                              {ci === 0 && <td rowSpan={f.categories.length} className={TM}>{name(f)}</td>}
                              <td className={TL}>{t(`category.${c.category}`)}{c.specification ? ` (${c.specification})` : ""}</td>
                              {cells(c.metrics)}
                            </tr>
                          )))}
                          <tr className="bg-[#e6eef9] font-bold">
                            <td className={TL} colSpan={2}>{t("stats.totalProvince")}</td>{cells(province.total)}
                          </tr>
                        </Fragment>
                      ))}
                      <tr className="bg-[#cddcf1] font-bold">
                        <td className={TL} colSpan={4}>{t("stats.totalRegion")}</td>{cells(region.total)}
                      </tr>
                    </Fragment>
                  );
                })}
                {d.regions.length > 1 && (
                  <tr className="bg-[#b3c8e6] font-bold">
                    <td className={TL} colSpan={4}>{t("stats.totalAll")}</td>{cells(d.grandTotal)}
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
        <p className="text-xs leading-[1.8] text-muted">{t("stats.note")}</p>
      </section>
    </>
  );
}

const TH = "border border-[#5b84bb] bg-[#0c4797] px-[3px] py-[7px] text-center font-semibold text-white";
const TD = "border border-[#cdd2da] px-[3px] py-1.5 text-center tabular-nums";
const TL = "border border-[#cdd2da] px-2 py-1.5 text-start";
const TM = "border border-[#cdd2da] bg-[#f4f5f7] px-2 py-1.5 text-start align-middle font-semibold";
