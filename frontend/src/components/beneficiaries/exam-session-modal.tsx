"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input, Select, Textarea } from "@/components/ui/form";
import { Modal } from "@/components/ui/modal";
import { Spinner } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n } from "@/lib/i18n";
import type { Bulletin, ExamStatus, SessionType } from "@/lib/types";

interface Values {
  examNumber: string;
  grades: { moduleId: number; status: ExamStatus; grade: string }[];
  finalExamStatus: ExamStatus;
  finalExamGrade: string;
  passThreshold: string;
  exceptionalPass: boolean;
  exceptionalPassReason: string;
}

const STATUSES: ExamStatus[] = ["TAKEN", "ABSENT"];

/** Moyenne pondérée des modules, aperçu uniquement : le serveur calcule et fait foi. */
export function previewAverages(
  modules: { id: number; coefficient: number }[],
  grades: { moduleId: number; status: ExamStatus; grade: string }[],
  finalGrade: string,
  finalStatus: ExamStatus,
): { modules?: number; overall?: number; absent: boolean } {
  const absent = finalStatus === "ABSENT" || grades.some((g) => g.status === "ABSENT");
  let sum = 0, coef = 0;
  for (const m of modules) {
    const g = grades.find((x) => x.moduleId === m.id);
    if (!g || g.status !== "TAKEN" || g.grade === "" || Number.isNaN(Number(g.grade))) return { absent };
    sum += Number(g.grade) * m.coefficient;
    coef += m.coefficient;
  }
  if (coef === 0) return { absent };
  const avg = sum / coef;
  if (finalStatus !== "TAKEN" || finalGrade === "" || Number.isNaN(Number(finalGrade))) return { modules: avg, absent };
  return { modules: avg, overall: (avg + Number(finalGrade)) / 2, absent };
}

export function ExamSessionModal({ enrollmentId, type, open, onClose }: {
  enrollmentId: number; type: SessionType; open: boolean; onClose: () => void;
}) {
  const { t, label } = useI18n();
  const bulletin = useQuery({
    queryKey: ["bulletin", enrollmentId], queryFn: () => api<Bulletin>(`/enrollments/${enrollmentId}/bulletin`), enabled: open,
  });
  return (
    <Modal open={open} onClose={onClose} wide title={`${label("SessionType", type)}`}>
      {open && (bulletin.data
        ? <Form bulletin={bulletin.data} enrollmentId={enrollmentId} type={type} onClose={onClose} />
        : <Spinner label={t("common.loading")} />)}
    </Modal>
  );
}

