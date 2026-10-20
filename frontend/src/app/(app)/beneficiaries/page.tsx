"use client";

import { useQuery } from "@tanstack/react-query";
import type { ColumnDef, SortingState } from "@tanstack/react-table";
import { Plus, Search } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { StatusBadge } from "@/components/beneficiaries/status-badge";
import { Button } from "@/components/ui/button";
import { Badge, PageHeader } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Checkbox, Input, Select } from "@/components/ui/form";
import { ErrorState } from "@/components/ui/states";
import { api, type Query } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import { useCentersOptions, useFilieresOptions, useSeasons } from "@/lib/queries";
import type { BeneficiarySummary, PageResponse } from "@/lib/types";

const PAGE_SIZE = 20;
const STATUSES = ["ENROLLED", "DEPARTED", "GRADUATED", "FAILED", "ABSENT"] as const;
const CATEGORIES = ["MIGRANT", "DISABLED", "OTHER", "REMAINING"] as const;

export default function BeneficiariesPage() {
  const me = useMe();
  const router = useRouter();
  const params = useSearchParams();
  const { t, name, label, num } = useI18n();
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [seasonId, setSeasonId] = useState("");
  const [centerId, setCenterId] = useState(params.get("centerId") ?? "");
  const [filiereId, setFiliereId] = useState("");
  const [status, setStatus] = useState("");
  const [category, setCategory] = useState("");
  const [gender, setGender] = useState("");
  const [without, setWithout] = useState(false);
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "rank", desc: false }]);

  useEffect(() => {
    const id = setTimeout(() => { setQ(search.trim()); setPage(0); }, 300);
    return () => clearTimeout(id);
  }, [search]);

  const seasons = useSeasons();
  const centers = useCentersOptions();
  const filieres = useFilieresOptions();

  const query: Query = {
    search: q, seasonId, centerId, filiereId, status, category, gender, withoutEnrollment: without || undefined, page, size: PAGE_SIZE,
    sort: sorting[0] ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}` : undefined,
  };
  const list = useQuery({
    queryKey: ["beneficiaries", "list", query],
    queryFn: () => api<PageResponse<BeneficiarySummary>>("/beneficiaries", { query }),
    placeholderData: (prev) => prev,
  });

  const columns = useMemo<ColumnDef<BeneficiarySummary>[]>(() => [
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
    { id: "age", header: t("ben.col.age"), enableSorting: false, cell: ({ row }) => num(row.original.age) },
    { id: "gender", header: t("ben.col.gender"), enableSorting: false, cell: ({ row }) => label("Gender", row.original.gender) },
    { id: "center", header: t("ben.col.center"), cell: ({ row }) => row.original.centerNameAr },
    { id: "filiere", header: t("ben.col.filiere"), cell: ({ row }) => row.original.filiereNameAr ?? <span className="text-muted">—</span> },
    { id: "category", header: t("ben.col.category"), cell: ({ row }) => <Badge tone={row.original.category === "REMAINING" ? "neutral" : "brand"}>{t(`category.${row.original.category}`)}</Badge> },
    { id: "status", header: t("ben.col.status"), cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ], [t, label, num]);

  const reset = (f: (v: string) => void) => (e: { target: { value: string } }) => { f(e.target.value); setPage(0); };

  return (
    <>
      <PageHeader title={t("ben.title")} subtitle={t("ben.subtitle")}
        actions={can.write(me) && <Button icon={<Plus className="size-4" />} onClick={() => router.push("/beneficiaries/new")}>{t("ben.new")}</Button>} />
      <div className="mb-4 space-y-3 rounded-xl border border-line bg-white p-4">
        <div className="relative">
          <Search className="pointer-events-none absolute inset-y-0 start-3 my-auto size-4 text-muted" aria-hidden />
          <Input className="ps-9" placeholder={t("ben.search")} value={search} onChange={(e) => setSearch(e.target.value)} aria-label={t("common.search")} />
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
          <Select value={seasonId} onChange={reset(setSeasonId)} aria-label={t("ben.filter.season")}>
            <option value="">{t("ben.filter.season")} — {t("common.all")}</option>
            {seasons.data?.map((s) => <option key={s.id} value={s.id}>{s.label}{s.current ? ` (${t("ben.enroll.currentSeason")})` : ""}</option>)}
          </Select>
          <Select value={centerId} onChange={reset(setCenterId)} aria-label={t("ben.filter.center")}>
            <option value="">{t("ben.filter.center")} — {t("common.all")}</option>
            {centers.data?.map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
          </Select>
          <Select value={filiereId} onChange={reset(setFiliereId)} aria-label={t("ben.filter.filiere")}>
            <option value="">{t("ben.filter.filiere")} — {t("common.all")}</option>
            {filieres.data?.map((f) => <option key={f.id} value={f.id}>{name(f)}</option>)}
          </Select>
          <Select value={category} onChange={reset(setCategory)} aria-label={t("ben.filter.category")}>
            <option value="">{t("ben.filter.category")} — {t("common.all")}</option>
            {CATEGORIES.map((c) => <option key={c} value={c}>{t(`category.${c}`)}</option>)}
          </Select>
          <Select value={status} onChange={reset(setStatus)} aria-label={t("ben.filter.status")}>
            <option value="">{t("ben.filter.status")} — {t("common.all")}</option>
            {STATUSES.map((s) => <option key={s} value={s}>{label("EnrollmentStatus", s)}</option>)}
          </Select>
          <Select value={gender} onChange={reset(setGender)} aria-label={t("ben.filter.gender")}>
            <option value="">{t("ben.filter.gender")} — {t("common.all")}</option>
            <option value="M">{label("Gender", "M")}</option>
            <option value="F">{label("Gender", "F")}</option>
          </Select>
        </div>
        <Checkbox label={t("ben.filter.withoutEnrollment")} checked={without} onChange={(e) => { setWithout(e.target.checked); setPage(0); }} />
      </div>
      {list.isError ? <ErrorState error={list.error} /> : (
        <DataTable columns={columns} data={list.data?.content ?? []} loading={list.isPending}
          sorting={sorting} onSortingChange={(s) => { setSorting(s); setPage(0); }}
          page={page} pageSize={PAGE_SIZE} total={list.data?.totalElements ?? 0} onPageChange={setPage}
          onRowClick={(b) => router.push(`/beneficiaries/${b.id}`)} />
      )}
    </>
  );
}
