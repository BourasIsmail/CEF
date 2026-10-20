"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { Modal } from "@/components/ui/modal";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n } from "@/lib/i18n";
import type { ModuleView } from "@/lib/types";

interface Values { nameAr: string; nameFr: string; coefficient: string; durationHours: string }

export function ModuleFormModal({ filiereId, module, onClose }: { filiereId: number; module: ModuleView | "new" | null; onClose: () => void }) {
  const { t } = useI18n();
  return (
    <Modal open={!!module} onClose={onClose} title={module === "new" ? t("modules.add") : t("modules.edit")}>
      {module && <Form filiereId={filiereId} module={module} onClose={onClose} />}
    </Modal>
  );
}

function Form({ filiereId, module, onClose }: { filiereId: number; module: ModuleView | "new"; onClose: () => void }) {
  const { t } = useI18n();
  const toast = useToast();
  const qc = useQueryClient();
  const [formError, setFormError] = useState<string>();
  const existing = module === "new" ? null : module;
  const { register, handleSubmit, setError, formState: { errors } } = useForm<Values>({
    defaultValues: existing
      ? { nameAr: existing.nameAr, nameFr: existing.nameFr ?? "", coefficient: String(existing.coefficient), durationHours: String(existing.durationHours) }
      : { nameAr: "", nameFr: "", coefficient: "1", durationHours: "" },
  });

  const save = useMutation({
    mutationFn: (v: Values) => {
      const body = { nameAr: v.nameAr, nameFr: v.nameFr || undefined, coefficient: Number(v.coefficient), durationHours: Number(v.durationHours) };
      return existing
        ? api(`/filieres/${filiereId}/modules/${existing.id}`, { method: "PUT", body })
        : api(`/filieres/${filiereId}/modules`, { method: "POST", body });
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["filieres"] }); toast.success(t("common.saved")); onClose(); },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(errorText(e, t)); },
  });

  return (
    <form onSubmit={handleSubmit((v) => { setFormError(undefined); save.mutate(v); })} className="space-y-4" noValidate>
      <Field label={t("modules.field.nameAr")} required error={errors.nameAr?.message}>
        <Input invalid={!!errors.nameAr} {...register("nameAr", { required: t("common.required") })} />
      </Field>
      <Field label={t("modules.field.nameFr")} error={errors.nameFr?.message}>
        <Input className="ltr-field" {...register("nameFr")} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("modules.field.coefficient")} required error={errors.coefficient?.message}>
          <Input type="number" step="0.01" min="0.01" max="99.99" invalid={!!errors.coefficient} {...register("coefficient", { required: t("common.required") })} />
        </Field>
        <Field label={t("modules.field.hours")} required error={errors.durationHours?.message}>
          <Input type="number" min={1} max={5000} invalid={!!errors.durationHours} {...register("durationHours", { required: t("common.required") })} />
        </Field>
      </div>
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}