function Form({ bulletin, enrollmentId, type, onClose }: { bulletin: Bulletin; enrollmentId: number; type: SessionType; onClose: () => void }) {
  const { t, name, label, num } = useI18n();
  const toast = useToast();
  const qc = useQueryClient();
  const [formError, setFormError] = useState<string>();
  const existing = bulletin.sessions.find((s) => s.type === type);

  const { register, handleSubmit, control, setError, formState: { errors } } = useForm<Values>({
    defaultValues: {
      examNumber: existing?.examNumber ?? "",
      grades: bulletin.modules.map((m) => {
        const g = existing?.grades.find((x) => x.moduleId === m.id);
        return { moduleId: m.id, status: g && g.status !== "NOT_TAKEN" ? g.status : "TAKEN", grade: g?.grade != null ? String(g.grade) : "" };
      }),
      finalExamStatus: existing && existing.finalExamStatus !== "NOT_TAKEN" ? existing.finalExamStatus : "TAKEN",
      finalExamGrade: existing?.finalExamGrade != null ? String(existing.finalExamGrade) : "",
      passThreshold: String(existing?.passThreshold ?? 10),
      exceptionalPass: existing?.exceptionalPass ?? false,
      exceptionalPassReason: existing?.exceptionalPassReason ?? "",
    },
  });
  const v = useWatch({ control });
  const preview = previewAverages(bulletin.modules, (v.grades ?? []) as Values["grades"], v.finalExamGrade ?? "", (v.finalExamStatus ?? "TAKEN") as ExamStatus);

  const save = useMutation({
    mutationFn: (f: Values) => api(`/enrollments/${enrollmentId}/sessions/${type}`, {
      method: "PUT",
      body: {
        examNumber: f.examNumber || undefined,
        finalExamStatus: f.finalExamStatus,
        finalExamGrade: f.finalExamStatus === "TAKEN" && f.finalExamGrade !== "" ? Number(f.finalExamGrade) : undefined,
        passThreshold: f.passThreshold === "" ? undefined : Number(f.passThreshold),
        exceptionalPass: f.exceptionalPass,
        exceptionalPassReason: f.exceptionalPass ? f.exceptionalPassReason : undefined,
        grades: f.grades.map((g) => ({ moduleId: g.moduleId, status: g.status, grade: g.status === "TAKEN" && g.grade !== "" ? Number(g.grade) : undefined })),
      },
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["beneficiaries"] });
      qc.invalidateQueries({ queryKey: ["bulletin"] });
      qc.invalidateQueries({ queryKey: ["dashboard"] });
      toast.success(t("ben.exam.saved"));
      onClose();
    },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(errorText(e, t)); },
  });

  const gradeRule = { validate: (x: string) => x === "" || (Number(x) >= 0 && Number(x) <= 20) || "0 – 20" };

  return (
    <form onSubmit={handleSubmit((f) => { setFormError(undefined); save.mutate(f); })} className="space-y-4" noValidate>
      {type === "MAKEUP" && <p className="rounded-lg bg-warn-50 px-3 py-2 text-sm text-warn">{t("ben.exam.makeupHint")}</p>}
      <p className="text-xs text-muted">{t("ben.exam.absentNote")}</p>
      <Field label={t("ben.exam.number")} className="max-w-xs"><Input className="ltr-field" {...register("examNumber")} /></Field>

      <div className="overflow-x-auto rounded-lg border border-line">
        <table className="w-full text-sm">
          <thead className="bg-surface text-muted">
            <tr>
              <th className="px-3 py-2 text-start font-semibold">{t("ben.exam.module")}</th>
              <th className="px-3 py-2 text-start font-semibold">{t("ben.exam.coefficient")}</th>
              <th className="px-3 py-2 text-start font-semibold">{t("ben.exam.status")}</th>
              <th className="px-3 py-2 text-start font-semibold">{t("ben.exam.grade")}</th>
            </tr>
          </thead>
          <tbody>
            {bulletin.modules.map((m, i) => (
              <tr key={m.id} className="border-t border-line">
                <td className="px-3 py-2 font-medium">{name(m)}</td>
                <td className="px-3 py-2 tabular-nums">{num(m.coefficient, 2)}</td>
                <td className="px-3 py-2">
                  <Select aria-label={`${name(m)} — ${t("ben.exam.status")}`} {...register(`grades.${i}.status` as const)}>
                    {STATUSES.map((s) => <option key={s} value={s}>{label("ExamStatus", s)}</option>)}
                  </Select>
                </td>
                <td className="px-3 py-2">
                  <Input type="number" step="0.01" min={0} max={20} className="ltr-field w-28" aria-label={`${name(m)} — ${t("ben.exam.grade")}`}
                    disabled={v.grades?.[i]?.status === "ABSENT"} invalid={!!errors.grades?.[i]?.grade}
                    {...register(`grades.${i}.grade` as const, gradeRule)} />
                </td>
              </tr>
            ))}
            <tr className="border-t-2 border-line bg-brand-50/40">
              <td className="px-3 py-2 font-semibold" colSpan={2}>{t("ben.exam.final")}</td>
              <td className="px-3 py-2">
                <Select aria-label={`${t("ben.exam.final")} — ${t("ben.exam.status")}`} {...register("finalExamStatus")}>
                  {STATUSES.map((s) => <option key={s} value={s}>{label("ExamStatus", s)}</option>)}
                </Select>
              </td>
              <td className="px-3 py-2">
                <Input type="number" step="0.01" min={0} max={20} className="ltr-field w-28" aria-label={`${t("ben.exam.final")} — ${t("ben.exam.grade")}`}
                  disabled={v.finalExamStatus === "ABSENT"} {...register("finalExamGrade", gradeRule)} />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-lg bg-surface p-3 text-sm">
          <div className="text-muted">{t("ben.exam.modulesAverage")}</div>
          <div className="text-xl font-bold tabular-nums">{preview.modules == null ? "—" : num(preview.modules, 2)}</div>
        </div>
        <div className="rounded-lg bg-surface p-3 text-sm">
          <div className="text-muted">{t("ben.exam.overall")}</div>
          <div className="text-xl font-bold tabular-nums">{preview.overall == null ? "—" : num(preview.overall, 2)}</div>
          <div className="text-xs text-muted">{t("ben.exam.overallNote")}</div>
        </div>
        <Field label={t("ben.exam.threshold")} hint={t("ben.exam.thresholdHint")}>
          <Input type="number" step="0.01" min={0} max={20} className="ltr-field" {...register("passThreshold", gradeRule)} />
        </Field>
      </div>

      <div className="space-y-3 rounded-lg border border-line p-4">
        <Checkbox label={t("ben.exam.exceptional")} {...register("exceptionalPass")} />
        {v.exceptionalPass && (
          <Field label={t("ben.exam.exceptionalReason")} required error={errors.exceptionalPassReason?.message}>
            <Textarea invalid={!!errors.exceptionalPassReason} {...register("exceptionalPassReason", { validate: (x) => !v.exceptionalPass || !!x.trim() || t("common.required") })} />
          </Field>
        )}
      </div>

      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}
