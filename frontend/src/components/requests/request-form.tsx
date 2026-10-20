"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
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
  const { t, name, label } = useI18n();
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

  const save = useMutation({
    mutationFn: (f: Values) => {
      const body = {
        requestType: f.requestType, centerId: Number(f.centerId), associationId: f.associationId ? Number(f.associationId) : undefined,
        targetFiliereId: f.requestType === "MODIFY" ? Number(f.targetFiliereId) : undefined,
        proposedNameAr: f.proposedNameAr || undefined, proposedNameFr: f.proposedNameFr || undefined,
        durationMonths: f.durationMonths ? Number(f.durationMonths) : undefined,
        expectedBeneficiaries: f.expectedBeneficiaries ? Number(f.expectedBeneficiaries) : undefined,
        modificationSummary: f.modificationSummary || undefined,
      };
      return request
        ? api<RequestDetail>(`/filiere-requests/${request.id}`, { method: "PUT", body })
        : api<RequestDetail>("/filiere-requests", { method: "POST", body });
    },
    onSuccess: (r) => { qc.invalidateQueries({ queryKey: ["requests"] }); toast.success(t(request ? "req.updated" : "req.created")); router.push(`/requests/${r.id}`); },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(errorText(e, t)); window.scrollTo({ top: 0, behavior: "smooth" }); },
  });

  const required = { required: t("common.required") };
  return (
    <form onSubmit={handleSubmit((f) => { setFormError(undefined); save.mutate(f); })} noValidate className="space-y-6">
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-4 py-3 text-sm text-danger">{formError}</p>}
      <Card title={t("req.section.info")}>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("req.field.type")} required>
            <div className="flex gap-2">
              {(["CREATE", "MODIFY"] as const).map((x) => (
                <label key={x} className="flex flex-1 cursor-pointer items-center gap-2 rounded-lg border border-line px-3 py-2 text-sm has-[:checked]:border-brand-600 has-[:checked]:bg-brand-50 has-[:disabled]:opacity-60">
                  <input type="radio" value={x} disabled={!!request} className="accent-brand-600" {...register("requestType")} /> {label("RequestType", x)}
                </label>
              ))}
            </div>
          </Field>
          <Field label={t("req.field.center")} required error={errors.centerId?.message}>
            <Select disabled={!!request} invalid={!!errors.centerId} {...register("centerId", required)}>
              <option value="">{t("common.select")}</option>
              {request && <option value={request.centerId}>{request.centerNameAr}</option>}
              {centers.data?.filter((c) => c.id !== request?.centerId).map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
            </Select>
          </Field>
          <Field label={t("req.field.association")}>
            <Select disabled={!centerId} {...register("associationId")}>
              <option value="">{t("centers.notSpecified")}</option>
              {center.data?.associations.map((a) => <option key={a.associationId} value={a.associationId}>{a.associationNameAr}</option>)}
            </Select>
          </Field>
          {modify && (
            <Field label={t("req.field.target")} required error={errors.targetFiliereId?.message}>
              <Select disabled={!!request} invalid={!!errors.targetFiliereId} {...register("targetFiliereId", { validate: (x) => !modify || !!x || t("common.required") })}>
                <option value="">{t("common.select")}</option>
                {request?.targetFiliereId && <option value={request.targetFiliereId}>{request.targetFiliereNameAr}</option>}
                {filieres.data?.filter((f) => f.filiereId !== request?.targetFiliereId).map((f) => <option key={f.filiereId} value={f.filiereId}>{name(f)}</option>)}
              </Select>
            </Field>
          )}
          <Field label={t("req.field.nameAr")} required={!modify} hint={modify ? t("req.field.nameHintModify") : undefined} error={errors.proposedNameAr?.message}>
            <Input invalid={!!errors.proposedNameAr} {...register("proposedNameAr", { validate: (x) => modify || !!x.trim() || t("common.required") })} />
          </Field>
          <Field label={t("req.field.nameFr")}><Input className="ltr-field" {...register("proposedNameFr")} /></Field>
          <Field label={t("req.field.duration")} error={errors.durationMonths?.message}><Input type="number" min={1} max={60} {...register("durationMonths")} /></Field>
          <Field label={t("req.field.expected")}><Input type="number" min={0} {...register("expectedBeneficiaries")} /></Field>
          {modify && (
            <Field label={t("req.field.summary")} required error={errors.modificationSummary?.message} className="sm:col-span-2">
              <Textarea invalid={!!errors.modificationSummary} {...register("modificationSummary")} />
            </Field>
          )}
        </div>
      </Card>
      <div className="sticky bottom-0 -mx-4 flex justify-end gap-2 border-t border-line bg-surface/95 px-4 py-3 backdrop-blur lg:-mx-8 lg:px-8">
        <Button variant="secondary" onClick={() => router.back()}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}
