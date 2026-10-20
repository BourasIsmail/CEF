"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { ExamSessionModal } from "./exam-session-modal";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { Spinner } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { errorText } from "@/lib/errors";
import { useI18n } from "@/lib/i18n";
import type { Bulletin, DiplomaView, EnrollmentView, SessionType, SessionView } from "@/lib/types";

const card = "rounded-xl border border-line bg-white p-6 space-y-3.5";

/** Résultats d'examen de la dernière inscription et diplôme, tels que dans la maquette « fiche bénéficiaire ». */
export function EnrollmentResults({ enrollment, writer }: { enrollment: EnrollmentView; writer: boolean }) {
  const { t, label, num } = useI18n();
  const [editing, setEditing] = useState<SessionType | null>(null);
  const bulletin = useQuery({ queryKey: ["bulletin", enrollment.id], queryFn: () => api<Bulletin>(`/enrollments/${enrollment.id}/bulletin`) });
  if (bulletin.isPending) return <Spinner />;
  if (bulletin.isError) return null;
  const b = bulletin.data;
  const sessions = [...b.sessions].sort((x) => (x.type === "NORMAL" ? -1 : 1));
  const threshold = sessions[0]?.passThreshold ?? 10;
  const canEnter = writer && enrollment.status !== "DEPARTED";
  const canMakeup = canEnter && b.makeupAllowed;
  const note = (v?: number | null) => (v != null ? num(v, 2) : "—");

  const resultText = (s: SessionView) => s.result === "PASSED" ? t("ben.res.passed")
    : s.result === "FAILED" ? t(s.type === "NORMAL" && b.makeupAllowed ? "ben.res.failedMakeup" : "ben.res.failed")
    : label("ExamResult", s.result);
  const resultTone = (s: SessionView) => s.result === "PASSED" ? "bg-ok-50 text-ok" : s.result === "FAILED" ? "bg-danger-50 text-danger" : "bg-surface text-muted";
  const exceptional = sessions.find((s) => s.exceptionalPass);

  return (
    <>
      <section className={`${card} flex flex-wrap items-center justify-between gap-4 !space-y-0`}>
        <div className="min-w-72 flex-1 text-sm leading-8 text-muted">
          <strong className="text-base text-ink">{t("ben.exam.rule")}</strong><br />
          {t("ben.exam.ruleText")}<br />
          {t("ben.exam.statusHint")}
        </div>
        <div className="flex flex-col gap-1.5 text-sm font-medium">
          <span>{t("ben.exam.thresholdLabel")}</span>
          <Input readOnly className="ltr-field w-28 text-center" value={num(threshold, 2)} aria-label={t("ben.exam.thresholdLabel")} />
        </div>
      </section>

      {sessions.map((s) => {
        const sumCoef = b.modules.reduce((a, m) => a + m.coefficient, 0);
        const gradeOf = (id: number) => s.grades.find((g) => g.moduleId === id);
        const sumProd = b.modules.reduce((a, m) => a + (gradeOf(m.id)?.grade ?? 0) * m.coefficient, 0);
        return (
          <section key={s.type} className={card}>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-xl font-semibold">{label("SessionType", s.type)}</h2>
              <div className="flex flex-wrap items-center gap-2">
                {(s.type === "NORMAL" ? canEnter : canMakeup) && (
                  <Button variant="secondary" onClick={() => setEditing(s.type)}>{t("ben.exam.editSession")}</Button>
                )}
                <span className={`rounded-full px-3.5 py-1.5 text-sm font-semibold ${resultTone(s)}`}>{resultText(s)}</span>
              </div>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-surface text-muted">
                  <tr>
                    {[t("ben.exam.module"), t("ben.exam.coefficient"), t("ben.exam.gradeOf20"), t("ben.exam.state"), t("ben.exam.product")].map((h) => (
                      <th key={h} className="whitespace-nowrap px-3 py-3 text-start font-semibold">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {b.modules.map((m) => {
                    const g = gradeOf(m.id);
                    return (
                      <tr key={m.id} className="border-t border-line">
                        <td className="px-3 py-3">{m.nameAr}</td>
                        <td className="px-3 py-3 tabular-nums">{num(m.coefficient)}</td>
                        <td className="px-3 py-3 tabular-nums">{note(g?.grade)}</td>
                        <td className="px-3 py-3">{g ? label("ExamStatus", g.status) : "—"}</td>
                        <td className="px-3 py-3 tabular-nums">{g?.grade != null ? num(g.grade * m.coefficient, 2) : "—"}</td>
                      </tr>
                    );
                  })}
                  <tr className="border-t border-line bg-surface font-bold">
                    <td className="px-3 py-3">{t("ben.exam.weightedAverage")}</td>
                    <td className="px-3 py-3 tabular-nums">{num(sumCoef)}</td>
                    <td className="px-3 py-3 tabular-nums" colSpan={2}>{note(s.modulesAverage)}</td>
                    <td className="px-3 py-3 tabular-nums">{num(sumProd, 2)}</td>
                  </tr>
                  <tr className="border-t border-line">
                    <td className="px-3 py-3 font-semibold">{t("ben.exam.final")}</td>
                    <td className="px-3 py-3">—</td>
                    <td className="px-3 py-3 tabular-nums">{note(s.finalExamGrade)}</td>
                    <td className="px-3 py-3">{label("ExamStatus", s.finalExamStatus)}</td>
                    <td className="px-3 py-3">—</td>
                  </tr>
                  <tr className="border-t border-line bg-brand-50 font-bold">
                    <td className="px-3 py-3" colSpan={2}>{t("ben.exam.overall")}</td>
                    <td className="px-3 py-3 tabular-nums" colSpan={3}>
                      {s.overallAverage != null
                        ? <>{t("ben.exam.formula", { mod: note(s.modulesAverage), final: note(s.finalExamGrade), general: note(s.overallAverage) })} · {t("ben.exam.mention")} : {s.mention ?? "—"}</>
                        : "—"}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        );
      })}
      {sessions.length === 0 && (
        <section className={card}>
          <p className="text-sm text-muted">{t("ben.exam.noSession")}</p>
          {canEnter && <div><Button onClick={() => setEditing("NORMAL")}>{t("ben.exam.enterSession")}</Button></div>}
        </section>
      )}

      <section className="space-y-3.5 rounded-xl border border-dashed border-line bg-white p-6">
        <h2 className="text-lg font-semibold">{t("ben.exam.exceptionalTitle")}</h2>
        <label className="flex min-h-11 items-center gap-2.5 text-sm">
          <input type="checkbox" className="size-5" checked={!!exceptional} disabled readOnly />
          <span>{t("ben.exam.exceptionalHelp")}</span>
        </label>
        <div className="space-y-1.5 text-sm font-medium">
          <span>{t("ben.exam.reason")}</span>
          <textarea rows={2} disabled readOnly value={exceptional?.exceptionalPassReason ?? ""}
            className="w-full rounded-lg border border-line bg-surface px-3 py-2.5 text-sm" />
        </div>
        {writer && <p className="text-xs text-muted">{t("ben.exam.exceptionalNote")}</p>}
      </section>

      <DiplomaCard key={`${enrollment.id}-${enrollment.diploma?.serialNumber ?? ""}`} enrollment={enrollment} writer={writer} />

      {editing && <ExamSessionModal open enrollmentId={enrollment.id} type={editing} onClose={() => setEditing(null)} />}
    </>
  );
}

interface DiplomaValues { receivedDate: string; serialNumber: string; additionalInfo: string }

function DiplomaCard({ enrollment, writer }: { enrollment: EnrollmentView; writer: boolean }) {
  const { t } = useI18n();
  const toast = useToast();
  const qc = useQueryClient();
  const d: DiplomaView | undefined = enrollment.diploma;
  const open = enrollment.status === "GRADUATED" && writer;
  const { register, handleSubmit, formState: { errors } } = useForm<DiplomaValues>({
    defaultValues: { receivedDate: d?.receivedDate ?? "", serialNumber: d?.serialNumber ?? "", additionalInfo: d?.additionalInfo ?? "" },
  });
  const save = useMutation({
    mutationFn: (v: DiplomaValues) =>
      api(`/enrollments/${enrollment.id}/diploma`, { method: "PUT", body: { serialNumber: v.serialNumber, receivedDate: v.receivedDate || undefined, additionalInfo: v.additionalInfo || undefined } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["beneficiaries"] });
      qc.invalidateQueries({ queryKey: ["bulletin"] });
      toast.success(t("ben.saved"));
    },
    onError: (e) => toast.error(errorText(e, t)),
  });
  return (
    <section className={card}>
      <h2 className="text-xl font-semibold">{t("ben.diploma.title")}</h2>
      {enrollment.status !== "GRADUATED" && <p className="rounded-lg bg-surface px-3 py-2 text-sm text-muted">{t("ben.banner.diploma")}</p>}
      <form onSubmit={handleSubmit((v) => save.mutate(v))} className="space-y-4" noValidate>
        <fieldset disabled={!open} className="grid gap-4 sm:grid-cols-4">
          <Field label={t("ben.diploma.received")}><Input type="date" className="ltr-field" {...register("receivedDate")} /></Field>
          <Field label={t("ben.diploma.serial")} required error={errors.serialNumber?.message}>
            <Input className="ltr-field" invalid={!!errors.serialNumber} {...register("serialNumber", { required: t("common.required") })} />
          </Field>
          <div className="sm:col-span-2"><Field label={t("ben.diploma.info")}><Input {...register("additionalInfo")} /></Field></div>
        </fieldset>
        {open && <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>}
      </form>
    </section>
  );
}
