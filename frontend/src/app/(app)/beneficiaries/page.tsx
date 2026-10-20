"use client";

import { useQuery } from "@tanstack/react-query";
import type { ColumnDef, SortingState } from "@tanstack/react-table";
import { FileSpreadsheet, Plus, Search, X } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { StatusBadge } from "@/components/beneficiaries/status-badge";
import { Button } from "@/components/ui/button";
import { Badge, PageHeader } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Checkbox, Input, Select } from "@/components/ui/form";
import { ErrorState } from "@/components/ui/states";
import { api, download, type Query } from "@/lib/api";
import { useToast } from "@/components/ui/toast";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import { useCentersOptions, useFilieresOptions, useProvinces, useSeasons } from "@/lib/queries";
import type { BeneficiarySummary, PageResponse } from "@/lib/types";

const PAGE_SIZES = [20, 50, 100] as const;
const COLUMNS_KEY = "cef_ben_columns";
const COLUMN_IDS = ["rank", "registrationNumber", "nameAr", "gender", "age", "center", "filiere", "category", "status"] as const;
const STATUSES = ["ENROLLED", "DEPARTED", "GRADUATED", "FAILED", "ABSENT"] as const;
const CATEGORIES = ["MIGRANT", "DISABLED", "OTHER", "REMAINING"] as const;

