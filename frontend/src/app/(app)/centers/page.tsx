"use client";

import { useQuery } from "@tanstack/react-query";
import type { ColumnDef, SortingState } from "@tanstack/react-table";
import { FileSpreadsheet, Plus, Search } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge, PageHeader } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Input, Select } from "@/components/ui/form";
import { ErrorState } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api, download, type Query } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import { useCommunes, useFilieresOptions, useProvinces, useRegions } from "@/lib/queries";
import type { CenterSummary, PageResponse } from "@/lib/types";

const PAGE_SIZE = 20;
const LANDS = ["OWNED_BY_EN", "RENTED_BY_EN", "PROVIDED_TO_EN", "ASSOCIATION", "OTHER_SECTOR"] as const;
const MODE_STYLE = {
  DIRECT_EN: "bg-[#E4EEF9] text-[#1B4A7F]",
  ASSOCIATION_WITH_EN_DIRECTOR: "bg-[#FBF1DC] text-[#6B4300]",
  ASSOCIATION_FULL: "bg-[#DFF1E5] text-[#1C6B3A]",
} as const;
const MODES = ["DIRECT_EN", "ASSOCIATION_WITH_EN_DIRECTOR", "ASSOCIATION_FULL"] as const;

export default function CentersPage() {
  const me = useMe();
  const router = useRouter();
  const { t, name, label, num, locale } = useI18n();
  const toast = useToast();
  const [exporting, setExporting] = useState(false);
  const [communeId, setCommuneId] = useState("");
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [regionId, setRegionId] = useState("");
  const [provinceId, setProvinceId] = useState("");
  const [areaType, setAreaType] = useState("");
  const [landStatus, setLandStatus] = useState("");
  const [managementMode, setManagementMode] = useState("");
  const [filiereId, setFiliereId] = useState("");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "nameAr", desc: false }]);

  useEffect(() => {
    const id = setTimeout(() => { setQ(search.trim()); setPage(0); }, 300);
    return () => clearTimeout(id);
  }, [search]);

  const regions = useRegions();
  const provinces = useProvinces(regionId ? Number(regionId) : undefined);
  const filieres = useFilieresOptions();
  const communes = useCommunes(provinceId ? Number(provinceId) : me.provinceId);
  const showRegion = me.role === "ADMIN";
  const showProvince = me.role === "ADMIN" || me.role === "REGIONAL";

  const query: Query = {
    search: q, regionId, provinceId, communeId, areaType, landStatus, managementMode, filiereId, page, size: PAGE_SIZE,
    sort: sorting[0] ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}` : undefined,
  };
  const list = useQuery({
    queryKey: ["centers", "list", query],
    queryFn: () => api<PageResponse<CenterSummary>>("/centers", { query }),
    placeholderData: (prev) => prev,
  });

  const columns = useMemo<ColumnDef<CenterSummary>[]>(() => [
    {
      id: "nameAr", header: t("centers.col.name"),
      cell: ({ row }) => (
        <div>
          <div className="flex items-center gap-2 font-semibold">{name(row.original)} {!row.original.active && <Badge>{t("common.inactive")}</Badge>}</div>
          <div className="text-xs text-muted">{t("centers.provincePrefix", { name: row.original.provincialDirectorateNameAr })}</div>
        </div>
      ),
    },
    { id: "commune", header: t("centers.col.commune"), enableSorting: false, cell: ({ row }) => row.original.communeNameAr },
    { id: "areaType", header: t("centers.col.area"), cell: ({ row }) => label("AreaType", row.original.areaType) },
    { id: "landStatus", header: t("centers.col.land"), cell: ({ row }) => row.original.landStatus ? label("LandStatus", row.original.landStatus) : "—" },
    {
      id: "managementMode", header: t("centers.col.management"),
      cell: ({ row }) => {
        const m = row.original.managementMode;
        return m ? <span className={`inline-block whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ${MODE_STYLE[m]}`}>{t(`centers.mgmt.short.${m}` as const)}</span> : "—";
      },
    },
    { id: "filieresCount", header: t("centers.col.filieres"), enableSorting: false, cell: ({ row }) => t("centers.filieresN", { n: num(row.original.filieresCount) }) },
    { id: "beneficiariesCount", header: t("centers.col.beneficiaries"), enableSorting: false, cell: ({ row }) => num(row.original.beneficiariesCount) },
    { id: "staffCount", header: t("centers.col.staff"), enableSorting: false, cell: ({ row }) => num(row.original.staffCount) },
    {
      id: "edit", header: t("common.actions"), enableSorting: false,
      cell: ({ row }) => can.write(me) ? (
        <Link href={`/centers/${row.original.id}/edit`} onClick={(e) => e.stopPropagation()} className="font-semibold text-brand-700 hover:underline">{t("common.edit")}</Link>
      ) : null,
    },
  ], [t, name, label, num, me]);

  async function exportXlsx() {
    setExporting(true);
    try {
      const { page: _p, size: _s, sort: _o, ...filters } = query;
      await download("/centers/export.xlsx", { ...filters, lang: locale }, "centres-cef.xlsx");
    } catch {
      toast.error(t("centers.exportFailed"));
    } finally {
      setExporting(false);
    }
  }

  const reset = (f: (v: string) => void) => (e: { target: { value: string } }) => { f(e.target.value); setPage(0); };

  return (
    <>
      <PageHeader title={t("centers.title")} subtitle={list.data ? t("centers.subtitle.n", { n: num(list.data.totalElements) }) : t("centers.subtitle")}
        actions={<>
          <Button variant="secondary" icon={<FileSpreadsheet className="size-4" />} loading={exporting} onClick={exportXlsx}>{t("centers.export")}</Button>
          {can.write(me) && <Button icon={<Plus className="size-4" />} onClick={() => router.push("/centers/new")}>{t("centers.new")}</Button>}
        </>} />
      <div className="mb-4 space-y-3 rounded-xl border border-line bg-white p-4">
        <div className="relative">
          <Search className="pointer-events-none absolute inset-y-0 start-3 my-auto size-4 text-muted" aria-hidden />
          <Input className="ps-9" placeholder={t("centers.search")} value={search} onChange={(e) => setSearch(e.target.value)} aria-label={t("common.search")} />
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-7">
          {showRegion && (
            <Select value={regionId} onChange={(e) => { setRegionId(e.target.value); setProvinceId(""); setCommuneId(""); setPage(0); }} aria-label={t("centers.field.region")}>
              <option value="">{t("centers.field.region")} — {t("common.all")}</option>
              {regions.data?.map((r) => <option key={r.id} value={r.id}>{name(r)}</option>)}
            </Select>
          )}
          {showProvince && (
            <Select value={provinceId} onChange={(e) => { setProvinceId(e.target.value); setCommuneId(""); setPage(0); }} aria-label={t("centers.field.province")}>
              <option value="">{t("centers.field.province")} — {t("common.all")}</option>
              {provinces.data?.map((p) => <option key={p.id} value={p.id}>{name(p)}</option>)}
            </Select>
          )}
          <Select value={communeId} onChange={reset(setCommuneId)} aria-label={t("centers.filter.commune")}>
            <option value="">{t("centers.filter.commune")} — {t("common.all")}</option>
            {communes.data?.map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
          </Select>
          <Select value={areaType} onChange={reset(setAreaType)} aria-label={t("centers.filter.area")}>
            <option value="">{t("centers.filter.area")} — {t("common.all")}</option>
            {(["URBAN", "RURAL"] as const).map((a) => <option key={a} value={a}>{label("AreaType", a)}</option>)}
          </Select>
          <Select value={managementMode} onChange={reset(setManagementMode)} aria-label={t("centers.filter.management")}>
            <option value="">{t("centers.filter.management")} — {t("common.all")}</option>
            {MODES.map((m) => <option key={m} value={m}>{label("ManagementMode", m)}</option>)}
          </Select>
          <Select value={landStatus} onChange={reset(setLandStatus)} aria-label={t("centers.filter.land")}>
            <option value="">{t("centers.filter.land")} — {t("common.all")}</option>
            {LANDS.map((l) => <option key={l} value={l}>{label("LandStatus", l)}</option>)}
          </Select>
          <Select value={filiereId} onChange={reset(setFiliereId)} aria-label={t("centers.filter.filiere")}>
            <option value="">{t("centers.filter.filiere")} — {t("common.all")}</option>
            {filieres.data?.map((f) => <option key={f.id} value={f.id}>{name(f)}</option>)}
          </Select>
        </div>
      </div>
      {list.isError ? <ErrorState error={list.error} /> : (
        <DataTable columns={columns} data={list.data?.content ?? []} loading={list.isPending}
          sorting={sorting} onSortingChange={(s) => { setSorting(s); setPage(0); }}
          page={page} pageSize={PAGE_SIZE} total={list.data?.totalElements ?? 0} onPageChange={setPage}
          onRowClick={(c) => router.push(`/centers/${c.id}`)} />
      )}
    </>
  );
}
