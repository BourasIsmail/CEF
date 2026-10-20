"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input } from "@/components/ui/form";
import { Modal } from "@/components/ui/modal";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n } from "@/lib/i18n";
import type { FiliereDetail, FiliereSummary } from "@/lib/types";

interface Values { code: string; nameAr: string; nameFr: string; durationMonths: string; active: boolean }

/** Création / modification d'une filière du catalogue (administrateur). */
export function FiliereFormModal({ filiere, open, onClose, onSaved }: {
  filiere: FiliereSummary | null; open: boolean; onClose: () => void; onSaved?: (id: number) => void;
}) {
  const { t } = useI18n();
  return (
    <Modal open={open} onClose={onClose} title={filiere ? t("filieres.edit") : t("filieres.new")}>
      {open && <Form filiere={filiere} onClose={onClose} onSaved={onSaved} />}
    </Modal>
  );
}

function Form({ filiere, onClose, onSaved }: { filiere: FiliereSummary | null; onClose: () => void; onSaved?: (id: number) => void }) {
  const { t } = useI18n();
  const toast = useToast();
  const qc = useQueryClient();
  const [formError, setFormError] = useState<string>();
  const { register, handleSubmit, setError, formState: { errors } } = useForm<Values>({
    defaultValues: filiere
      ? { code: filiere.code, nameAr: filiere.nameAr, nameFr: filiere.nameFr, durationMonths: String(filiere.durationMonths), active: filiere.active }
      : { code: "", nameAr: "", nameFr: "", durationMonths: "12", active: true },
  });

  const save = useMutation({
    mutationFn: (v: Values) => {
      const body = { code: v.code.trim(), nameAr: v.nameAr, nameFr: v.nameFr, durationMonths: Number(v.durationMonths), active: v.active };
      return filiere
        ? api<FiliereDetail>(`/filieres/${filiere.id}`, { method: "PUT", body })
        : api<FiliereDetail>("/filieres", { method: "POST", body });
    },
    onSuccess: (d) => {
      qc.invalidateQueries({ queryKey: ["filieres"] });
      toast.success(t(filiere ? "filieres.updated" : "filieres.created"));
      onClose();
      onSaved?.(d.summary.id);
    },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(errorText(e, t)); },
  });

  return (
    <form onSubmit={handleSubmit((v) => { setFormError(undefined); save.mutate(v); })} className="space-y-4" noValidate>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("filieres.field.code")} required error={errors.code?.message}>
          <Input className="ltr-field uppercase" invalid={!!errors.code}
            {...register("code", { required: t("common.required"), pattern: { value: /^[A-Za-z0-9_-]{2,30}$/, message: "A-Z, 0-9, - _" } })} />
        </Field>
        <Field label={t("filieres.field.duration")} required error={errors.durationMonths?.message}>
          <Input type="number" min={1} max={60} invalid={!!errors.durationMonths} {...register("durationMonths", { required: t("common.required"), min: 1, max: 60 })} />
        </Field>
      </div>
      <Field label={t("filieres.field.nameAr")} required error={errors.nameAr?.message}>
        <Input invalid={!!errors.nameAr} {...register("nameAr", { required: t("common.required") })} />
      </Field>
      <Field label={t("filieres.field.nameFr")} required error={errors.nameFr?.message}>
        <Input className="ltr-field" invalid={!!errors.nameFr} {...register("nameFr", { required: t("common.required") })} />
      </Field>
      <Checkbox label={t("filieres.field.active")} {...register("active")} />
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}
