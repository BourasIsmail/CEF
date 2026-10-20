"use client";

import { useQuery } from "@tanstack/react-query";
import type { ColumnDef, SortingState } from "@tanstack/react-table";
import { Plus, Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { FiliereFormModal } from "@/components/filieres/filiere-form-modal";
import { Button } from "@/components/ui/button";
import { Badge, PageHeader } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Input, Select } from "@/components/ui/form";
import { ErrorState } from "@/components/ui/states";
import { api, type Query } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import type { FiliereSummary, PageResponse } from "@/lib/types";

const PAGE_SIZE = 20;

export default function FilieresPage() {
  const me = useMe();
  const router = useRouter();
  const { t, name, num } = useI18n();
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [active, setActive] = useState<"" | "true" | "false">("");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "code", desc: false }]);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    const id = setTimeout(() => { setQ(search.trim()); setPage(0); }, 300);
    return () => clearTimeout(id);
  }, [search]);

  const query: Query = { q, active, page, size: PAGE_SIZE, sort: sorting[0] ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}` : undefined };
  const list = useQuery({
    queryKey: ["filieres", "list", query],
    queryFn: () => api<PageResponse<FiliereSummary>>("/filieres", { query }),
    placeholderData: (prev) => prev,
  });

  const columns = useMemo<ColumnDef<FiliereSummary>[]>(() => [
    { id: "code", header: t("filieres.col.code"), cell: ({ row }) => <span className="ltr-field font-semibold">{row.original.code}</span> },
    { id: "nameAr", header: t("filieres.col.name"), cell: ({ row }) => <span className="font-medium">{name(row.original)}</span> },
    { id: "durationMonths", header: t("filieres.col.duration"), cell: ({ row }) => t("filieres.months", { n: row.original.durationMonths }) },
    { id: "modulesCount", header: t("filieres.col.modules"), enableSorting: false, cell: ({ row }) => num(row.original.modulesCount) },
    { id: "totalCoefficient", header: t("filieres.col.coefficient"), enableSorting: false, cell: ({ row }) => num(row.original.totalCoefficient, 2) },
    { id: "totalHours", header: t("filieres.col.hours"), enableSorting: false, cell: ({ row }) => t("filieres.hours", { n: num(row.original.totalHours) }) },
    { id: "centersCount", header: t("filieres.col.centers"), enableSorting: false, cell: ({ row }) => num(row.original.centersCount) },
    { id: "active", header: t("common.status"), enableSorting: false, cell: ({ row }) => <Badge tone={row.original.active ? "ok" : "neutral"}>{t(row.original.active ? "common.active" : "common.inactive")}</Badge> },
  ], [t, name, num]);

  return (
    <>
      <PageHeader title={t("filieres.title")} subtitle={t("filieres.subtitle")}
        actions={can.admin(me) && <Button icon={<Plus className="size-4" />} onClick={() => setCreating(true)}>{t("filieres.new")}</Button>} />
      <div className="mb-4 grid gap-3 rounded-xl border border-line bg-white p-4 sm:grid-cols-[1fr_12rem]">
        <div className="relative">
          <Search className="pointer-events-none absolute inset-y-0 start-3 my-auto size-4 text-muted" aria-hidden />
          <Input className="ps-9" placeholder={t("filieres.search")} value={search} onChange={(e) => setSearch(e.target.value)} aria-label={t("common.search")} />
        </div>
        <Select value={active} onChange={(e) => { setActive(e.target.value as "" | "true" | "false"); setPage(0); }} aria-label={t("common.status")}>
          <option value="">{t("common.status")} — {t("common.all")}</option>
          <option value="true">{t("common.active")}</option>
          <option value="false">{t("common.inactive")}</option>
        </Select>
      </div>
      {list.isError ? <ErrorState error={list.error} /> : (
        <DataTable columns={columns} data={list.data?.content ?? []} loading={list.isPending}
          sorting={sorting} onSortingChange={(s) => { setSorting(s); setPage(0); }}
          page={page} pageSize={PAGE_SIZE} total={list.data?.totalElements ?? 0} onPageChange={setPage}
          onRowClick={(f) => router.push(`/filieres/${f.id}`)} />
      )}
      <FiliereFormModal filiere={null} open={creating} onClose={() => setCreating(false)} onSaved={(id) => router.push(`/filieres/${id}`)} />
    </>
  );
}
