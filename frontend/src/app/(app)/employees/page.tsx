"use client";

import { useQuery } from "@tanstack/react-query";
import type { ColumnDef, SortingState } from "@tanstack/react-table";
import { Plus, Search } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge, PageHeader } from "@/components/ui/card";
import { DataTable } from "@/components/ui/data-table";
import { Checkbox, Input, Select } from "@/components/ui/form";
import { ErrorState } from "@/components/ui/states";
import { api, type Query } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import { useCentersOptions } from "@/lib/queries";
import type { EmployeeSummary, PageResponse, StaffSummary } from "@/lib/types";

const PAGE_SIZE = 20;
const TYPES = ["PEDAGOGICAL", "CENTER_DIRECTOR", "ADMINISTRATIVE", "GUEST_TRAINER", "PEDAGOGICAL_COORDINATOR"] as const;
const STATUSES = ["ENTRAIDE_NATIONALE", "ASSOCIATION", "OTHER_SECTOR", "PROMOTION_NATIONALE", "FOREIGN_VOLUNTEER"] as const;
const ACTIVITIES = ["ACTIVE", "ON_LEAVE", "SECONDED", "SUSPENDED", "RETIRED"] as const;

export default function EmployeesPage() {
  const me = useMe();
  const router = useRouter();
  const params = useSearchParams();
  const { t, name, label, num } = useI18n();
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [centerId, setCenterId] = useState(params.get("centerId") ?? "");
  const [type, setType] = useState("");
  const [status, setStatus] = useState("");
  const [activity, setActivity] = useState("");
  const [without, setWithout] = useState(false);
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "nameAr", desc: false }]);

  useEffect(() => {
    const id = setTimeout(() => { setQ(search.trim()); setPage(0); }, 300);
    return () => clearTimeout(id);
  }, [search]);

  const centers = useCentersOptions();
  const summary = useQuery({ queryKey: ["employees", "summary", centerId], queryFn: () => api<StaffSummary>("/employees/summary", { query: { centerId } }) });

  const query: Query = {
    search: q, centerId, type, administrativeStatus: status, activityStatus: activity, withoutCenter: without || undefined, page, size: PAGE_SIZE,
    sort: sorting[0] ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}` : undefined,
  };
  const list = useQuery({
    queryKey: ["employees", "list", query],
    queryFn: () => api<PageResponse<EmployeeSummary>>("/employees", { query }),
    placeholderData: (prev) => prev,
  });

  const columns = useMemo<ColumnDef<EmployeeSummary>[]>(() => [
    {
      id: "nameAr", header: t("hr.col.name"),
      cell: ({ row }) => (
        <div>
          <div className="font-medium">{row.original.fullNameAr}</div>
          {row.original.fullNameFr && <div className="ltr-field text-xs text-muted">{row.original.fullNameFr}</div>}
        </div>
      ),
    },
    { id: "type", header: t("hr.col.type"), cell: ({ row }) => label("EmployeeType", row.original.employeeType) },
    { id: "administrativeStatus", header: t("hr.col.status"), cell: ({ row }) => <Badge tone="brand">{label("AdministrativeStatus", row.original.administrativeStatus)}</Badge> },
    { id: "center", header: t("hr.col.center"), cell: ({ row }) => row.original.centerNameAr ?? <span className="text-muted">{t("hr.provincial")}</span> },
    { id: "rankNumber", header: t("hr.col.rank"), cell: ({ row }) => <span className="ltr-field text-xs">{row.original.rankNumber ?? "—"}</span> },
    { id: "phone", header: t("hr.col.phone"), enableSorting: false, cell: ({ row }) => <span className="ltr-field text-xs">{row.original.phone ?? "—"}</span> },
    { id: "activityStatus", header: t("hr.col.activity"), cell: ({ row }) => <Badge tone={row.original.activityStatus === "ACTIVE" ? "ok" : "warn"}>{label("ActivityStatus", row.original.activityStatus)}</Badge> },
  ], [t, label]);

  const reset = (f: (v: string) => void) => (e: { target: { value: string } }) => { f(e.target.value); setPage(0); };

  return (
    <>
      <PageHeader title={t("hr.title")} subtitle={t("hr.subtitle")}
        actions={can.write(me) && <Button icon={<Plus className="size-4" />} onClick={() => router.push("/employees/new")}>{t("hr.new")}</Button>} />

      {summary.data && (
        <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-xl border border-line bg-white p-4">
            <div className="text-sm text-muted">{t("hr.summary.total")}</div>
            <div className="text-2xl font-bold text-brand-700">{num(summary.data.total)}</div>
            <div className="text-xs text-muted">{t("hr.summary.males")} {num(summary.data.male)} · {t("hr.summary.females")} {num(summary.data.female)}</div>
          </div>
          {(["PEDAGOGICAL", "GUEST_TRAINER", "CENTER_DIRECTOR"] as const).map((k) => (
            <div key={k} className="rounded-xl border border-line bg-white p-4">
              <div className="text-sm text-muted">{label("EmployeeType", k)}</div>
              <div className="text-2xl font-bold text-brand-700">{num(summary.data.byType[k] ?? 0)}</div>
            </div>
          ))}
        </div>
      )}

      <div className="mb-4 space-y-3 rounded-xl border border-line bg-white p-4">
        <div className="relative">
          <Search className="pointer-events-none absolute inset-y-0 start-3 my-auto size-4 text-muted" aria-hidden />
          <Input className="ps-9" placeholder={t("hr.search")} value={search} onChange={(e) => setSearch(e.target.value)} aria-label={t("common.search")} />
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Select value={centerId} onChange={reset(setCenterId)} aria-label={t("hr.filter.center")}>
            <option value="">{t("hr.filter.center")} — {t("common.all")}</option>
            {centers.data?.map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
          </Select>
          <Select value={type} onChange={reset(setType)} aria-label={t("hr.filter.type")}>
            <option value="">{t("hr.filter.type")} — {t("common.all")}</option>
            {TYPES.map((x) => <option key={x} value={x}>{label("EmployeeType", x)}</option>)}
          </Select>
          <Select value={status} onChange={reset(setStatus)} aria-label={t("hr.filter.status")}>
            <option value="">{t("hr.filter.status")} — {t("common.all")}</option>
            {STATUSES.map((x) => <option key={x} value={x}>{label("AdministrativeStatus", x)}</option>)}
          </Select>
          <Select value={activity} onChange={reset(setActivity)} aria-label={t("hr.filter.activity")}>
            <option value="">{t("hr.filter.activity")} — {t("common.all")}</option>
            {ACTIVITIES.map((x) => <option key={x} value={x}>{label("ActivityStatus", x)}</option>)}
          </Select>
        </div>
        <Checkbox label={t("hr.filter.withoutCenter")} checked={without} onChange={(e) => { setWithout(e.target.checked); setPage(0); }} />
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
