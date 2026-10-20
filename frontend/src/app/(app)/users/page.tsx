"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ColumnDef, SortingState } from "@tanstack/react-table";
import { KeyRound, Lock, Pencil, Plus, Power, Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { UserFormModal } from "@/components/users/user-form-modal";
import { ResetPasswordModal } from "@/components/users/reset-password-modal";
import { Button } from "@/components/ui/button";
import { Badge, PageHeader } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm";
import { DataTable } from "@/components/ui/data-table";
import { Input, Select } from "@/components/ui/form";
import { ErrorState } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api, type Query } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { errorKey } from "@/lib/errors";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import type { PageResponse, Role, UserView } from "@/lib/types";

const PAGE_SIZE = 20;

/** Gestion des comptes : réservée à l'administrateur. */
export default function UsersPage() {
  const me = useMe();
  const router = useRouter();
  const { t, dateTime } = useI18n();
  const toast = useToast();
  const qc = useQueryClient();

  const [search, setSearch] = useState("");
  const [debounced, setDebounced] = useState("");
  const [role, setRole] = useState<Role | "">("");
  const [enabled, setEnabled] = useState<"" | "true" | "false">("");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "username", desc: false }]);
  const [editing, setEditing] = useState<UserView | "new" | null>(null);
  const [resetting, setResetting] = useState<UserView | null>(null);
  const [disabling, setDisabling] = useState<UserView | null>(null);

  useEffect(() => {
    if (!can.manageUsers(me)) router.replace("/");
  }, [me, router]);

  useEffect(() => {
    const id = setTimeout(() => { setDebounced(search.trim()); setPage(0); }, 300);
    return () => clearTimeout(id);
  }, [search]);

  const query: Query = {
    search: debounced, role, enabled, page, size: PAGE_SIZE,
    sort: sorting[0] ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}` : undefined,
  };
  const list = useQuery({
    queryKey: ["users", query],
    queryFn: () => api<PageResponse<UserView>>("/users", { query }),
    enabled: can.manageUsers(me),
    placeholderData: (prev) => prev,
  });

  const setEnabledMutation = useMutation({
    mutationFn: ({ id, value }: { id: number; value: boolean }) => api<UserView>(`/users/${id}/enabled`, { method: "PUT", body: { enabled: value } }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["users"] }); setDisabling(null); toast.success(t("users.updated")); },
    onError: (e) => { setDisabling(null); toast.error(t(errorKey(e))); },
  });

  const scopeOf = (u: UserView) => u.provinceNameAr ?? u.regionNameAr ?? t("users.scopeAll");

  const columns = useMemo<ColumnDef<UserView>[]>(() => [
    { id: "username", header: t("users.col.username"), cell: ({ row }) => <span className="ltr-field font-medium">{row.original.username}</span> },
    { id: "fullName", header: t("users.col.fullName"), cell: ({ row }) => row.original.fullName },
    { id: "role", header: t("users.col.role"), cell: ({ row }) => <Badge tone="brand">{t(`role.${row.original.role}`)}</Badge> },
    { id: "scope", header: t("users.col.scope"), enableSorting: false, cell: ({ row }) => scopeOf(row.original) },
    {
      id: "enabled", header: t("users.col.status"),
      cell: ({ row }) => {
        const u = row.original;
        if (!u.enabled) return <Badge tone="danger">{t("users.status.disabled")}</Badge>;
        if (u.locked) return <Badge tone="warn">{t("users.status.locked")}</Badge>;
        return <Badge tone="ok">{t("users.status.enabled")}</Badge>;
      },
    },
    { id: "lastLoginAt", header: t("users.col.lastLogin"), cell: ({ row }) => row.original.lastLoginAt ? dateTime(row.original.lastLoginAt) : <span className="text-muted">{t("users.never")}</span> },
    {
      id: "actions", header: t("common.actions"), enableSorting: false,
      cell: ({ row }) => {
        const u = row.original;
        const self = u.id === me.id;
        return (
          <div className="flex gap-1">
            <Button variant="ghost" className="px-2" title={t("common.edit")} aria-label={t("common.edit")} onClick={() => setEditing(u)}><Pencil className="size-4" /></Button>
            <Button variant="ghost" className="px-2" title={t("users.resetPassword")} aria-label={t("users.resetPassword")} onClick={() => setResetting(u)}><KeyRound className="size-4" /></Button>
            {u.locked && u.enabled && (
              <Button variant="ghost" className="px-2" title={t("users.unlock")} aria-label={t("users.unlock")}
                onClick={() => setEnabledMutation.mutate({ id: u.id, value: true })}><Lock className="size-4" /></Button>
            )}
            {!self && (
              <Button variant="ghost" className="px-2" title={u.enabled ? t("users.disable") : t("users.enable")} aria-label={u.enabled ? t("users.disable") : t("users.enable")}
                onClick={() => (u.enabled ? setDisabling(u) : setEnabledMutation.mutate({ id: u.id, value: true }))}>
                <Power className={`size-4 ${u.enabled ? "" : "text-ok"}`} />
              </Button>
            )}
          </div>
        );
      },
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [t, dateTime, me.id]);

  if (!can.manageUsers(me)) return null;

  return (
    <>
      <PageHeader
        title={t("users.title")}
        subtitle={t("users.subtitle")}
        actions={<Button icon={<Plus className="size-4" />} onClick={() => setEditing("new")}>{t("users.new")}</Button>}
      />

      <div className="mb-4 grid gap-3 rounded-xl border border-line bg-white p-4 sm:grid-cols-[1fr_12rem_10rem]">
        <div className="relative">
          <Search className="pointer-events-none absolute inset-y-0 start-3 my-auto size-4 text-muted" aria-hidden />
          <Input className="ps-9" placeholder={t("users.search")} value={search} onChange={(e) => setSearch(e.target.value)} aria-label={t("common.search")} />
        </div>
        <Select value={role} onChange={(e) => { setRole(e.target.value as Role | ""); setPage(0); }} aria-label={t("users.col.role")}>
          <option value="">{t("users.col.role")} — {t("common.all")}</option>
          {(["ADMIN", "REGIONAL", "PROVINCIAL"] as const).map((r) => <option key={r} value={r}>{t(`role.${r}`)}</option>)}
        </Select>
        <Select value={enabled} onChange={(e) => { setEnabled(e.target.value as "" | "true" | "false"); setPage(0); }} aria-label={t("users.col.status")}>
          <option value="">{t("users.col.status")} — {t("common.all")}</option>
          <option value="true">{t("users.status.enabled")}</option>
          <option value="false">{t("users.status.disabled")}</option>
        </Select>
      </div>

      {list.isError ? <ErrorState error={list.error} /> : (
        <DataTable
          columns={columns} data={list.data?.content ?? []} loading={list.isPending}
          sorting={sorting} onSortingChange={(s) => { setSorting(s); setPage(0); }}
          page={page} pageSize={PAGE_SIZE} total={list.data?.totalElements ?? 0} onPageChange={setPage}
        />
      )}

      <UserFormModal user={editing} onClose={() => setEditing(null)} />
      <ResetPasswordModal user={resetting} onClose={() => setResetting(null)} />
      <ConfirmDialog
        open={!!disabling} danger loading={setEnabledMutation.isPending}
        title={t("users.disable")} message={t("users.disable.confirm", { name: disabling?.fullName ?? "" })}
        onClose={() => setDisabling(null)}
        onConfirm={() => disabling && setEnabledMutation.mutate({ id: disabling.id, value: false })}
      />
    </>
  );
}
