"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Award, FileText, Plus, Trash2 } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { ExamSessionModal } from "./exam-session-modal";
import { ResultBadge, StatusBadge } from "./status-badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { Modal } from "@/components/ui/modal";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n } from "@/lib/i18n";
import { useSeasons } from "@/lib/queries";
import type { BeneficiaryDetail, CenterFiliere, EnrollmentView, SessionType } from "@/lib/types";

type Dialog =
  | { kind: "enroll"; enrollment?: EnrollmentView }
  | { kind: "depart" | "diploma" | "delete" | "finalize" | "deleteDiploma"; enrollment: EnrollmentView }
  | { kind: "exam"; enrollment: EnrollmentView; type: SessionType }
  | null;

/** Inscriptions du bénéficiaire, cycle de vie (départ, clôture), résultats d'examen et diplôme. */
export function EnrollmentSection({ beneficiary, writer }: { beneficiary: BeneficiaryDetail; writer: boolean }) {
  const { t, label, date, num } = useI18n();
  const toast = useToast();
  const qc = useQueryClient();
  const [dialog, setDialog] = useState<Dialog>(null);
  const close = () => setDialog(null);
  const refresh = () => { qc.invalidateQueries({ queryKey: ["beneficiaries"] }); qc.invalidateQueries({ queryKey: ["bulletin"] }); qc.invalidateQueries({ queryKey: ["dashboard"] }); };
  const fail = (e: unknown) => { close(); toast.error(errorText(e, t)); };

  const action = useMutation({
    mutationFn: ({ path, method }: { path: string; method: "POST" | "DELETE" }) => api(path, { method }),
    onSuccess: () => { refresh(); close(); }, onError: fail,
  });

  const rows = beneficiary.enrollments;
  return (
    <Card title={t("ben.section.enrollments")} actions={writer && <Button icon={<Plus className="size-4" />} onClick={() => setDialog({ kind: "enroll" })}>{t("ben.enroll.add")}</Button>}>
      {rows.length === 0 && <p className="text-sm text-muted">{t("ben.enroll.none")}</p>}
      <div className="space-y-4">
        {rows.map((e) => (
          <div key={e.id} className="rounded-lg border border-line p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="flex flex-wrap items-center gap-2 text-base font-semibold">
                  {e.filiereNameAr} <StatusBadge status={e.status} />
                </div>
                <div className="mt-1 text-sm text-muted">
                  {e.seasonLabel} · {e.centerNameAr} · {date(e.startDate)}{e.examDate ? ` → ${t("ben.enroll.exam")} ${date(e.examDate)}` : ""}
                </div>
                {e.status === "DEPARTED" && <div className="mt-1 text-sm text-warn">{t("ben.enroll.departedOn", { date: date(e.departureDate) })} — {e.departureReason}</div>}
              </div>
              <div className="flex flex-wrap gap-2">
                <Link href={`/beneficiaries/${beneficiary.id}/bulletin/${e.id}`}>
                  <Button variant="secondary" icon={<FileText className="size-4" />}>{t("ben.exam.bulletin")}</Button>
                </Link>
              </div>
            </div>

            {e.sessions.length > 0 && (
              <div className="mt-3 flex flex-wrap gap-3">
                {e.sessions.map((s) => (
                  <div key={s.type} className="rounded-lg bg-surface px-3 py-2 text-sm">
                    <div className="text-xs text-muted">{label("SessionType", s.type)}</div>
                    <div className="flex items-center gap-2"><ResultBadge result={s.result} />
                      {s.overallAverage != null && <span className="font-semibold tabular-nums">{num(s.overallAverage, 2)}</span>}
                      {s.mention && <span className="text-muted">{s.mention}</span>}</div>
                  </div>
                ))}
              </div>
            )}

            {e.diploma && (
              <div className="mt-3 flex items-center gap-2 rounded-lg bg-ok-50 px-3 py-2 text-sm text-ok">
                <Award className="size-4" /> {t("ben.diploma.title")} <b className="ltr-field">{e.diploma.serialNumber}</b>
                {e.diploma.receivedDate && <span>· {date(e.diploma.receivedDate)}</span>}
              </div>
            )}

            {writer && (
              <div className="mt-4 flex flex-wrap gap-2 border-t border-line pt-3">
                {e.status !== "DEPARTED" && (
                  <>
                    <Button variant="secondary" onClick={() => setDialog({ kind: "exam", enrollment: e, type: "NORMAL" })}>
                      {t(e.sessions.some((s) => s.type === "NORMAL") ? "ben.exam.edit" : "ben.exam.enter")} — {label("SessionType", "NORMAL")}
                    </Button>
                    {(e.status === "ENROLLED" || e.status === "ABSENT" || e.status === "FAILED") && e.sessions.some((s) => s.type === "NORMAL") && (
                      <Button variant="secondary" onClick={() => setDialog({ kind: "exam", enrollment: e, type: "MAKEUP" })}>
                        {t(e.sessions.some((s) => s.type === "MAKEUP") ? "ben.exam.edit" : "ben.exam.enter")} — {label("SessionType", "MAKEUP")}
                      </Button>
                    )}
                  </>
                )}
                {e.status === "ENROLLED" && e.sessions.length > 0 && <Button variant="ghost" onClick={() => setDialog({ kind: "finalize", enrollment: e })}>{t("ben.enroll.finalize")}</Button>}
                {e.status === "ENROLLED" && <Button variant="ghost" onClick={() => setDialog({ kind: "depart", enrollment: e })}>{t("ben.enroll.depart")}</Button>}
                {e.status === "DEPARTED" && <Button variant="ghost" onClick={() => action.mutate({ path: `/enrollments/${e.id}/reinstate`, method: "POST" })}>{t("ben.enroll.reinstate")}</Button>}
                {e.status === "GRADUATED" && <Button variant="ghost" icon={<Award className="size-4" />} onClick={() => setDialog({ kind: "diploma", enrollment: e })}>{t(e.diploma ? "ben.diploma.edit" : "ben.diploma.add")}</Button>}
                {e.diploma && <Button variant="ghost" className="text-danger" onClick={() => setDialog({ kind: "deleteDiploma", enrollment: e })}>{t("ben.diploma.delete")}</Button>}
                <Button variant="ghost" onClick={() => setDialog({ kind: "enroll", enrollment: e })}>{t("common.edit")}</Button>
                <Button variant="ghost" className="text-danger" icon={<Trash2 className="size-4" />} onClick={() => setDialog({ kind: "delete", enrollment: e })}>{t("common.delete")}</Button>
              </div>
            )}
          </div>
        ))}
      </div>

      <EnrollmentFormModal beneficiary={beneficiary} dialog={dialog?.kind === "enroll" ? dialog : null} onClose={close} onSaved={refresh} />
      <DepartModal enrollment={dialog?.kind === "depart" ? dialog.enrollment : null} onClose={close} onSaved={refresh} />
      <DiplomaModal enrollment={dialog?.kind === "diploma" ? dialog.enrollment : null} onClose={close} onSaved={refresh} />
      {dialog?.kind === "exam" && <ExamSessionModal open enrollmentId={dialog.enrollment.id} type={dialog.type} onClose={close} />}
      <ConfirmDialog open={dialog?.kind === "delete"} danger loading={action.isPending} title={t("common.delete")} message={t("ben.enroll.delete.confirm")}
        onClose={close} onConfirm={() => dialog?.kind === "delete" && action.mutate({ path: `/enrollments/${dialog.enrollment.id}`, method: "DELETE" })} />
      <ConfirmDialog open={dialog?.kind === "finalize"} loading={action.isPending} title={t("ben.enroll.finalize")} message={t("ben.enroll.finalize.confirm")}
        onClose={close} onConfirm={() => dialog?.kind === "finalize" && action.mutate({ path: `/enrollments/${dialog.enrollment.id}/finalize`, method: "POST" })} />
      <ConfirmDialog open={dialog?.kind === "deleteDiploma"} danger loading={action.isPending} title={t("common.delete")} message={t("ben.diploma.delete.confirm")}
        onClose={close} onConfirm={() => dialog?.kind === "deleteDiploma" && action.mutate({ path: `/enrollments/${dialog.enrollment.id}/diploma`, method: "DELETE" })} />
    </Card>
  );
}

