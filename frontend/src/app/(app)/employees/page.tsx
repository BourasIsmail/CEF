"use client";

import { useQuery } from "@tanstack/react-query";
import type { ColumnDef, SortingState } from "@tanstack/react-table";
import { FileSpreadsheet, Plus, Search } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Input, Select } from "@/components/ui/form";
import { ErrorState } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api, download, type Query } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import { useCentersOptions, useFilieresOptions, useProvinces } from "@/lib/queries";
import type { EmployeeSummary, PageResponse } from "@/lib/types";

const PAGE_SIZE = 20;
const TYPES = ["PEDAGOGICAL", "CENTER_DIRECTOR", "ADMINISTRATIVE", "GUEST_TRAINER", "PEDAGOGICAL_COORDINATOR"] as const;
const ACTIVITY_STYLE = {
  ACTIVE: "bg-[#DFF1E5] text-[#1C6B3A]",
  ON_LEAVE: "bg-[#FBF1DC] text-[#6B4300]",
  SECONDED: "bg-[#E4EEF9] text-[#1B4A7F]",
  SUSPENDED: "bg-[#F9E4E1] text-[#A4332B]",
  RETIRED: "bg-[#EEF3F2] text-[#3C4D55]",
} as const;
const ACTIVITIES = ["ACTIVE", "ON_LEAVE", "SECONDED", "SUSPENDED", "RETIRED"] as const;

