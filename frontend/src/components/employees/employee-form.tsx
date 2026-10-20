"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n } from "@/lib/i18n";
import { useCentersOptions, useProvinces } from "@/lib/queries";
import type { EmployeeDetail } from "@/lib/types";

const TYPES = ["PEDAGOGICAL", "CENTER_DIRECTOR", "ADMINISTRATIVE", "GUEST_TRAINER", "PEDAGOGICAL_COORDINATOR"] as const;
const STATUSES = ["ENTRAIDE_NATIONALE", "ASSOCIATION", "OTHER_SECTOR", "PROMOTION_NATIONALE", "FOREIGN_VOLUNTEER"] as const;
const ACTIVITIES = ["ACTIVE", "ON_LEAVE", "SECONDED", "SUSPENDED", "RETIRED"] as const;
const ID_TYPES = ["NATIONAL_ID", "PASSPORT", "RESIDENCE_CARD", "BIRTH_CERTIFICATE", "OTHER"] as const;
const LEVELS = ["NONE", "PRIMARY", "LOWER_SECONDARY", "UPPER_SECONDARY", "BACCALAUREATE", "UNIVERSITY", "VOCATIONAL", "OTHER"] as const;

interface Values {
  provincialDirectorateId: string; centerId: string; employeeType: string; administrativeStatus: string;
  otherSectorText: string; delegatingBody: string; fullNameAr: string; fullNameFr: string; gender: string;
  birthDate: string; birthPlace: string; idDocumentType: string; idDocumentNumber: string; address: string; phone: string; email: string;
  educationLevel: string; diplomaTitle: string; specialty: string; diplomaDate: string; experienceDescription: string; otherQualifications: string;
  enGrade: string; enScale: string; rankNumber: string; hireDate: string; activityStatus: string;
}

function initial(e?: EmployeeDetail): Values {
  return {
    provincialDirectorateId: e ? String(e.provincialDirectorateId) : "", centerId: e?.centerId ? String(e.centerId) : "",
    employeeType: e?.employeeType ?? "", administrativeStatus: e?.administrativeStatus ?? "",
    otherSectorText: e?.otherSectorText ?? "", delegatingBody: e?.delegatingBody ?? "", fullNameAr: e?.fullNameAr ?? "", fullNameFr: e?.fullNameFr ?? "",
    gender: e?.gender ?? "", birthDate: e?.birthDate ?? "", birthPlace: e?.birthPlace ?? "", idDocumentType: e?.idDocumentType ?? "",
    idDocumentNumber: e?.idDocumentNumber ?? "", address: e?.address ?? "", phone: e?.phone ?? "", email: e?.email ?? "",
    educationLevel: e?.educationLevel ?? "", diplomaTitle: e?.diplomaTitle ?? "", specialty: e?.specialty ?? "", diplomaDate: e?.diplomaDate ?? "",
    experienceDescription: e?.experienceDescription ?? "", otherQualifications: e?.otherQualifications ?? "",
    enGrade: e?.enGrade ?? "", enScale: e?.enScale ?? "", rankNumber: e?.rankNumber ?? "", hireDate: e?.hireDate ?? "", activityStatus: e?.activityStatus ?? "ACTIVE",
  };
}

/**
 * Fiche d'un agent. Règles : centre obligatoire sauf coordonnateur (interdit) et administratif (facultatif) ;
 * données Entraide Nationale (cadre, échelle, n° d'ordre) uniquement pour ce statut ; centre non modifiable (mutation).
 */