// ---------------------------------------------------------------- inscription

interface EnrollValues { filiereId: string; seasonId: string; startDate: string; examDate: string }

function EnrollmentFormModal({ beneficiary, dialog, onClose, onSaved }: {
  beneficiary: BeneficiaryDetail; dialog: { kind: "enroll"; enrollment?: EnrollmentView } | null; onClose: () => void; onSaved: () => void;
}) {
  const { t } = useI18n();
  return (
    <Modal open={!!dialog} onClose={onClose} title={dialog?.enrollment ? t("ben.enroll.edit") : t("ben.enroll.add")}>
      {dialog && <EnrollForm beneficiary={beneficiary} enrollment={dialog.enrollment} onClose={onClose} onSaved={onSaved} />}
    </Modal>
  );
}

function EnrollForm({ beneficiary, enrollment, onClose, onSaved }: { beneficiary: BeneficiaryDetail; enrollment?: EnrollmentView; onClose: () => void; onSaved: () => void }) {
  const { t, name } = useI18n();
  const toast = useToast();
  const [formError, setFormError] = useState<string>();
  const seasons = useSeasons();
  const filieres = useQuery({ queryKey: ["centers", beneficiary.centerId, "filieres"], queryFn: () => api<CenterFiliere[]>(`/centers/${beneficiary.centerId}/filieres`) });
  const { register, handleSubmit, setError, formState: { errors } } = useForm<EnrollValues>({
    defaultValues: { filiereId: enrollment ? String(enrollment.filiereId) : "", seasonId: "", startDate: enrollment?.startDate ?? new Date().toISOString().slice(0, 10), examDate: enrollment?.examDate ?? "" },
  });
  const save = useMutation({
    mutationFn: (v: EnrollValues) => {
      const body = { filiereId: Number(v.filiereId), seasonId: v.seasonId ? Number(v.seasonId) : undefined, startDate: v.startDate, examDate: v.examDate || undefined };
      return enrollment
        ? api(`/enrollments/${enrollment.id}`, { method: "PUT", body })
        : api(`/beneficiaries/${beneficiary.id}/enrollments`, { method: "POST", body });
    },
    onSuccess: () => { onSaved(); toast.success(t("ben.saved")); onClose(); },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(errorText(e, t)); },
  });
  const required = { required: t("common.required") };
  return (
    <form onSubmit={handleSubmit((v) => { setFormError(undefined); save.mutate(v); })} className="space-y-4" noValidate>
      <Field label={t("ben.enroll.filiere")} required error={errors.filiereId?.message}>
        <Select invalid={!!errors.filiereId} {...register("filiereId", required)}>
          <option value="">{t("common.select")}</option>
          {filieres.data?.filter((f) => f.active || f.filiereId === enrollment?.filiereId).map((f) => <option key={f.filiereId} value={f.filiereId}>{name(f)}</option>)}
        </Select>
      </Field>
      {!enrollment && (
        <Field label={t("ben.enroll.season")}>
          <Select {...register("seasonId")}>
            <option value="">{t("ben.enroll.currentSeason")}</option>
            {seasons.data?.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
          </Select>
        </Field>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("ben.enroll.start")} required error={errors.startDate?.message}><Input type="date" className="ltr-field" {...register("startDate", required)} /></Field>
        <Field label={t("ben.enroll.exam")}><Input type="date" className="ltr-field" {...register("examDate")} /></Field>
      </div>
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- départ

function DepartModal({ enrollment, onClose, onSaved }: { enrollment: EnrollmentView | null; onClose: () => void; onSaved: () => void }) {
  const { t } = useI18n();
  return (
    <Modal open={!!enrollment} onClose={onClose} title={t("ben.enroll.depart")}>
      {enrollment && <DepartForm enrollment={enrollment} onClose={onClose} onSaved={onSaved} />}
    </Modal>
  );
}

function DepartForm({ enrollment, onClose, onSaved }: { enrollment: EnrollmentView; onClose: () => void; onSaved: () => void }) {
  const { t } = useI18n();
  const [formError, setFormError] = useState<string>();
  const { register, handleSubmit, setError, formState: { errors } } = useForm<{ departureDate: string; reason: string }>({
    defaultValues: { departureDate: new Date().toISOString().slice(0, 10), reason: "" },
  });
  const save = useMutation({
    mutationFn: (v: { departureDate: string; reason: string }) => api(`/enrollments/${enrollment.id}/depart`, { method: "POST", body: v }),
    onSuccess: () => { onSaved(); onClose(); },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(errorText(e, t)); },
  });
  const required = { required: t("common.required") };
  return (
    <form onSubmit={handleSubmit((v) => { setFormError(undefined); save.mutate(v); })} className="space-y-4" noValidate>
      <Field label={t("ben.enroll.departDate")} required error={errors.departureDate?.message}><Input type="date" className="ltr-field" {...register("departureDate", required)} /></Field>
      <Field label={t("ben.enroll.departReason")} required error={errors.reason?.message}><Textarea invalid={!!errors.reason} {...register("reason", required)} /></Field>
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- diplôme

function DiplomaModal({ enrollment, onClose, onSaved }: { enrollment: EnrollmentView | null; onClose: () => void; onSaved: () => void }) {
  const { t } = useI18n();
  return (
    <Modal open={!!enrollment} onClose={onClose} title={t("ben.diploma.title")}>
      {enrollment && <DiplomaForm enrollment={enrollment} onClose={onClose} onSaved={onSaved} />}
    </Modal>
  );
}

function DiplomaForm({ enrollment, onClose, onSaved }: { enrollment: EnrollmentView; onClose: () => void; onSaved: () => void }) {
  const { t } = useI18n();
  const [formError, setFormError] = useState<string>();
  const d = enrollment.diploma;
  const { register, handleSubmit, setError, formState: { errors } } = useForm<{ serialNumber: string; receivedDate: string; additionalInfo: string }>({
    defaultValues: { serialNumber: d?.serialNumber ?? "", receivedDate: d?.receivedDate ?? "", additionalInfo: d?.additionalInfo ?? "" },
  });
  const save = useMutation({
    mutationFn: (v: { serialNumber: string; receivedDate: string; additionalInfo: string }) =>
      api(`/enrollments/${enrollment.id}/diploma`, { method: "PUT", body: { serialNumber: v.serialNumber, receivedDate: v.receivedDate || undefined, additionalInfo: v.additionalInfo || undefined } }),
    onSuccess: () => { onSaved(); onClose(); },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(errorText(e, t)); },
  });
  return (
    <form onSubmit={handleSubmit((v) => { setFormError(undefined); save.mutate(v); })} className="space-y-4" noValidate>
      <Field label={t("ben.diploma.serial")} required error={errors.serialNumber?.message}><Input className="ltr-field" invalid={!!errors.serialNumber} {...register("serialNumber", { required: t("common.required") })} /></Field>
      <Field label={t("ben.diploma.received")}><Input type="date" className="ltr-field" {...register("receivedDate")} /></Field>
      <Field label={t("ben.diploma.info")}><Textarea {...register("additionalInfo")} /></Field>
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}

