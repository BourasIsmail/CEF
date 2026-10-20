"use client";

import { useQuery } from "@tanstack/react-query";
import type { ColumnDef, SortingState } from "@tanstack/react-table";
import clsx from "clsx";
import { Plus, Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { RequestStatusBadge } from "@/components/requests/request-badge";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Checkbox, Input, Select } from "@/components/ui/form";
import { ErrorState } from "@/components/ui/states";
import { api, type Query } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import type { PageResponse, RequestCounts, RequestStatus, RequestSummary } from "@/lib/types";

const PAGE_SIZE = 20;
const STATUSES: RequestStatus[] = ["DRAFT", "SUBMITTED", "RETURNED", "REGIONAL_VALIDATED", "APPROVED", "REJECTED"];

export default function RequestsPage() {
  const me = useMe();
  const router = useRouter();
  const { t, label, date, num } = useI18n();
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const [actionable, setActionable] = useState(false);
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "createdAt", desc: true }]);

  useEffect(() => {
    const id = setTimeout(() => { setQ(search.trim()); setPage(0); }, 300);
    return () => clearTimeout(id);
  }, [search]);

  const counts = useQuery({ queryKey: ["requests", "counts"], queryFn: () => api<RequestCounts>("/filiere-requests/counts") });
  const query: Query = {
    search: q, status, type, actionable: actionable || undefined, page, size: PAGE_SIZE,
    sort: sorting[0] ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}` : undefined,
  };
  const list = useQuery({
    queryKey: ["requests", "list", query],
    queryFn: () => api<PageResponse<RequestSummary>>("/filiere-requests", { query }),
    placeholderData: (prev) => prev,
  });

  const columns = useMemo<ColumnDef<RequestSummary>[]>(() => [
    { id: "number", header: t("req.col.number"), cell: ({ row }) => <span className="ltr-field font-semibold">{row.original.requestNumber}</span> },
    { id: "type", header: t("req.col.type"), cell: ({ row }) => label("RequestType", row.original.requestType) },
    { id: "name", header: t("req.col.name"), cell: ({ row }) => <span className="font-medium">{row.original.proposedNameAr}</span> },
    { id: "center", header: t("req.col.center"), cell: ({ row }) => <div>{row.original.centerNameAr}<div className="text-xs text-muted">{row.original.provinceNameAr}</div></div> },
    { id: "status", header: t("req.col.status"), cell: ({ row }) => <RequestStatusBadge status={row.original.status} /> },
    { id: "submittedAt", header: t("req.col.submitted"), cell: ({ row }) => row.original.submittedAt ? date(row.original.submittedAt) : "—" },
  ], [t, label, date]);

  return (
    <>
      <PageHeader title={t("req.title")} subtitle={t("req.subtitle")}
        actions={can.write(me) && <Button icon={<Plus className="size-4" />} onClick={() => router.push("/requests/new")}>{t("req.new")}</Button>} />

      {counts.data && (
        <div className="mb-4 flex flex-wrap gap-2">
          <button type="button" onClick={() => { setStatus(""); setPage(0); }}
            className={clsx("rounded-full border px-3 py-1 text-sm", status === "" ? "border-brand-600 bg-brand-50 text-brand-700" : "border-line bg-white text-muted")}>
            {t("req.counts.all")} · {num(counts.data.total)}
          </button>
          {STATUSES.filter((s) => me.role !== "REGIONAL" || s !== "DRAFT").map((s) => (
            <button key={s} type="button" onClick={() => { setStatus(s); setPage(0); }}
              className={clsx("rounded-full border px-3 py-1 text-sm", status === s ? "border-brand-600 bg-brand-50 text-brand-700" : "border-line bg-white text-muted")}>
              {label("RequestStatus", s)} · {num(counts.data.byStatus[s] ?? 0)}
            </button>
          ))}
        </div>
      )}

      <div className="mb-4 grid gap-3 rounded-xl border border-line bg-white p-4 sm:grid-cols-[1fr_12rem_auto]">
        <div className="relative">
          <Search className="pointer-events-none absolute inset-y-0 start-3 my-auto size-4 text-muted" aria-hidden />
          <Input className="ps-9" placeholder={t("req.search")} value={search} onChange={(e) => setSearch(e.target.value)} aria-label={t("common.search")} />
        </div>
        <Select value={type} onChange={(e) => { setType(e.target.value); setPage(0); }} aria-label={t("req.filter.type")}>
          <option value="">{t("req.filter.type")} — {t("common.all")}</option>
          <option value="CREATE">{label("RequestType", "CREATE")}</option>
          <option value="MODIFY">{label("RequestType", "MODIFY")}</option>
        </Select>
        <Checkbox label={t("req.filter.actionable")} checked={actionable} onChange={(e) => { setActionable(e.target.checked); setPage(0); }} />
      </div>

      {list.isError ? <ErrorState error={list.error} /> : (
        <DataTable columns={columns} data={list.data?.content ?? []} loading={list.isPending}
          sorting={sorting} onSortingChange={(s) => { setSorting(s); setPage(0); }}
          page={page} pageSize={PAGE_SIZE} total={list.data?.totalElements ?? 0} onPageChange={setPage}
          onRowClick={(r) => router.push(`/requests/${r.id}`)} />
      )}
    </>
  );
}