export default function EmployeesPage() {
  const me = useMe();
  const router = useRouter();
  const params = useSearchParams();
  const { t, name, label, num, date, locale } = useI18n();
  const toast = useToast();
  const [exporting, setExporting] = useState(false);
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [centerId, setCenterId] = useState(params.get("centerId") ?? "");
  const [type, setType] = useState("");
  const [activity, setActivity] = useState("");
  const [filiereId, setFiliereId] = useState("");
  const [provinceId, setProvinceId] = useState("");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "nameAr", desc: false }]);

  useEffect(() => {
    const id = setTimeout(() => { setQ(search.trim()); setPage(0); }, 300);
    return () => clearTimeout(id);
  }, [search]);

  const provinces = useProvinces();
  const centers = useCentersOptions(provinceId ? Number(provinceId) : undefined);
  const filieres = useFilieresOptions();
  const showProvince = me.role === "ADMIN" || me.role === "REGIONAL";

  const query: Query = {
    search: q, provinceId, centerId, filiereId, type, activityStatus: activity, page, size: PAGE_SIZE,
    sort: sorting[0] ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}` : undefined,
  };
  const list = useQuery({
    queryKey: ["employees", "list", query],
    queryFn: () => api<PageResponse<EmployeeSummary>>("/employees", { query }),
    placeholderData: (prev) => prev,
  });

  const columns = useMemo<ColumnDef<EmployeeSummary>[]>(() => [
    { id: "rankNumber", header: t("hr.col.rank2"), cell: ({ row }) => <span className="ltr-field text-xs">{row.original.rankNumber ?? "—"}</span> },
    {
      id: "nameAr", header: t("hr.col.name"),
      cell: ({ row }) => (
        <div>
          <div className="font-medium">{row.original.fullNameAr}</div>
          {row.original.fullNameFr && <div className="ltr-field text-xs text-muted">{row.original.fullNameFr}</div>}
        </div>
      ),
    },
    {
      id: "type", header: t("hr.col.position"),
      cell: ({ row }) => (
        [row.original.enGrade ?? label("EmployeeType", row.original.employeeType), row.original.enScale ? t("hr.scale", { n: row.original.enScale }) : null].filter(Boolean).join("، ")
      ),
    },
    { id: "center", header: t("hr.col.assignment"), cell: ({ row }) => row.original.centerNameAr ?? <span className="text-muted">{t("hr.provincial")}</span> },
    {
      id: "teaching", header: t("hr.col.teaching"), enableSorting: false,
      cell: ({ row }) => (locale === "fr" ? row.original.teachingFilieresFr ?? row.original.teachingFilieresAr : row.original.teachingFilieresAr) ?? "—",
    },
    { id: "hireDate", header: t("hr.col.hire"), cell: ({ row }) => (row.original.hireDate ? date(row.original.hireDate) : "—") },
    { id: "activityStatus", header: t("hr.col.activity"), cell: ({ row }) => <span className={`inline-block whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ${ACTIVITY_STYLE[row.original.activityStatus]}`}>{label("ActivityStatus", row.original.activityStatus)}</span> },
    {
      id: "edit", header: "", enableSorting: false,
      cell: ({ row }) => can.write(me) ? (
        <Button variant="secondary" onClick={(e) => { e.stopPropagation(); router.push(`/employees/${row.original.id}/edit`); }}>{t("common.edit")}</Button>
      ) : null,
    },
  ], [t, label, locale, date, me, router]);

  async function exportXlsx() {
    setExporting(true);
    try {
      const { page: _p, size: _s, sort: _o, ...filters } = query;
      await download("/employees/export.xlsx", { ...filters, lang: locale }, "ressources-humaines-cef.xlsx");
    } catch {
      toast.error(t("centers.exportFailed"));
    } finally {
      setExporting(false);
    }
  }

  const reset = (f: (v: string) => void) => (e: { target: { value: string } }) => { f(e.target.value); setPage(0); };

  return (
    <>
      <PageHeader title={t("hr.title")} subtitle={list.data ? t("hr.subtitle.n", { n: num(list.data.totalElements) }) : t("hr.subtitle")}
        actions={<>
          <Button variant="secondary" icon={<FileSpreadsheet className="size-4" />} loading={exporting} onClick={exportXlsx}>{t("centers.export")}</Button>
          {can.write(me) && <Button icon={<Plus className="size-4" />} onClick={() => router.push("/employees/new")}>{t("hr.new")}</Button>}
        </>} />

      <div className="mb-4 space-y-3 rounded-xl border border-line bg-white p-4">
        <div className="relative">
          <Search className="pointer-events-none absolute inset-y-0 start-3 my-auto size-4 text-muted" aria-hidden />
          <Input className="ps-9" placeholder={t("hr.search")} value={search} onChange={(e) => setSearch(e.target.value)} aria-label={t("common.search")} />
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          {showProvince && (
            <Select value={provinceId} onChange={(e) => { setProvinceId(e.target.value); setCenterId(""); setPage(0); }} aria-label={t("hr.filter.province")}>
              <option value="">{t("hr.filter.province")} — {t("common.all")}</option>
              {provinces.data?.map((p) => <option key={p.id} value={p.id}>{name(p)}</option>)}
            </Select>
          )}
          <Select value={centerId} onChange={reset(setCenterId)} aria-label={t("hr.filter.center")}>
            <option value="">{t("hr.filter.center")} — {t("common.all")}</option>
            {centers.data?.map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
          </Select>
          <Select value={type} onChange={reset(setType)} aria-label={t("hr.filter.job")}>
            <option value="">{t("hr.filter.job")} — {t("common.all")}</option>
            {TYPES.map((x) => <option key={x} value={x}>{label("EmployeeType", x)}</option>)}
          </Select>
          <Select value={filiereId} onChange={reset(setFiliereId)} aria-label={t("hr.filter.filiere")}>
            <option value="">{t("hr.filter.filiere")} — {t("common.all")}</option>
            {filieres.data?.map((f) => <option key={f.id} value={f.id}>{name(f)}</option>)}
          </Select>
          <Select value={activity} onChange={reset(setActivity)} aria-label={t("hr.filter.activity")}>
            <option value="">{t("hr.filter.activity")} — {t("common.all")}</option>
            {ACTIVITIES.map((x) => <option key={x} value={x}>{label("ActivityStatus", x)}</option>)}
          </Select>
        </div>
      </div>

      {list.isError ? <ErrorState error={list.error} /> : (
        <DataTable columns={columns} data={list.data?.content ?? []} loading={list.isPending}
          sorting={sorting} onSortingChange={(s) => { setSorting(s); setPage(0); }}
          page={page} pageSize={PAGE_SIZE} total={list.data?.totalElements ?? 0} onPageChange={setPage}
          onRowClick={(e) => router.push(`/employees/${e.id}`)} />
      )}
    </>
  );
}