export function EmployeeForm({ employee }: { employee?: EmployeeDetail }) {
  const me = useMe();
  const { t, name, label } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const qc = useQueryClient();
  const [formError, setFormError] = useState<string>();
  const { register, handleSubmit, control, setError, formState: { errors } } = useForm<Values>({ defaultValues: initial(employee) });
  const v = useWatch({ control });
  const centers = useCentersOptions();
  const provinces = useProvinces();

  const type = v.employeeType;
  const status = v.administrativeStatus;
  const noCenter = type === "PEDAGOGICAL_COORDINATOR";
  const centerRequired = !!type && type !== "PEDAGOGICAL_COORDINATOR" && type !== "ADMINISTRATIVE";
  const showCenter = !noCenter;
  const needsProvince = me.role === "ADMIN" && !employee && (noCenter || (type === "ADMINISTRATIVE" && !v.centerId));
  const en = status === "ENTRAIDE_NATIONALE";
  const err = (k: keyof Values) => errors[k]?.message as string | undefined;
  const required = { required: t("common.required") };

  const save = useMutation({
    mutationFn: (f: Values) => {
      const body = {
        provincialDirectorateId: needsProvince && f.provincialDirectorateId ? Number(f.provincialDirectorateId) : undefined,
        centerId: !noCenter && f.centerId ? Number(f.centerId) : undefined,
        employeeType: f.employeeType, administrativeStatus: f.administrativeStatus,
        otherSectorText: f.administrativeStatus === "OTHER_SECTOR" ? f.otherSectorText : undefined,
        delegatingBody: f.administrativeStatus === "FOREIGN_VOLUNTEER" ? f.delegatingBody : undefined,
        fullNameAr: f.fullNameAr, fullNameFr: f.fullNameFr || undefined, gender: f.gender,
        birthDate: f.birthDate || undefined, birthPlace: f.birthPlace || undefined,
        idDocumentType: f.idDocumentType || undefined, idDocumentNumber: f.idDocumentNumber || undefined,
        address: f.address || undefined, phone: f.phone || undefined, email: f.email || undefined,
        educationLevel: f.educationLevel || undefined, diplomaTitle: f.diplomaTitle || undefined, specialty: f.specialty || undefined,
        diplomaDate: f.diplomaDate || undefined, experienceDescription: f.experienceDescription || undefined, otherQualifications: f.otherQualifications || undefined,
        enGrade: en ? f.enGrade || undefined : undefined, enScale: en ? f.enScale || undefined : undefined,
        rankNumber: en ? f.rankNumber : undefined, hireDate: f.hireDate || undefined, activityStatus: f.activityStatus,
      };
      return employee
        ? api<EmployeeDetail>(`/employees/${employee.id}`, { method: "PUT", body })
        : api<EmployeeDetail>("/employees", { method: "POST", body });
    },
    onSuccess: (e) => {
      qc.invalidateQueries({ queryKey: ["employees"] });
      qc.invalidateQueries({ queryKey: ["centers"] });
      toast.success(t(employee ? "hr.updated" : "hr.created"));
      router.push(`/employees/${e.id}`);
    },
    onError: (e) => {
      if (!applyServerErrors(e, setError)) setFormError(errorText(e, t));
      window.scrollTo({ top: 0, behavior: "smooth" });
    },
  });

  return (
    <form onSubmit={handleSubmit((f) => { setFormError(undefined); save.mutate(f); })} noValidate className="space-y-6">
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-4 py-3 text-sm text-danger">{formError}</p>}

      <Card title={t("hr.section.job")}>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label={t("hr.field.type")} required error={err("employeeType")}>
            <Select invalid={!!errors.employeeType} {...register("employeeType", required)}>
              <option value="">{t("common.select")}</option>
              {TYPES.map((x) => <option key={x} value={x}>{label("EmployeeType", x)}</option>)}
            </Select>
          </Field>
          <Field label={t("hr.field.status")} required error={err("administrativeStatus")}>
            <Select invalid={!!errors.administrativeStatus} {...register("administrativeStatus", required)}>
              <option value="">{t("common.select")}</option>
              {STATUSES.map((x) => <option key={x} value={x}>{label("AdministrativeStatus", x)}</option>)}
            </Select>
          </Field>
          <Field label={t("hr.field.activity")}>
            <Select {...register("activityStatus")}>{ACTIVITIES.map((x) => <option key={x} value={x}>{label("ActivityStatus", x)}</option>)}</Select>
          </Field>
          {showCenter && (
            <Field label={t("hr.field.center")} required={centerRequired} hint={employee ? t("hr.field.centerHint") : undefined} error={err("centerId")} className="lg:col-span-2">
              <Select disabled={!!employee} invalid={!!errors.centerId} {...register("centerId", { validate: (x) => !centerRequired || !!x || t("common.required") })}>
                <option value="">{type === "ADMINISTRATIVE" ? t("hr.provincial") : t("common.select")}</option>
                {employee?.centerId && <option value={employee.centerId}>{employee.centerNameAr}</option>}
                {centers.data?.filter((c) => c.id !== employee?.centerId).map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
              </Select>
            </Field>
          )}
          {needsProvince && (
            <Field label={t("hr.field.province")} required error={err("provincialDirectorateId")}>
              <Select invalid={!!errors.provincialDirectorateId} {...register("provincialDirectorateId", { validate: (x) => !needsProvince || !!x || t("common.required") })}>
                <option value="">{t("common.select")}</option>
                {provinces.data?.map((p) => <option key={p.id} value={p.id}>{name(p)}</option>)}
              </Select>
            </Field>
          )}
          {status === "OTHER_SECTOR" && (
            <Field label={t("hr.field.otherSector")} required error={err("otherSectorText")}>
              <Input invalid={!!errors.otherSectorText} {...register("otherSectorText", { validate: (x) => status !== "OTHER_SECTOR" || !!x.trim() || t("common.required") })} />
            </Field>
          )}
          {status === "FOREIGN_VOLUNTEER" && (
            <Field label={t("hr.field.delegating")} required error={err("delegatingBody")}>
              <Input invalid={!!errors.delegatingBody} {...register("delegatingBody", { validate: (x) => status !== "FOREIGN_VOLUNTEER" || !!x.trim() || t("common.required") })} />
            </Field>
          )}
        </div>
      </Card>

      <Card title={t("hr.section.personal")}>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label={t("hr.field.nameAr")} required error={err("fullNameAr")} className="lg:col-span-2"><Input invalid={!!errors.fullNameAr} {...register("fullNameAr", required)} /></Field>
          <Field label={t("hr.field.gender")} required error={err("gender")}>
            <Select invalid={!!errors.gender} {...register("gender", required)}>
              <option value="">{t("common.select")}</option>
              <option value="F">{label("Gender", "F")}</option>
              <option value="M">{label("Gender", "M")}</option>
            </Select>
          </Field>
          <Field label={t("hr.field.nameFr")} className="lg:col-span-2"><Input className="ltr-field" {...register("fullNameFr")} /></Field>
          <Field label={t("hr.field.birthDate")}><Input type="date" className="ltr-field" {...register("birthDate")} /></Field>
          <Field label={t("hr.field.birthPlace")}><Input {...register("birthPlace")} /></Field>
          <Field label={t("hr.field.idType")}>
            <Select {...register("idDocumentType")}>
              <option value="">{t("common.select")}</option>
              {ID_TYPES.map((x) => <option key={x} value={x}>{label("IdDocumentType", x)}</option>)}
            </Select>
          </Field>
          <Field label={t("hr.field.idNumber")}><Input className="ltr-field" {...register("idDocumentNumber")} /></Field>
          <Field label={t("hr.field.phone")}><Input type="tel" className="ltr-field" {...register("phone")} /></Field>
          <Field label={t("hr.field.email")} error={err("email")}><Input type="email" className="ltr-field" {...register("email")} /></Field>
          <Field label={t("hr.field.address")} className="lg:col-span-2"><Input {...register("address")} /></Field>
        </div>
      </Card>

      <Card title={t("hr.section.qualifications")}>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label={t("hr.field.education")}>
            <Select {...register("educationLevel")}>
              <option value="">{t("common.select")}</option>
              {LEVELS.map((x) => <option key={x} value={x}>{label("EducationLevel", x)}</option>)}
            </Select>
          </Field>
          <Field label={t("hr.field.diploma")}><Input {...register("diplomaTitle")} /></Field>
          <Field label={t("hr.field.specialty")}><Input {...register("specialty")} /></Field>
          <Field label={t("hr.field.diplomaDate")}><Input type="date" className="ltr-field" {...register("diplomaDate")} /></Field>
          <Field label={t("hr.field.experience")} className="sm:col-span-2"><Input {...register("experienceDescription")} /></Field>
          <Field label={t("hr.field.otherQualifications")} className="sm:col-span-2 lg:col-span-3"><Textarea {...register("otherQualifications")} /></Field>
        </div>
      </Card>

      <Card title={t("hr.section.en")}>
        {!en ? <p className="text-sm text-muted">{t("hr.enOnly")}</p> : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Field label={t("hr.field.rank")} required error={err("rankNumber")}>
              <Input className="ltr-field" invalid={!!errors.rankNumber} {...register("rankNumber", { validate: (x) => !en || !!x.trim() || t("common.required") })} />
            </Field>
            <Field label={t("hr.field.grade")}><Input {...register("enGrade")} /></Field>
            <Field label={t("hr.field.scale")}><Input {...register("enScale")} /></Field>
            <Field label={t("hr.field.hireDate")}><Input type="date" className="ltr-field" {...register("hireDate")} /></Field>
          </div>
        )}
        {!en && <div className="mt-4 max-w-xs"><Field label={t("hr.field.hireDate")}><Input type="date" className="ltr-field" {...register("hireDate")} /></Field></div>}
      </Card>

      <div className="sticky bottom-0 -mx-4 flex justify-end gap-2 border-t border-line bg-surface/95 px-4 py-3 backdrop-blur lg:-mx-8 lg:px-8">
        <Button variant="secondary" onClick={() => router.back()}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}
