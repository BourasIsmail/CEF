"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { DocumentChecklist } from "@/components/requests/document-checklist";
import { NumberedSection } from "@/components/ui/section";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n } from "@/lib/i18n";
import { useCentersOptions } from "@/lib/queries";
import type { CenterDetail, CenterFiliere, RequestDetail } from "@/lib/types";

interface Values {
  requestType: "CREATE" | "MODIFY"; centerId: string; associationId: string; targetFiliereId: string;
  proposedNameAr: string; proposedNameFr: string; durationMonths: string; expectedBeneficiaries: string; modificationSummary: string;
}

/** Brouillon de demande : le type et le centre ne se modifient plus après la création. */
export function RequestForm({ request }: { request?: RequestDetail }) {
  const { t, name } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const qc = useQueryClient();
  const [formError, setFormError] = useState<string>();
  const centers = useCentersOptions();
  const { register, handleSubmit, control, setError, formState: { errors } } = useForm<Values>({
    defaultValues: {
      requestType: request?.requestType ?? "CREATE", centerId: request ? String(request.centerId) : "", associationId: request?.associationId ? String(request.associationId) : "",
      targetFiliereId: request?.targetFiliereId ? String(request.targetFiliereId) : "", proposedNameAr: request?.proposedNameAr ?? "", proposedNameFr: request?.proposedNameFr ?? "",
      durationMonths: request?.durationMonths ? String(request.durationMonths) : "", expectedBeneficiaries: request?.expectedBeneficiaries != null ? String(request.expectedBeneficiaries) : "",
      modificationSummary: request?.modificationSummary ?? "",
    },
  });
  const v = useWatch({ control });
  const centerId = v.centerId ?? "";
  const modify = v.requestType === "MODIFY";
  const center = useQuery({ queryKey: ["centers", centerId], queryFn: () => api<CenterDetail>(`/centers/${centerId}`), enabled: !!centerId });
  const filieres = useQuery({ queryKey: ["centers", centerId, "filieres"], queryFn: () => api<CenterFiliere[]>(`/centers/${centerId}/filieres`), enabled: !!centerId && modify });

  const types = useQuery({
    queryKey: ["requests", "document-types"], enabled: !request,
    queryFn: () => api<{ code: string; position: number }[]>("/filiere-requests/document-types"),
  });
  const slots = request?.documents ?? (types.data ?? []).map((x) => ({ typeCode: x.code, position: x.position, document: undefined }));
  const uploaded = slots.filter((d) => d.document).length;
  const total = request?.documents.length ?? 8;
  const canSubmit = !!request && uploaded === total;

  const save = useMutation({
    mutationFn: async ({ f, submit }: { f: Values; submit: boolean }) => {
      const body = {
        requestType: f.requestType, centerId: Number(f.centerId), associationId: f.associationId ? Number(f.associationId) : undefined,
        targetFiliereId: f.requestType === "MODIFY" ? Number(f.targetFiliereId) : undefined,
        proposedNameAr: f.proposedNameAr || undefined, proposedNameFr: f.proposedNameFr || undefined,
        durationMonths: f.durationMonths ? Number(f.durationMonths) : undefined,
        expectedBeneficiaries: f.expectedBeneficiaries ? Number(f.expectedBeneficiaries) : undefined,
        modificationSummary: f.modificationSummary || undefined,
      };
      const r = request
        ? await api<RequestDetail>(`/filiere-requests/${request.id}`, { method: "PUT", body })
        : await api<RequestDetail>("/filiere-requests", { method: "POST", body });
      if (submit) await api(`/filiere-requests/${r.id}/submit`, { method: "POST", body: {} });
      return { r, submit };
    },
    onSuccess: ({ r, submit }) => {
      qc.invalidateQueries({ queryKey: ["requests"] });
      if (submit) { toast.success(t("req.done.submit")); router.push(`/requests/${r.id}`); return; }
      toast.success(t(request ? "req.updated" : "req.created"));
      // le brouillon existe : on passe à l'édition pour pouvoir joindre les pièces
      if (!request) router.replace(`/requests/${r.id}/edit`);
    },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(errorText(e, t)); window.scrollTo({ top: 0, behavior: "smooth" }); },
  });
  const run = (submit: boolean) => handleSubmit((f) => { setFormError(undefined); save.mutate({ f, submit }); })();

  const required = { required: t("common.required") };
  return (
    <form onSubmit={(e) => { e.preventDefault(); run(false); }} noValidate className="space-y-5">
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-4 py-3 text-sm text-danger">{formError}</p>}
      <NumberedSection id="info" num={1} title={t("req.section.info")}>
        <fieldset className="m-0 border-0 p-0">
          <legend className="mb-2.5 p-0 text-[13px] font-medium">{t("req.field.type")}</legend>
          <div className="flex flex-wrap gap-2.5">
            {(["CREATE", "MODIFY"] as const).map((x) => (
              <label key={x} className="flex min-h-11 cursor-pointer items-center gap-2 rounded-lg border border-[#C9D5D2] bg-white px-4 text-sm has-[:checked]:border-brand-600 has-[:checked]:bg-[#E3F0EE] has-[:checked]:font-semibold has-[:disabled]:opacity-60">
                <input type="radio" value={x} disabled={!!request} className="accent-brand-600" {...register("requestType")} />
                <span>{t(x === "CREATE" ? "req.form.typeCreate" : "req.form.typeModify")}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <div className="flex flex-wrap gap-4">
          <Field label={t("req.field.center")} required error={errors.centerId?.message} className="min-w-0 flex-[1_1_240px]">
            <Select disabled={!!request} invalid={!!errors.centerId} {...register("centerId", required)}>
              <option value="">{t("common.select")}</option>
              {request && <option value={request.centerId}>{request.centerNameAr}</option>}
              {centers.data?.filter((c) => c.id !== request?.centerId).map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
            </Select>
          </Field>
          <Field label={t("req.field.association")} className="min-w-0 flex-[1_1_240px]">
            <Select disabled={!centerId} {...register("associationId")}>
              <option value="">{t("centers.notSpecified")}</option>
              {center.data?.associations.map((a) => <option key={a.associationId} value={a.associationId}>{a.associationNameAr}</option>)}
            </Select>
          </Field>
          {modify && (
            <Field label={t("req.field.target")} required error={errors.targetFiliereId?.message} className="min-w-0 flex-[1_1_100%]">
              <Select disabled={!!request} invalid={!!errors.targetFiliereId} {...register("targetFiliereId", { validate: (x) => !modify || !!x || t("common.required") })}>
                <option value="">{t("common.select")}</option>
                {request?.targetFiliereId && <option value={request.targetFiliereId}>{request.targetFiliereNameAr}</option>}
                {filieres.data?.filter((f) => f.filiereId !== request?.targetFiliereId).map((f) => <option key={f.filiereId} value={f.filiereId}>{name(f)}</option>)}
              </Select>
            </Field>
          )}
          <Field label={t("req.field.nameAr")} required={!modify} hint={modify ? t("req.field.nameHintModify") : undefined} error={errors.proposedNameAr?.message} className="min-w-0 flex-[2_1_280px]">
            <Input invalid={!!errors.proposedNameAr} {...register("proposedNameAr", { validate: (x) => modify || !!x.trim() || t("common.required") })} />
          </Field>
          <Field label={t("req.field.nameFr")} className="min-w-0 flex-[2_1_280px]"><Input className="ltr-field" {...register("proposedNameFr")} /></Field>
          <Field label={t("req.field.duration")} error={errors.durationMonths?.message} className="min-w-0 flex-[1_1_160px]"><Input type="number" min={1} max={60} {...register("durationMonths")} /></Field>
          <Field label={t("req.field.expected")} className="min-w-0 flex-[1_1_160px]"><Input type="number" min={0} {...register("expectedBeneficiaries")} /></Field>
          {modify && (
            <Field label={t("req.field.summary")} required error={errors.modificationSummary?.message} className="min-w-0 flex-[1_1_100%]">
              <Textarea invalid={!!errors.modificationSummary} {...register("modificationSummary", { validate: (x) => !modify || !!x.trim() || t("common.required") })} />
            </Field>
          )}
        </div>
      </NumberedSection>

      <NumberedSection id="documents" num={2} title={t("req.section.documents")}>
        <div className="-mt-3 flex flex-wrap items-center justify-between gap-2">
          <p className="m-0 text-sm text-muted">{t("req.section.documents.hint")}</p>
          <span className="rounded-full bg-[#FBF1DC] px-3.5 py-1.5 text-sm font-semibold text-[#5E3D00]">{t("req.doc.progress", { n: uploaded, total })}</span>
        </div>
        <DocumentChecklist requestId={request?.id} slots={slots} editable={!request || request.availableActions.includes("EDIT")}
          onChange={() => qc.invalidateQueries({ queryKey: ["requests"] })} />
        {!request && <p className="text-sm text-[#5E3D00]">{t("req.form.saveFirst")}</p>}
      </NumberedSection>

      <div className="flex flex-wrap items-center gap-3 pb-6 pt-2">
        <button type="submit" disabled={save.isPending}
          className="min-h-12 rounded-lg border border-brand-600 bg-white px-6 text-[15px] font-semibold text-brand-700 hover:bg-brand-50 disabled:opacity-60">{t("req.form.saveDraft")}</button>
        <button type="button" disabled={!canSubmit || save.isPending} onClick={() => run(true)}
          className="min-h-12 rounded-lg bg-brand-600 px-7 text-[15px] font-semibold text-white hover:bg-brand-700 disabled:bg-[#C9D5D2] disabled:text-muted">{t("req.form.submit")}</button>
        {!canSubmit && <span className="text-[13px] text-[#5E3D00]">{t("req.form.submitHint")}</span>}
      </div>
    </form>
  );
}
