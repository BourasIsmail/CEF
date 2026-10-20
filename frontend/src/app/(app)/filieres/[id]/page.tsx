"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowDown, ArrowUp, Pencil, Plus, Trash2 } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { FiliereFormModal } from "@/components/filieres/filiere-form-modal";
import { ModuleFormModal } from "@/components/filieres/module-form-modal";
import { Button } from "@/components/ui/button";
import { Badge, Card, PageHeader } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm";
import { ErrorState, Spinner } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { errorText } from "@/lib/errors";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import type { FiliereDetail, ModuleView } from "@/lib/types";

export default function FiliereDetailPage() {
  const { id } = useParams<{ id: string }>();
  const me = useMe();
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();
  const { t, name, num } = useI18n();
  const admin = can.admin(me);
  const [editing, setEditing] = useState(false);
  const [module, setModule] = useState<ModuleView | "new" | null>(null);
  const [deletingModule, setDeletingModule] = useState<ModuleView | null>(null);
  const [deleting, setDeleting] = useState(false);

  const q = useQuery({ queryKey: ["filieres", id], queryFn: () => api<FiliereDetail>(`/filieres/${id}`) });
  const refresh = () => { qc.invalidateQueries({ queryKey: ["filieres"] }); };
  const fail = (e: unknown) => toast.error(errorText(e, t));

  const reorder = useMutation({
    mutationFn: (ids: number[]) => api(`/filieres/${id}/modules/order`, { method: "PUT", body: { moduleIds: ids } }),
    onSuccess: refresh, onError: fail,
  });
  const removeModule = useMutation({
    mutationFn: (m: ModuleView) => api(`/filieres/${id}/modules/${m.id}`, { method: "DELETE" }),
    onSuccess: () => { refresh(); setDeletingModule(null); }, onError: (e) => { setDeletingModule(null); fail(e); },
  });
  const removeFiliere = useMutation({
    mutationFn: () => api(`/filieres/${id}`, { method: "DELETE" }),
    onSuccess: () => { refresh(); toast.success(t("filieres.deleted")); router.replace("/filieres"); },
    onError: (e) => { setDeleting(false); fail(e); },
  });

  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  const { summary: f, modules, centers } = q.data;

  const move = (i: number, d: -1 | 1) => {
    const ids = modules.map((m) => m.id);
    [ids[i], ids[i + d]] = [ids[i + d], ids[i]];
    reorder.mutate(ids);
  };
  const totalCoef = modules.reduce((s, m) => s + m.coefficient, 0);
  const totalHours = modules.reduce((s, m) => s + m.durationHours, 0);

  return (
    <>
      <Link href="/filieres" className="mb-2 inline-block text-sm text-brand-700 hover:underline">← {t("filieres.title")}</Link>
      <PageHeader
        title={<span className="flex items-center gap-3">{name(f)} <Badge tone={f.active ? "ok" : "neutral"}>{t(f.active ? "common.active" : "common.inactive")}</Badge></span>}
        subtitle={<><span className="ltr-field inline-block">{f.code}</span> · {t("filieres.months", { n: f.durationMonths })}</>}
        actions={admin && <>
          <Button variant="secondary" icon={<Pencil className="size-4" />} onClick={() => setEditing(true)}>{t("common.edit")}</Button>
          <Button variant="danger" icon={<Trash2 className="size-4" />} onClick={() => setDeleting(true)}>{t("common.delete")}</Button>
        </>}
      />

      <div className="space-y-6">
        <Card title={t("modules.title")} actions={admin && <Button icon={<Plus className="size-4" />} onClick={() => setModule("new")}>{t("modules.add")}</Button>}>
          <p className="mb-3 text-xs text-muted">{t("modules.hint")}</p>
          {modules.length === 0 ? <p className="text-sm text-muted">{t("modules.empty")}</p> : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="text-muted">
                  <tr>
                    <th className="px-3 py-2 text-start font-semibold">{t("modules.col.position")}</th>
                    <th className="px-3 py-2 text-start font-semibold">{t("modules.col.name")}</th>
                    <th className="px-3 py-2 text-start font-semibold">{t("modules.col.coefficient")}</th>
                    <th className="px-3 py-2 text-start font-semibold">{t("modules.col.hours")}</th>
                    {admin && <th className="px-3 py-2 text-start font-semibold">{t("common.actions")}</th>}
                  </tr>
                </thead>
                <tbody>
                  {modules.map((m, i) => (
                    <tr key={m.id} className="border-t border-line">
                      <td className="px-3 py-2 tabular-nums">{num(m.position)}</td>
                      <td className="px-3 py-2 font-medium">{name(m)}</td>
                      <td className="px-3 py-2 tabular-nums">{num(m.coefficient, 2)}</td>
                      <td className="px-3 py-2 tabular-nums">{num(m.durationHours)}</td>
                      {admin && (
                        <td className="px-3 py-2">
                          <div className="flex gap-1">
                            <Button variant="ghost" className="px-2" disabled={i === 0 || reorder.isPending} title={t("modules.moveUp")} aria-label={t("modules.moveUp")} onClick={() => move(i, -1)}><ArrowUp className="size-4" /></Button>
                            <Button variant="ghost" className="px-2" disabled={i === modules.length - 1 || reorder.isPending} title={t("modules.moveDown")} aria-label={t("modules.moveDown")} onClick={() => move(i, 1)}><ArrowDown className="size-4" /></Button>
                            <Button variant="ghost" className="px-2" title={t("common.edit")} aria-label={t("common.edit")} onClick={() => setModule(m)}><Pencil className="size-4" /></Button>
                            <Button variant="ghost" className="px-2 text-danger" title={t("common.delete")} aria-label={t("common.delete")} onClick={() => setDeletingModule(m)}><Trash2 className="size-4" /></Button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t-2 border-line font-semibold">
                    <td className="px-3 py-2" colSpan={2}>{t("modules.total")}</td>
                    <td className="px-3 py-2 tabular-nums">{num(totalCoef, 2)}</td>
                    <td className="px-3 py-2 tabular-nums">{num(totalHours)}</td>
                    {admin && <td />}
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </Card>

        <Card title={t("filieres.centersList")}>
          {centers.length === 0 ? <p className="text-sm text-muted">{t("filieres.noCenters")}</p> : (
            <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {centers.map((c) => (
                <li key={c.id}><Link href={`/centers/${c.id}`} className="block rounded-lg border border-line px-3 py-2 text-sm hover:bg-brand-50">
                  <span className="ltr-field me-2 inline-block text-xs text-muted">{c.code}</span>{c.nameAr}
                </Link></li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <FiliereFormModal filiere={f} open={editing} onClose={() => setEditing(false)} />
      <ModuleFormModal filiereId={f.id} module={module} onClose={() => setModule(null)} />
      <ConfirmDialog open={!!deletingModule} danger loading={removeModule.isPending} title={t("common.delete")}
        message={t("modules.delete.confirm", { name: deletingModule ? name(deletingModule) : "" })}
        onClose={() => setDeletingModule(null)} onConfirm={() => deletingModule && removeModule.mutate(deletingModule)} />
      <ConfirmDialog open={deleting} danger loading={removeFiliere.isPending} title={t("common.delete")}
        message={t("filieres.delete.confirm", { name: name(f) })} onClose={() => setDeleting(false)} onConfirm={() => removeFiliere.mutate()} />
    </>
  );
}
