"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { AssignCentersModal } from "@/components/filieres/assign-centers-modal";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm";
import { Field, Input, Select } from "@/components/ui/form";
import { ErrorState, Spinner } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { errorText } from "@/lib/errors";
import { useI18n } from "@/lib/i18n";
import { useProvinces } from "@/lib/queries";
import type { FiliereDetail, ModuleView, Program } from "@/lib/types";

interface ModuleRow { key: string; id?: number; nameAr: string; nameFr?: string; coefficient: string; durationHours: string }

const toRow = (m: ModuleView): ModuleRow => ({ key: `m${m.id}`, id: m.id, nameAr: m.nameAr, nameFr: m.nameFr, coefficient: String(m.coefficient), durationHours: String(m.durationHours) });
const sectionCls = "flex flex-col gap-4 rounded-xl border border-line bg-white p-6";
const cellInput = "h-10 w-full rounded-lg border border-[#bcc1c9] bg-white px-2.5 text-sm disabled:bg-[#eef0f3]";

/** Panneau de droite de la page « الشعب والوحدات » : données, modules (édition en ligne) et centres de la filière. */
export function FiliereEditor({ id, admin }: { id: number; admin: boolean }) {
  const q = useQuery({ queryKey: ["filieres", String(id)], queryFn: () => api<FiliereDetail>(`/filieres/${id}`) });
  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  return <Editor key={`${id}-${q.data.updatedAt}`} detail={q.data} admin={admin} />;
}