export default function BeneficiariesPage() {
  const me = useMe();
  const router = useRouter();
  const params = useSearchParams();
  const { t, name, label, num, locale } = useI18n();
  const toast = useToast();
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [seasonId, setSeasonId] = useState("");
  const [centerId, setCenterId] = useState(params.get("centerId") ?? "");
  const [filiereId, setFiliereId] = useState("");
  const [status, setStatus] = useState("");
  const [category, setCategory] = useState("");
  const [gender, setGender] = useState("");
  const [provinceId, setProvinceId] = useState("");
  const [without, setWithout] = useState(false);
  const [pageSize, setPageSize] = useState<number>(50);
  const [hidden, setHidden] = useState<string[]>([]);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(COLUMNS_KEY);
      // eslint-disable-next-line react-hooks/set-state-in-effect -- lecture unique du stockage navigateur après hydratation
      if (saved) setHidden(JSON.parse(saved));
    } catch { /* stockage indisponible */ }
  }, []);
  const toggleColumn = (id: string) => setHidden((h) => {
    const next = h.includes(id) ? h.filter((x) => x !== id) : [...h, id];
    try { localStorage.setItem(COLUMNS_KEY, JSON.stringify(next)); } catch { /* ignoré */ }
    return next;
  });
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "rank", desc: false }]);

  useEffect(() => {
    const id = setTimeout(() => { setQ(search.trim()); setPage(0); }, 300);
    return () => clearTimeout(id);
  }, [search]);

  const seasons = useSeasons();
  const provinces = useProvinces();
  const centers = useCentersOptions(provinceId ? Number(provinceId) : undefined);
  const filieres = useFilieresOptions();

  const query: Query = {
    search: q, seasonId, provinceId, centerId, filiereId, status, category, gender, withoutEnrollment: without || undefined, page, size: pageSize,
    sort: sorting[0] ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}` : undefined,
  };
  const list = useQuery({
    queryKey: ["beneficiaries", "list", query],
    queryFn: () => api<PageResponse<BeneficiarySummary>>("/beneficiaries", { query }),
    placeholderData: (prev) => prev,
  });

  const allColumns = useMemo<ColumnDef<BeneficiarySummary>[]>(() => [
    { id: "rank", header: t("ben.col.rank"), cell: ({ row }) => <span className="tabular-nums">{num(row.original.rankNumber)}</span> },
    { id: "registrationNumber", header: t("ben.col.reg"), cell: ({ row }) => <span className="ltr-field text-xs">{row.original.registrationNumber}</span> },
    {
      id: "nameAr", header: t("ben.col.name"),
      cell: ({ row }) => (
        <div>
          <div className="font-medium">{row.original.fullNameAr}</div>
          {row.original.fullNameFr && <div className="ltr-field text-xs text-muted">{row.original.fullNameFr}</div>}
        </div>
      ),
    },
    { id: "gender", header: t("ben.col.gender"), enableSorting: false, cell: ({ row }) => label("Gender", row.original.gender) },
    { id: "age", header: t("ben.col.age"), enableSorting: false, cell: ({ row }) => num(row.original.age) },
    { id: "center", header: t("ben.col.center"), cell: ({ row }) => row.original.centerNameAr },
    { id: "filiere", header: t("ben.col.filiere"), cell: ({ row }) => row.original.filiereNameAr ?? <span className="text-muted">—</span> },
    { id: "category", header: t("ben.col.category"), cell: ({ row }) => <Badge tone={row.original.category === "REMAINING" ? "neutral" : "brand"}>{t(`category.${row.original.category}`)}</Badge> },
    { id: "status", header: t("ben.col.status"), cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ], [t, label, num]);
  const columns = useMemo<ColumnDef<BeneficiarySummary>[]>(() => [
    ...allColumns.filter((c) => !hidden.includes(String(c.id))),
    ...(can.write(me) ? [{
      id: "actions", header: t("ben.col.actions"), enableSorting: false,
      cell: ({ row }: { row: { original: BeneficiarySummary } }) => (
        <a href={`/beneficiaries/${row.original.id}/edit`} onClick={(e) => { e.stopPropagation(); e.preventDefault(); router.push(`/beneficiaries/${row.original.id}/edit`); }}
          className="font-semibold text-brand-700 hover:underline">{t("common.edit")}</a>
      ),
    } satisfies ColumnDef<BeneficiarySummary>] : []),
  ], [allColumns, hidden, me, router, t]);

  const reset = (f: (v: string) => void) => (e: { target: { value: string } }) => { f(e.target.value); setPage(0); };

  const seasonLabel = seasons.data?.find((x) => String(x.id) === seasonId)?.label ?? seasons.data?.find((x) => x.current)?.label ?? "";
  const chips: { key: string; text: string; clear: () => void }[] = [];
  const addChip = (key: string, title: string, value: string, clear: () => void) => { if (value) chips.push({ key, text: `${title}: ${value}`, clear }); };
  addChip("season", t("ben.filter.season"), seasonId ? seasonLabel : "", () => setSeasonId(""));
  addChip("province", t("ben.filter.province"), provinceId ? name(provinces.data?.find((x) => String(x.id) === provinceId) ?? { nameAr: provinceId }) : "", () => { setProvinceId(""); setCenterId(""); });
  addChip("center", t("ben.filter.center"), centerId ? name(centers.data?.find((x) => String(x.id) === centerId) ?? { nameAr: centerId }) : "", () => setCenterId(""));
  addChip("filiere", t("ben.filter.filiere"), filiereId ? name(filieres.data?.find((x) => String(x.id) === filiereId) ?? { nameAr: filiereId }) : "", () => setFiliereId(""));
  addChip("category", t("ben.filter.category"), category ? t(`category.${category as (typeof CATEGORIES)[number]}`) : "", () => setCategory(""));
  addChip("status", t("ben.filter.status"), status ? label("EnrollmentStatus", status) : "", () => setStatus(""));
  addChip("gender", t("ben.filter.gender"), gender ? label("Gender", gender) : "", () => setGender(""));
  addChip("without", t("ben.filter.withoutEnrollment"), without ? "✓" : "", () => setWithout(false));
  const resetAll = () => {
    setSearch(""); setSeasonId(""); setProvinceId(""); setCenterId(""); setFiliereId(""); setCategory(""); setStatus(""); setGender(""); setWithout(false); setPage(0);
  };

  async function exportExcel() {
    setExporting(true);
    try {
      const { page: _p, size: _s, ...filters } = query;
      void _p; void _s;
      await download("/beneficiaries/export.xlsx", { ...filters, lang: locale }, "beneficiaires.xlsx");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setExporting(false);
    }
  }

  return (
    <>
      <PageHeader title={t("ben.title")}
        subtitle={list.data ? t("ben.subtitleCount", { total: num(list.data.totalElements), season: seasonLabel }) : t("ben.subtitle")}
        actions={(
          <>
            <Button variant="secondary" icon={<FileSpreadsheet className="size-4" />} loading={exporting} onClick={exportExcel}>{t("ben.exportExcel")}</Button>
            {can.write(me) && <Button icon={<Plus className="size-4" />} onClick={() => router.push("/beneficiaries/new")}>{t("ben.new")}</Button>}
          </>
        )} />
      <div className="mb-4 space-y-4 rounded-xl border border-line bg-white p-5">
        <label className="block space-y-1.5 text-sm font-medium">
          <span>{t("common.search")}</span>
          <div className="relative">
            <Search className="pointer-events-none absolute inset-y-0 start-3 my-auto size-4 text-muted" aria-hidden />
            <Input className="ps-9" type="search" placeholder={t("ben.search")} value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
        </label>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
          <Field label={t("ben.filter.season")}>
            <Select value={seasonId || String(seasons.data?.find((x) => x.current)?.id ?? "")} onChange={reset(setSeasonId)}>
              {seasons.data?.map((x) => <option key={x.id} value={x.id}>{x.label}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.filter.province")}>
            <Select value={provinceId} onChange={(e) => { setProvinceId(e.target.value); setCenterId(""); setPage(0); }}>
              <option value="">{t("common.all")}</option>
              {provinces.data?.map((x) => <option key={x.id} value={x.id}>{name(x)}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.filter.center")}>
            <Select value={centerId} onChange={reset(setCenterId)}>
              <option value="">{t("common.all")}</option>
              {centers.data?.map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.filter.filiere")}>
            <Select value={filiereId} onChange={reset(setFiliereId)}>
              <option value="">{t("common.all")}</option>
              {filieres.data?.map((f) => <option key={f.id} value={f.id}>{name(f)}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.filter.category")}>
            <Select value={category} onChange={reset(setCategory)}>
              <option value="">{t("common.all")}</option>
              {CATEGORIES.map((c) => <option key={c} value={c}>{t(`category.${c}`)}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.filter.status")}>
            <Select value={status} onChange={reset(setStatus)}>
              <option value="">{t("common.all")}</option>
              {STATUSES.map((x) => <option key={x} value={x}>{label("EnrollmentStatus", x)}</option>)}
            </Select>
          </Field>
        </div>
        <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
          <div className="w-40">
            <Field label={t("ben.filter.gender")}>
              <Select value={gender} onChange={reset(setGender)}>
                <option value="">{t("common.all")}</option>
                <option value="M">{label("Gender", "M")}</option>
                <option value="F">{label("Gender", "F")}</option>
              </Select>
            </Field>
          </div>
          <Checkbox label={t("ben.filter.withoutEnrollment")} checked={without} onChange={(e) => { setWithout(e.target.checked); setPage(0); }} />
        </div>
        {chips.length > 0 && (
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="text-muted">{t("ben.chips.active")}</span>
            {chips.map((c) => (
              <span key={c.key} className="inline-flex items-center gap-1 rounded-full bg-brand-50 px-3 py-1.5 text-brand-700">
                {c.text}
                <button type="button" aria-label={t("common.reset")} onClick={() => { c.clear(); setPage(0); }}><X className="size-3.5" /></button>
              </span>
            ))}
            <button type="button" onClick={resetAll} className="min-h-9 font-semibold text-brand-700 hover:underline">{t("common.reset")}</button>
          </div>
        )}
      </div>

      <div className="mb-2 flex flex-wrap items-center justify-end gap-2">
        <details className="relative">
          <summary className="flex min-h-10 cursor-pointer list-none items-center rounded-lg border border-line bg-white px-3.5 text-sm">{t("ben.columns")} ▾</summary>
          <div className="absolute end-0 z-20 mt-1 min-w-48 space-y-1 rounded-lg border border-line bg-white p-3 shadow-lg">
            {COLUMN_IDS.map((id) => (
              <Checkbox key={id} label={t(COLUMN_LABEL[id])} checked={!hidden.includes(id)} onChange={() => toggleColumn(id)} />
            ))}
          </div>
        </details>
        <Select className="!w-auto" aria-label={t("ben.perPage", { n: pageSize })} value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }}>
          {PAGE_SIZES.map((n) => <option key={n} value={n}>{t("ben.perPage", { n })}</option>)}
        </Select>
      </div>
      {list.isError ? <ErrorState error={list.error} /> : (
        <DataTable columns={columns} data={list.data?.content ?? []} loading={list.isPending}
          sorting={sorting} onSortingChange={(s) => { setSorting(s); setPage(0); }}
          page={page} pageSize={pageSize} total={list.data?.totalElements ?? 0} onPageChange={setPage}
          onRowClick={(b) => router.push(`/beneficiaries/${b.id}`)} />
      )}
    </>
  );
}

const COLUMN_LABEL = {
  rank: "ben.col.rank", registrationNumber: "ben.col.reg", nameAr: "ben.col.name", gender: "ben.col.gender", age: "ben.col.age",
  center: "ben.col.center", filiere: "ben.col.filiere", category: "ben.col.category", status: "ben.col.status",
} as const;

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1.5 text-sm font-medium">
      <span>{label}</span>
      {children}
    </label>
  );
}
