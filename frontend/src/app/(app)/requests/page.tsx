"use client";

import { useQuery } from "@tanstack/react-query";
import type { ColumnDef, SortingState } from "@tanstack/react-table";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { RequestPath, RequestStatusBadge } from "@/components/requests/request-badge";
import { DataTable } from "@/components/ui/data-table";
import { Field, Checkbox, Input, Select } from "@/components/ui/form";
import { ErrorState } from "@/components/ui/states";
import { api, type Query } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import { useCentersOptions } from "@/lib/queries";
import type { PageResponse, RequestStatus, RequestSummary } from "@/lib/types";

const PAGE_SIZE = 20;
const STATUSES: RequestStatus[] = ["DRAFT", "SUBMITTED", "RETURNED", "REGIONAL_VALIDATED", "APPROVED", "REJECTED"];

export default function RequestsPage() {
  const me = useMe();
  const router = useRouter();
  const { t, label, date, num, name } = useI18n();
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const [centerId, setCenterId] = useState("");
  const [actionable, setActionable] = useState(false);
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "createdAt", desc: true }]);

  useEffect(() => {
    const id = setTimeout(() => { setQ(search.trim()); setPage(0); }, 300);
    return () => clearTimeout(id);
  }, [search]);

  const centers = useCentersOptions();
  const query: Query = {
    search: q, status, type, centerId, actionable: actionable || undefined, page, size: PAGE_SIZE,
    sort: sorting[0] ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}` : undefined,
  };
  const list = useQuery({
    queryKey: ["requests", "list", query],
    queryFn: () => api<PageResponse<RequestSummary>>("/filiere-requests", { query }),
    placeholderData: (prev) => prev,
  });

  const columns = useMemo<ColumnDef<RequestSummary>[]>(() => [
    { id: "number", header: t("req.col.number"), cell: ({ row }) => <span className="ltr-field">{row.original.requestNumber}</span> },
    { id: "type", header: t("req.col.type"), cell: ({ row }) => t(row.original.requestType === "CREATE" ? "req.info.type.create" : "req.info.type.modify") },
    { id: "name", header: t("req.col.name"), cell: ({ row }) => <span className="font-semibold">{row.original.proposedNameAr || "—"}</span> },
    { id: "center", header: t("req.col.center"), cell: ({ row }) => row.original.centerNameAr },
    { id: "docs", header: t("req.col.docs"), enableSorting: false, cell: ({ row }) => <span className="ltr-field">{num(row.original.documentsCount)} / 8</span> },
    { id: "submittedAt", header: t("req.col.submitted"), cell: ({ row }) => row.original.submittedAt ? date(row.original.submittedAt) : "—" },
    { id: "status", header: t("req.col.status"), cell: ({ row }) => <RequestStatusBadge status={row.original.status} /> },
    {
      id: "actions", header: t("req.col.actions"), enableSorting: false,
      cell: ({ row }) => {
        const r = row.original;
        const draft = r.status === "DRAFT" || r.status === "RETURNED";
        const mine = draft && can.write(me);
        return (
          <Link href={mine ? `/requests/${r.id}/edit` : `/requests/${r.id}`} onClick={(e) => e.stopPropagation()}
            className="whitespace-nowrap font-semibold text-brand-700 hover:underline">
            {mine ? t("req.row.continue") : t(r.status === "REJECTED" ? "req.row.reason" : "req.row.view")}
          </Link>
        );
      },
    },
  ], [t, date, num, me]);

  const reset = (f: (v: string) => void) => (e: { target: { value: string } }) => { f(e.target.value); setPage(0); };

  return (
    <>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-[28px] font-bold">{t("req.page.title")}</h1>
          <p className="mt-1 text-sm text-muted">{t("req.subtitle")}</p>
        </div>
        {can.write(me) && (
          <Link href="/requests/new" className="flex min-h-11 items-center rounded-lg bg-brand-600 px-5 text-sm font-semibold text-white hover:bg-brand-700">+ {t("req.new")}</Link>
        )}
      </div>

      <section className="mb-5 rounded-xl border border-line bg-white px-6 py-5">
        <h2 className="mb-3.5 text-base font-semibold">{t("req.path.title")}</h2>
        <RequestPath />
      </section>

      <section className="mb-5 flex flex-wrap gap-3 rounded-xl border border-line bg-white p-5">
        <label className="flex min-w-[180px] flex-[1_1_180px] flex-col gap-1.5 text-[13px] font-medium">
          <span>{t("req.filter.status")}</span>
          <Select value={status} onChange={reset(setStatus)}>
            <option value="">{t("req.filter.allStatuses")}</option>
            {STATUSES.filter((s) => me.role !== "REGIONAL" || s !== "DRAFT").map((s) => <option key={s} value={s}>{label("RequestStatus", s)}</option>)}
          </Select>
        </label>
        <label className="flex min-w-[180px] flex-[1_1_180px] flex-col gap-1.5 text-[13px] font-medium">
          <span>{t("req.filter.type")}</span>
          <Select value={type} onChange={reset(setType)}>
            <option value="">{t("req.filter.allTypes")}</option>
            <option value="CREATE">{t("req.info.type.create")}</option>
            <option value="MODIFY">{t("req.info.type.modify")}</option>
          </Select>
        </label>
        <label className="flex min-w-[180px] flex-[1_1_180px] flex-col gap-1.5 text-[13px] font-medium">
          <span>{t("req.filter.center")}</span>
          <Select value={centerId} onChange={reset(setCenterId)}>
            <option value="">{t("req.filter.allCenters")}</option>
            {centers.data?.map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
          </Select>
        </label>
        <label className="flex min-w-[180px] flex-[2_1_240px] flex-col gap-1.5 text-[13px] font-medium">
          <span>{t("common.search")}</span>
          <Input placeholder={t("req.search")} value={search} onChange={(e) => setSearch(e.target.value)} />
        </label>
        <div className="flex items-end pb-2.5">
          <Checkbox label={t("req.filter.actionable")} checked={actionable} onChange={(e) => { setActionable(e.target.checked); setPage(0); }} />
        </div>
      </section>

      {list.isError ? <ErrorState error={list.error} /> : (
        <DataTable columns={columns} data={list.data?.content ?? []} loading={list.isPending}
          sorting={sorting} onSortingChange={(s) => { setSorting(s); setPage(0); }}
          page={page} pageSize={PAGE_SIZE} total={list.data?.totalElements ?? 0} onPageChange={setPage}
          onRowClick={(r) => router.push(`/requests/${r.id}`)} />
      )}
    </>
  );
}