function Editor({ detail, admin }: { detail: FiliereDetail; admin: boolean }) {
  const { t, name } = useI18n();
  const toast = useToast();
  const qc = useQueryClient();
  const router = useRouter();
  const f = detail.summary;
  const provinces = useProvinces();
  const programs = useQuery({ queryKey: ["programs"], queryFn: () => api<Program[]>("/programs"), staleTime: 10 * 60_000 });

  const [nameAr, setNameAr] = useState(f.nameAr);
  const [nameFr, setNameFr] = useState(f.nameFr);
  const [duration, setDuration] = useState(String(f.durationMonths));
  const [programId, setProgramId] = useState(String(f.programId));
  const [rows, setRows] = useState<ModuleRow[]>(() => detail.modules.map(toRow));
  const [assigning, setAssigning] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string>();

  const totalCoef = rows.reduce((s, r) => s + (Number(r.coefficient) || 0), 0);
  const totalHours = rows.reduce((s, r) => s + (Number(r.durationHours) || 0), 0);
  const { num } = useI18n();

  const byProvince = useMemo(() => {
    const map = new Map<number, { id: number; label: string; centers: FiliereDetail["centers"] }>();
    for (const c of detail.centers) {
      const p = provinces.data?.find((x) => x.id === c.provinceId);
      const entry = map.get(c.provinceId) ?? { id: c.provinceId, label: p ? name(p) : "", centers: [] };
      entry.centers.push(c);
      map.set(c.provinceId, entry);
    }
    return [...map.values()];
  }, [detail.centers, provinces.data, name]);

  const refresh = () => qc.invalidateQueries({ queryKey: ["filieres"] });
  const fail = (e: unknown) => { setError(errorText(e, t)); toast.error(errorText(e, t)); };

  const setCenters = useMutation({
    mutationFn: (centerIds: number[]) => api<FiliereDetail>(`/filieres/${f.id}/centers`, { method: "PUT", body: { centerIds } }),
    onSuccess: () => { refresh(); setAssigning(false); toast.success(t("filieres.saved")); },
    onError: fail,
  });

  const remove = useMutation({
    mutationFn: () => api(`/filieres/${f.id}`, { method: "DELETE" }),
    onSuccess: () => { refresh(); toast.success(t("filieres.deleted")); router.replace("/filieres"); },
    onError: (e) => { setDeleting(false); fail(e); },
  });

  const save = useMutation({
    mutationFn: async () => {
      await api(`/filieres/${f.id}`, {
        method: "PUT",
        body: { programId: Number(programId), code: f.code, nameAr, nameFr, durationMonths: Number(duration), active: f.active },
      });
      const keep = new Set(rows.filter((r) => r.id).map((r) => r.id));
      for (const m of detail.modules) if (!keep.has(m.id)) await api(`/filieres/${f.id}/modules/${m.id}`, { method: "DELETE" });
      const ids: number[] = [];
      for (const r of rows) {
        const body = { nameAr: r.nameAr, nameFr: r.nameFr || undefined, coefficient: Number(r.coefficient), durationHours: Number(r.durationHours) };
        if (r.id) {
          const before = detail.modules.find((m) => m.id === r.id);
          if (before && (before.nameAr !== r.nameAr || before.coefficient !== body.coefficient || before.durationHours !== body.durationHours)) {
            await api(`/filieres/${f.id}/modules/${r.id}`, { method: "PUT", body });
          }
          ids.push(r.id);
        } else {
          ids.push((await api<ModuleView>(`/filieres/${f.id}/modules`, { method: "POST", body })).id);
        }
      }
      const current = detail.modules.filter((m) => keep.has(m.id)).map((m) => m.id);
      if (ids.length > 1 && (ids.length !== current.length || ids.some((x, i) => x !== current[i]))) {
        await api(`/filieres/${f.id}/modules/order`, { method: "PUT", body: { moduleIds: ids } });
      }
    },
    onSuccess: () => { refresh(); toast.success(t("filieres.saved")); },
    onError: fail,
  });

  function submit() {
    setError(undefined);
    if (!nameAr.trim() || !nameFr.trim()) return setError(t("common.required"));
    const d = Number(duration);
    if (!Number.isInteger(d) || d < 1 || d > 60) return setError(t("filieres.field.duration"));
    for (const r of rows) {
      if (!r.nameAr.trim() || !(Number(r.coefficient) > 0) || !(Number(r.durationHours) >= 1)) return setError(t("modules.title"));
    }
    save.mutate();
  }

  const patch = (key: string, p: Partial<ModuleRow>) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...p } : r)));

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <section className={sectionCls}>
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-lg font-bold">{t("filieres.data")}</h2>
          {admin && <Button variant="ghost" className="text-danger" icon={<Trash2 className="size-4" />} onClick={() => setDeleting(true)}>{t("common.delete")}</Button>}
        </div>
        <div className="flex flex-wrap gap-4">
          <div className="min-w-[17rem] flex-[2]"><Field label={t("filieres.field.nameAr")}><Input value={nameAr} onChange={(e) => setNameAr(e.target.value)} disabled={!admin} /></Field></div>
          <div className="min-w-[17rem] flex-[2]"><Field label={t("filieres.field.nameFr")}><Input className="ltr-field" value={nameFr} onChange={(e) => setNameFr(e.target.value)} disabled={!admin} /></Field></div>
          <div className="min-w-40 flex-1"><Field label={t("filieres.field.duration")}><Input type="number" min={1} max={60} value={duration} onChange={(e) => setDuration(e.target.value)} disabled={!admin} /></Field></div>
          <div className="min-w-40 flex-1">
            <Field label={t("filieres.field.program")}>
              <Select value={programId} onChange={(e) => setProgramId(e.target.value)} disabled={!admin}>
                {(programs.data ?? [{ id: f.programId, code: f.programCode, nameAr: f.programCode, nameFr: f.programCode }]).map((p) => <option key={p.id} value={p.id}>{p.code}</option>)}
              </Select>
            </Field>
          </div>
        </div>
      </section>

      <section className={sectionCls}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-bold">{t("modules.title")}</h2>
          {admin && (
            <Button variant="secondary" onClick={() => setRows((rs) => [...rs, { key: `n${Date.now()}${rs.length}`, nameAr: "", coefficient: "1", durationHours: "30" }])}>
              + {t("modules.add2")}
            </Button>
          )}
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[#eef0f3] text-[#3f4a5a]">
                <th className="px-3 py-3 text-start font-semibold">{t("modules.col.position")}</th>
                <th className="px-3 py-3 text-start font-semibold">{t("modules.col.name")}</th>
                <th className="px-3 py-3 text-start font-semibold">{t("modules.col.coefficient")}</th>
                <th className="px-3 py-3 text-start font-semibold">{t("modules.col.hours")}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && <tr><td colSpan={5} className="px-3 py-4 text-muted">{t("modules.empty")}</td></tr>}
              {rows.map((r, i) => (
                <tr key={r.key} className="border-b border-line">
                  <td className="w-[70px] px-3 py-2 tabular-nums">{num(i + 1)}</td>
                  <td className="px-3 py-2"><input className={cellInput} aria-label={t("modules.col.name")} value={r.nameAr} disabled={!admin} onChange={(e) => patch(r.key, { nameAr: e.target.value })} /></td>
                  <td className="w-[110px] px-3 py-2"><input className={cellInput} type="number" step="0.01" min="0.01" aria-label={t("modules.col.coefficient")} value={r.coefficient} disabled={!admin} onChange={(e) => patch(r.key, { coefficient: e.target.value })} /></td>
                  <td className="w-[140px] px-3 py-2"><input className={cellInput} type="number" min="1" aria-label={t("modules.col.hours")} value={r.durationHours} disabled={!admin} onChange={(e) => patch(r.key, { durationHours: e.target.value })} /></td>
                  <td className="w-[90px] px-3 py-2">
                    {admin && <button type="button" className="min-h-10 font-semibold text-danger" onClick={() => setRows((rs) => rs.filter((x) => x.key !== r.key))}>{t("modules.col.actions")}</button>}
                  </td>
                </tr>
              ))}
              <tr className="bg-[#eef0f3] font-bold">
                <td className="px-3 py-3" colSpan={2}>{t("modules.total")}</td>
                <td className="px-3 py-3 tabular-nums">{num(totalCoef, 2)}</td>
                <td className="px-3 py-3 tabular-nums">{num(totalHours)}</td>
                <td />
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section className={sectionCls}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-bold">{t("filieres.assigned")}</h2>
          {admin && <Button variant="secondary" onClick={() => setAssigning(true)}>{t("filieres.assign")}</Button>}
        </div>
        {byProvince.length === 0 && <p className="text-sm text-muted">{t("filieres.noCenters")}</p>}
        {byProvince.map((p) => (
          <div key={p.id} className="flex flex-col gap-2">
            <div className="text-sm font-semibold text-[#3f4a5a]">{p.label}</div>
            <div className="flex flex-wrap gap-2">
              {p.centers.map((c) => (
                <span key={c.id} className="inline-flex items-center gap-2 rounded-full bg-[#e6eef9] px-3.5 py-2 text-sm text-[#093670]">
                  {c.nameAr}
                  {admin && (
                    <button type="button" aria-label={`${t("filieres.removeCenter")}: ${c.nameAr}`} title={t("filieres.removeCenter")}
                      disabled={setCenters.isPending}
                      onClick={() => setCenters.mutate(detail.centers.filter((x) => x.id !== c.id).map((x) => x.id))}>
                      <X className="size-4" />
                    </button>
                  )}
                </span>
              ))}
            </div>
          </div>
        ))}
      </section>

      {error && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{error}</p>}
      {admin && (
        <div className="flex flex-wrap gap-3 pb-6">
          <Button loading={save.isPending} onClick={submit}>{t("filieres.saveChanges")}</Button>
          <Button variant="secondary" onClick={() => { setRows(detail.modules.map(toRow)); setNameAr(f.nameAr); setNameFr(f.nameFr); setDuration(String(f.durationMonths)); setProgramId(String(f.programId)); setError(undefined); }}>{t("common.cancel")}</Button>
        </div>
      )}

      <AssignCentersModal open={assigning} onClose={() => setAssigning(false)} initial={detail.centers.map((c) => c.id)}
        loading={setCenters.isPending} onSave={(ids) => setCenters.mutate(ids)} />
      <ConfirmDialog open={deleting} danger loading={remove.isPending} title={t("common.delete")}
        message={t("filieres.delete.confirm", { name: name(f) })} onClose={() => setDeleting(false)} onConfirm={() => remove.mutate()} />
    </div>
  );
}
