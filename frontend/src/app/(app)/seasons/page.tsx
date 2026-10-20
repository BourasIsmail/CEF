"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Pencil, Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Badge, Card, PageHeader } from "@/components/ui/card";
import { Field, Input } from "@/components/ui/form";
import { Modal } from "@/components/ui/modal";
import { ErrorState, Spinner } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import { useSeasons } from "@/lib/queries";
import type { Season } from "@/lib/types";

interface Values { label: string; startDate: string; endDate: string }

function SeasonForm({ season, onClose }: { season: Season | null; onClose: () => void }) {
  const { t } = useI18n();
  const toast = useToast();
  const qc = useQueryClient();
  const [formError, setFormError] = useState<string>();
  const { register, handleSubmit, setError, formState: { errors } } = useForm<Values>({
    defaultValues: season ? { label: season.label, startDate: season.startDate, endDate: season.endDate } : { label: "", startDate: "", endDate: "" },
  });
  const save = useMutation({
    mutationFn: (v: Values) => season
      ? api(`/seasons/${season.id}`, { method: "PUT", body: v })
      : api("/seasons", { method: "POST", body: v }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["seasons"] }); toast.success(t("seasons.saved")); onClose(); },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(errorText(e, t)); },
  });
  return (
    <form onSubmit={handleSubmit((v) => { setFormError(undefined); save.mutate(v); })} className="space-y-4" noValidate>
      <Field label={t("seasons.field.label")} required error={errors.label?.message}>
        <Input className="ltr-field" placeholder="2025-2026" invalid={!!errors.label}
          {...register("label", { required: t("common.required"), pattern: { value: /^\d{4}-\d{4}$/, message: t("err.season.label_invalid") } })} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("seasons.field.start")} required error={errors.startDate?.message}>
          <Input type="date" className="ltr-field" invalid={!!errors.startDate} {...register("startDate", { required: t("common.required") })} />
        </Field>
        <Field label={t("seasons.field.end")} required error={errors.endDate?.message}>
          <Input type="date" className="ltr-field" invalid={!!errors.endDate} {...register("endDate", { required: t("common.required") })} />
        </Field>
      </div>
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}

/** Saisons de formation (الموسم التكويني) : réservé à l'administrateur. */
export default function SeasonsPage() {
  const me = useMe();
  const router = useRouter();
  const { t, date } = useI18n();
  const toast = useToast();
  const qc = useQueryClient();
  const seasons = useSeasons();
  const [editing, setEditing] = useState<Season | "new" | null>(null);

  useEffect(() => { if (!can.admin(me)) router.replace("/"); }, [me, router]);

  const setCurrent = useMutation({
    mutationFn: (id: number) => api(`/seasons/${id}/current`, { method: "PUT" }),
    onSuccess: () => { qc.invalidateQueries(); toast.success(t("seasons.saved")); },
    onError: (e) => toast.error(errorText(e, t)),
  });

  if (!can.admin(me)) return null;
  return (
    <>
      <PageHeader title={t("seasons.title")} subtitle={t("seasons.subtitle")}
        actions={<Button icon={<Plus className="size-4" />} onClick={() => setEditing("new")}>{t("seasons.new")}</Button>} />
      {seasons.isPending && <Spinner />}
      {seasons.isError && <ErrorState error={seasons.error} />}
      {seasons.data && (
        <Card>
          <table className="w-full text-sm">
            <thead className="text-muted">
              <tr>
                <th className="px-3 py-2 text-start">{t("seasons.col.label")}</th>
                <th className="px-3 py-2 text-start">{t("seasons.col.start")}</th>
                <th className="px-3 py-2 text-start">{t("seasons.col.end")}</th>
                <th className="px-3 py-2 text-start">{t("common.status")}</th>
                <th className="px-3 py-2 text-start">{t("common.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {seasons.data.map((s) => (
                <tr key={s.id} className="border-t border-line">
                  <td className="px-3 py-2 font-semibold">{s.label}</td>
                  <td className="px-3 py-2">{date(s.startDate)}</td>
                  <td className="px-3 py-2">{date(s.endDate)}</td>
                  <td className="px-3 py-2">{s.current && <Badge tone="ok">{t("seasons.current")}</Badge>}</td>
                  <td className="px-3 py-2">
                    <div className="flex gap-1">
                      <Button variant="ghost" className="px-2" title={t("common.edit")} aria-label={t("common.edit")} onClick={() => setEditing(s)}><Pencil className="size-4" /></Button>
                      {!s.current && <Button variant="ghost" className="px-2" title={t("seasons.setCurrent")} aria-label={t("seasons.setCurrent")} onClick={() => setCurrent.mutate(s.id)}><CheckCircle2 className="size-4" /></Button>}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
      <Modal open={!!editing} onClose={() => setEditing(null)} title={editing === "new" ? t("seasons.new") : t("seasons.edit")}>
        {editing && <SeasonForm season={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
      </Modal>
    </>
  );
}
