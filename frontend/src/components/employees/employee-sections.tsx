"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeftRight, Pencil, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm";
import { Field, Input, Select } from "@/components/ui/form";
import { Modal } from "@/components/ui/modal";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n } from "@/lib/i18n";
import { useCentersOptions, useSeasons } from "@/lib/queries";
import type { CenterFiliere, EmployeeDetail, FiliereSummary, GuestContractView, ModuleView, PageResponse, TeachingView } from "@/lib/types";

const useRefresh = () => {
  const qc = useQueryClient();
  return () => { qc.invalidateQueries({ queryKey: ["employees"] }); qc.invalidateQueries({ queryKey: ["centers"] }); };
};

// ---------------------------------------------------------------- affectations

export function AssignmentsCard({ employee, writer }: { employee: EmployeeDetail; writer: boolean }) {
  const { t, date } = useI18n();
  const [open, setOpen] = useState(false);
  return (
    <Card title={t("hr.section.assignments")} actions={writer && employee.employeeType !== "PEDAGOGICAL_COORDINATOR" &&
      <Button variant="secondary" icon={<ArrowLeftRight className="size-4" />} onClick={() => setOpen(true)}>{t("hr.transfer")}</Button>}>
      <ol className="space-y-3">
        {employee.assignments.map((a) => (
          <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-line px-4 py-2 text-sm">
            <div>
              <div className="font-medium">{a.centerNameAr ?? t("hr.provincial")}{a.positionTitle ? ` — ${a.positionTitle}` : ""}</div>
              {a.decisionReference && <div className="text-xs text-muted">{a.decisionReference}</div>}
            </div>
            <div className="text-muted">{t("hr.assign.from")} {date(a.fromDate)} {a.toDate ? `${t("hr.assign.to")} ${date(a.toDate)}` : `· ${t("hr.assign.current")}`}</div>
          </li>
        ))}
      </ol>
      <Modal open={open} onClose={() => setOpen(false)} title={t("hr.transfer")}>
        {open && <TransferForm employee={employee} onClose={() => setOpen(false)} />}
      </Modal>
    </Card>
  );
}

interface TransferValues { fromDate: string; centerId: string; positionTitle: string; decisionReference: string }

function TransferForm({ employee, onClose }: { employee: EmployeeDetail; onClose: () => void }) {
  const { t, name } = useI18n();
  const toast = useToast();
  const refresh = useRefresh();
  const [formError, setFormError] = useState<string>();
  const centers = useCentersOptions(employee.provincialDirectorateId);
  const canBeProvincial = employee.employeeType === "ADMINISTRATIVE";
  const { register, handleSubmit, setError, formState: { errors } } = useForm<TransferValues>({
    defaultValues: { fromDate: new Date().toISOString().slice(0, 10), centerId: "", positionTitle: "", decisionReference: "" },
  });
  const save = useMutation({
    mutationFn: (v: TransferValues) => api(`/employees/${employee.id}/transfers`, {
      method: "POST",
      body: { fromDate: v.fromDate, centerId: v.centerId ? Number(v.centerId) : undefined, positionTitle: v.positionTitle || undefined, decisionReference: v.decisionReference || undefined },
    }),
    onSuccess: () => { refresh(); toast.success(t("hr.saved")); onClose(); },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(errorText(e, t)); },
  });
  return (
    <form onSubmit={handleSubmit((v) => { setFormError(undefined); save.mutate(v); })} className="space-y-4" noValidate>
      <Field label={t("hr.transfer.from")} required error={errors.fromDate?.message}><Input type="date" className="ltr-field" {...register("fromDate", { required: t("common.required") })} /></Field>
      <Field label={t("hr.transfer.center")} required={!canBeProvincial} error={errors.centerId?.message}>
        <Select invalid={!!errors.centerId} {...register("centerId", { validate: (x) => canBeProvincial || !!x || t("common.required") })}>
          <option value="">{canBeProvincial ? t("hr.transfer.provincialOnly") : t("common.select")}</option>
          {centers.data?.filter((c) => c.id !== employee.centerId).map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
        </Select>
      </Field>
      <Field label={t("hr.transfer.position")}><Input {...register("positionTitle")} /></Field>
      <Field label={t("hr.transfer.decision")}><Input {...register("decisionReference")} /></Field>
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- enseignement

export function TeachingCard({ employee, writer }: { employee: EmployeeDetail; writer: boolean }) {
  const { t } = useI18n();
  const toast = useToast();
  const refresh = useRefresh();
  const [open, setOpen] = useState(false);
  const [deleting, setDeleting] = useState<TeachingView | null>(null);
  const remove = useMutation({
    mutationFn: (x: TeachingView) => api(`/employees/${employee.id}/teachings/${x.id}`, { method: "DELETE" }),
    onSuccess: () => { refresh(); setDeleting(null); }, onError: (e) => { setDeleting(null); toast.error(errorText(e, t)); },
  });
  const teaches = employee.employeeType === "PEDAGOGICAL" || employee.employeeType === "GUEST_TRAINER";
  if (!teaches) return null;
  return (
    <Card title={t("hr.section.teaching")} actions={writer && <Button variant="secondary" icon={<Plus className="size-4" />} onClick={() => setOpen(true)}>{t("hr.teaching.add")}</Button>}>
      {employee.teachings.length === 0 && <p className="text-sm text-muted">{t("hr.teaching.none")}</p>}
      <ul className="space-y-2">
        {employee.teachings.map((x) => (
          <li key={x.id} className="flex items-center justify-between gap-3 rounded-lg border border-line px-4 py-2 text-sm">
            <span><b>{x.seasonLabel}</b> · {x.filiereNameAr} · {x.moduleNameAr ?? t("hr.teaching.wholeFiliere")}</span>
            {writer && <Button variant="ghost" className="px-2 text-danger" aria-label={t("common.delete")} onClick={() => setDeleting(x)}><Trash2 className="size-4" /></Button>}
          </li>
        ))}
      </ul>
      <Modal open={open} onClose={() => setOpen(false)} title={t("hr.teaching.add")}>{open && <TeachingForm employee={employee} onClose={() => setOpen(false)} />}</Modal>
      <ConfirmDialog open={!!deleting} danger loading={remove.isPending} title={t("common.delete")} message={t("hr.teaching.delete.confirm")}
        onClose={() => setDeleting(null)} onConfirm={() => deleting && remove.mutate(deleting)} />
    </Card>
  );
}

function useEmployeeFilieres(employee: EmployeeDetail) {
  const all = useQuery({
    queryKey: ["filieres", "options"], enabled: !employee.centerId,
    queryFn: () => api<PageResponse<FiliereSummary>>("/filieres", { query: { size: 100, active: true, sort: "code,asc" } }).then((p) => p.content),
  });
  const atCenter = useQuery({
    queryKey: ["centers", employee.centerId, "filieres"], enabled: !!employee.centerId,
    queryFn: () => api<CenterFiliere[]>(`/centers/${employee.centerId}/filieres`),
  });
  return employee.centerId
    ? (atCenter.data ?? []).filter((f) => f.active).map((f) => ({ id: f.filiereId, nameAr: f.nameAr, nameFr: f.nameFr }))
    : (all.data ?? []).map((f) => ({ id: f.id, nameAr: f.nameAr, nameFr: f.nameFr }));
}

interface TeachingValues { seasonId: string; filiereId: string; moduleId: string }

function TeachingForm({ employee, onClose }: { employee: EmployeeDetail; onClose: () => void }) {
  const { t, name } = useI18n();
  const toast = useToast();
  const refresh = useRefresh();
  const [formError, setFormError] = useState<string>();
  const seasons = useSeasons();
  const filieres = useEmployeeFilieres(employee);
  const { register, handleSubmit, control, setValue, setError, formState: { errors } } = useForm<TeachingValues>({ defaultValues: { seasonId: "", filiereId: "", moduleId: "" } });
  const filiereId = useWatch({ control, name: "filiereId" });
  const modules = useQuery({ queryKey: ["filieres", filiereId, "modules"], enabled: !!filiereId, queryFn: () => api<ModuleView[]>(`/filieres/${filiereId}/modules`) });
  const save = useMutation({
    mutationFn: (v: TeachingValues) => api(`/employees/${employee.id}/teachings`, {
      method: "POST", body: { seasonId: Number(v.seasonId), filiereId: Number(v.filiereId), moduleId: v.moduleId ? Number(v.moduleId) : undefined },
    }),
    onSuccess: () => { refresh(); toast.success(t("hr.saved")); onClose(); },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(errorText(e, t)); },
  });
  const required = { required: t("common.required") };
  return (
    <form onSubmit={handleSubmit((v) => { setFormError(undefined); save.mutate(v); })} className="space-y-4" noValidate>
      <Field label={t("hr.teaching.season")} required error={errors.seasonId?.message}>
        <Select invalid={!!errors.seasonId} {...register("seasonId", required)}>
          <option value="">{t("common.select")}</option>
          {seasons.data?.map((s) => <option key={s.id} value={s.id}>{s.label}{s.current ? " ✓" : ""}</option>)}
        </Select>
      </Field>
      <Field label={t("hr.teaching.filiere")} required error={errors.filiereId?.message}>
        <Select invalid={!!errors.filiereId} {...register("filiereId", { ...required, onChange: () => setValue("moduleId", "") })}>
          <option value="">{t("common.select")}</option>
          {filieres.map((f) => <option key={f.id} value={f.id}>{name(f)}</option>)}
        </Select>
      </Field>
      <Field label={t("hr.teaching.module")}>
        <Select disabled={!filiereId} {...register("moduleId")}>
          <option value="">{t("hr.teaching.wholeFiliere")}</option>
          {modules.data?.map((m) => <option key={m.id} value={m.id}>{name(m)}</option>)}
        </Select>
      </Field>
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- prestations du formateur invité

export function GuestContractsCard({ employee, writer }: { employee: EmployeeDetail; writer: boolean }) {
  const { t, num, date } = useI18n();
  const toast = useToast();
  const refresh = useRefresh();
  const [editing, setEditing] = useState<GuestContractView | "new" | null>(null);
  const [deleting, setDeleting] = useState<GuestContractView | null>(null);
  const remove = useMutation({
    mutationFn: (x: GuestContractView) => api(`/employees/${employee.id}/guest-contracts/${x.id}`, { method: "DELETE" }),
    onSuccess: () => { refresh(); setDeleting(null); }, onError: (e) => { setDeleting(null); toast.error(errorText(e, t)); },
  });
  if (employee.employeeType !== "GUEST_TRAINER") return null;
  return (
    <Card title={t("hr.section.guest")} actions={writer && <Button variant="secondary" icon={<Plus className="size-4" />} onClick={() => setEditing("new")}>{t("hr.guest.add")}</Button>}>
      {employee.guestContracts.length === 0 && <p className="text-sm text-muted">{t("hr.guest.none")}</p>}
      <div className="space-y-3">
        {employee.guestContracts.map((c) => (
          <div key={c.id} className="rounded-lg border border-line p-4 text-sm">
            <div className="mb-2 flex items-center justify-between">
              <b>{c.seasonLabel} · {c.filiereNameAr}</b>
              {writer && <div className="flex gap-1">
                <Button variant="ghost" className="px-2" aria-label={t("common.edit")} onClick={() => setEditing(c)}><Pencil className="size-4" /></Button>
                <Button variant="ghost" className="px-2 text-danger" aria-label={t("common.delete")} onClick={() => setDeleting(c)}><Trash2 className="size-4" /></Button>
              </div>}
            </div>
            <dl className="grid gap-x-6 gap-y-1 sm:grid-cols-2 lg:grid-cols-3">
              <div><dt className="text-muted">{t("hr.guest.diplomaYear")}</dt><dd>{c.diplomaYear ?? "—"}</dd></div>
              <div><dt className="text-muted">{t("hr.guest.start")}</dt><dd>{date(c.workStartDate)}</dd></div>
              <div><dt className="text-muted">{t("hr.guest.rate")}</dt><dd>{c.hourlyRateMad != null ? num(c.hourlyRateMad, 2) : "—"}</dd></div>
              <div><dt className="text-muted">{t("hr.guest.months")}</dt><dd>{c.monthsPerYear ?? "—"}</dd></div>
              <div><dt className="text-muted">{t("hr.guest.hours")}</dt><dd>{c.hoursPerMonth != null ? num(c.hoursPerMonth, 2) : "—"}</dd></div>
              <div><dt className="text-muted">{t("hr.guest.count")}</dt><dd>{c.beneficiariesCount ?? "—"}</dd></div>
              <div className="sm:col-span-2 lg:col-span-3"><dt className="text-muted">{t("hr.guest.cost")}</dt><dd className="font-semibold">{c.estimatedAnnualCostMad != null ? num(c.estimatedAnnualCostMad, 2) : "—"}</dd></div>
            </dl>
          </div>
        ))}
      </div>
      <Modal open={!!editing} onClose={() => setEditing(null)} title={t("hr.section.guest")}>
        {editing && <GuestForm employee={employee} contract={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
      </Modal>
      <ConfirmDialog open={!!deleting} danger loading={remove.isPending} title={t("common.delete")} message={t("hr.guest.delete.confirm")}
        onClose={() => setDeleting(null)} onConfirm={() => deleting && remove.mutate(deleting)} />
    </Card>
  );
}

interface GuestValues { seasonId: string; filiereId: string; diplomaYear: string; workStartDate: string; hourlyRateMad: string; monthsPerYear: string; hoursPerMonth: string; beneficiariesCount: string }

function GuestForm({ employee, contract, onClose }: { employee: EmployeeDetail; contract: GuestContractView | null; onClose: () => void }) {
  const { t, name } = useI18n();
  const toast = useToast();
  const refresh = useRefresh();
  const [formError, setFormError] = useState<string>();
  const seasons = useSeasons();
  const filieres = useEmployeeFilieres(employee);
  const s = (v?: number | string | null) => (v == null ? "" : String(v));
  const { register, handleSubmit, setError, formState: { errors } } = useForm<GuestValues>({
    defaultValues: {
      seasonId: s(contract?.seasonId), filiereId: s(contract?.filiereId), diplomaYear: s(contract?.diplomaYear), workStartDate: contract?.workStartDate ?? "",
      hourlyRateMad: s(contract?.hourlyRateMad), monthsPerYear: s(contract?.monthsPerYear), hoursPerMonth: s(contract?.hoursPerMonth), beneficiariesCount: s(contract?.beneficiariesCount),
    },
  });
  const n = (x: string) => (x === "" ? undefined : Number(x));
  const save = useMutation({
    mutationFn: (v: GuestValues) => {
      const body = {
        seasonId: Number(v.seasonId), filiereId: Number(v.filiereId), diplomaYear: n(v.diplomaYear), workStartDate: v.workStartDate || undefined,
        hourlyRateMad: n(v.hourlyRateMad), monthsPerYear: n(v.monthsPerYear), hoursPerMonth: n(v.hoursPerMonth), beneficiariesCount: n(v.beneficiariesCount),
      };
      return contract
        ? api(`/employees/${employee.id}/guest-contracts/${contract.id}`, { method: "PUT", body })
        : api(`/employees/${employee.id}/guest-contracts`, { method: "POST", body });
    },
    onSuccess: () => { refresh(); toast.success(t("hr.saved")); onClose(); },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(errorText(e, t)); },
  });
  const required = { required: t("common.required") };
  return (
    <form onSubmit={handleSubmit((v) => { setFormError(undefined); save.mutate(v); })} className="space-y-4" noValidate>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("hr.guest.season")} required error={errors.seasonId?.message}>
          <Select invalid={!!errors.seasonId} {...register("seasonId", required)}>
            <option value="">{t("common.select")}</option>
            {seasons.data?.map((x) => <option key={x.id} value={x.id}>{x.label}</option>)}
          </Select>
        </Field>
        <Field label={t("hr.guest.filiere")} required error={errors.filiereId?.message}>
          <Select invalid={!!errors.filiereId} {...register("filiereId", required)}>
            <option value="">{t("common.select")}</option>
            {filieres.map((f) => <option key={f.id} value={f.id}>{name(f)}</option>)}
          </Select>
        </Field>
        <Field label={t("hr.guest.diplomaYear")} error={errors.diplomaYear?.message}><Input type="number" min={1950} max={2100} className="ltr-field" {...register("diplomaYear")} /></Field>
        <Field label={t("hr.guest.start")}><Input type="date" className="ltr-field" {...register("workStartDate")} /></Field>
        <Field label={t("hr.guest.rate")} error={errors.hourlyRateMad?.message}><Input type="number" step="0.01" min={0} className="ltr-field" {...register("hourlyRateMad")} /></Field>
        <Field label={t("hr.guest.months")} error={errors.monthsPerYear?.message}><Input type="number" min={1} max={12} className="ltr-field" {...register("monthsPerYear")} /></Field>
        <Field label={t("hr.guest.hours")} error={errors.hoursPerMonth?.message}><Input type="number" step="0.01" min={0} className="ltr-field" {...register("hoursPerMonth")} /></Field>
        <Field label={t("hr.guest.count")} error={errors.beneficiariesCount?.message}><Input type="number" min={0} className="ltr-field" {...register("beneficiariesCount")} /></Field>
      </div>
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}

