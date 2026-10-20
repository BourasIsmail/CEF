"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { OptionField } from "@/components/ui/option-group";
import { NumberedSection } from "@/components/ui/section";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n, type MessageKey } from "@/lib/i18n";
import { useCentersOptions, useProvinces, useSeasons } from "@/lib/queries";
import type { CenterFiliere, EmployeeDetail, FiliereSummary, PageResponse } from "@/lib/types";

const TYPES = ["PEDAGOGICAL", "CENTER_DIRECTOR", "ADMINISTRATIVE", "GUEST_TRAINER", "PEDAGOGICAL_COORDINATOR"] as const;
const STATUSES = ["ENTRAIDE_NATIONALE", "ASSOCIATION", "OTHER_SECTOR", "PROMOTION_NATIONALE", "FOREIGN_VOLUNTEER"] as const;
const ACTIVITIES = ["ACTIVE", "ON_LEAVE", "SECONDED", "SUSPENDED", "RETIRED"] as const;
const ID_TYPES = ["NATIONAL_ID", "PASSPORT", "RESIDENCE_CARD", "BIRTH_CERTIFICATE", "OTHER"] as const;
const LEVELS = ["NONE", "PRIMARY", "LOWER_SECONDARY", "UPPER_SECONDARY", "BACCALAUREATE", "UNIVERSITY", "VOCATIONAL", "OTHER"] as const;
/** Listes proposées pour الإطار et السلم ; une valeur existante hors liste reste sélectionnable. À valider avec la liste officielle. */
const GRADES = ["مكوّن", "منسق", "متصرف", "متصرف مساعد", "مهندس", "تقني", "مساعد تقني", "مساعد إداري"];
const SCALES = ["السلم 5", "السلم 6", "السلم 7", "السلم 8", "السلم 9", "السلم 10", "السلم 11"];

interface Values {
  provincialDirectorateId: string; centerId: string; employeeType: string; administrativeStatus: string;
  otherSectorText: string; delegatingBody: string; fullNameAr: string; fullNameFr: string; gender: string;
  birthDate: string; birthPlace: string; idDocumentType: string; idDocumentNumber: string; address: string; phone: string; email: string;
  educationLevel: string; diplomaTitle: string; specialty: string; diplomaDate: string; experienceDescription: string; otherQualifications: string;
  enGrade: string; enScale: string; rankNumber: string; hireDate: string; activityStatus: string;
  // معطيات المكوّن المستضاف
  gSeasonId: string; gFiliereId: string; gDiplomaYear: string; gStart: string; gRate: string; gMonths: string; gHours: string; gCount: string;
}

function initial(e?: EmployeeDetail): Values {
  const g = e?.guestContracts[0];
  const s = (v?: number | string | null) => (v == null ? "" : String(v));
  return {
    provincialDirectorateId: e ? String(e.provincialDirectorateId) : "", centerId: e?.centerId ? String(e.centerId) : "",
    employeeType: e?.employeeType ?? "", administrativeStatus: e?.administrativeStatus ?? "",
    otherSectorText: e?.otherSectorText ?? "", delegatingBody: e?.delegatingBody ?? "", fullNameAr: e?.fullNameAr ?? "", fullNameFr: e?.fullNameFr ?? "",
    gender: e?.gender ?? "", birthDate: e?.birthDate ?? "", birthPlace: e?.birthPlace ?? "", idDocumentType: e?.idDocumentType ?? "",
    idDocumentNumber: e?.idDocumentNumber ?? "", address: e?.address ?? "", phone: e?.phone ?? "", email: e?.email ?? "",
    educationLevel: e?.educationLevel ?? "", diplomaTitle: e?.diplomaTitle ?? "", specialty: e?.specialty ?? "", diplomaDate: e?.diplomaDate ?? "",
    experienceDescription: e?.experienceDescription ?? "", otherQualifications: e?.otherQualifications ?? "",
    enGrade: e?.enGrade ?? "", enScale: e?.enScale ?? "", rankNumber: e?.rankNumber ?? "", hireDate: e?.hireDate ?? "", activityStatus: e?.activityStatus ?? "ACTIVE",
    gSeasonId: s(g?.seasonId), gFiliereId: s(g?.filiereId), gDiplomaYear: s(g?.diplomaYear), gStart: g?.workStartDate ?? "",
    gRate: s(g?.hourlyRateMad), gMonths: s(g?.monthsPerYear), gHours: s(g?.hoursPerMonth), gCount: s(g?.beneficiariesCount),
  };
}

/**
 * Fiche d'un agent, dans l'ordre des maquettes : 1 type et statut administratif, 2 données personnelles, 3 formation et
 * expérience, 4 données des agents de l'Entraide Nationale, 5 formateur invité (première prestation).
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
  const seasons = useSeasons();

  const type = v.employeeType;
  const status = v.administrativeStatus;
  const noCenter = type === "PEDAGOGICAL_COORDINATOR";
  const centerRequired = !!type && type !== "PEDAGOGICAL_COORDINATOR" && type !== "ADMINISTRATIVE";
  const needsProvince = me.role === "ADMIN" && !employee && (noCenter || (type === "ADMINISTRATIVE" && !v.centerId));
  const en = status === "ENTRAIDE_NATIONALE";
  const guest = type === "GUEST_TRAINER";
  const centerId = v.centerId ?? employee?.centerId?.toString() ?? "";

  const centerFilieres = useQuery({
    queryKey: ["centers", centerId, "filieres"], enabled: guest && !!centerId,
    queryFn: () => api<CenterFiliere[]>(`/centers/${centerId}/filieres`),
  });
  const allFilieres = useQuery({
    queryKey: ["filieres", "options"], enabled: guest && !centerId,
    queryFn: () => api<PageResponse<FiliereSummary>>("/filieres", { query: { size: 100, active: true, sort: "code,asc" } }).then((p) => p.content),
  });
  const filiereOptions = centerId
    ? (centerFilieres.data ?? []).filter((f) => f.active).map((f) => ({ id: f.filiereId, nameAr: f.nameAr, nameFr: f.nameFr }))
    : (allFilieres.data ?? []).map((f) => ({ id: f.id, nameAr: f.nameAr, nameFr: f.nameFr }));

  const err = (k: keyof Values) => errors[k]?.message as string | undefined;
  const required = { required: t("common.required") };
  const n = (x: string) => (x === "" ? undefined : Number(x));
  const contractFilled = guest && !!v.gFiliereId;

  const save = useMutation({
    mutationFn: async (f: Values) => {
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
      const saved = employee
        ? await api<EmployeeDetail>(`/employees/${employee.id}`, { method: "PUT", body })
        : await api<EmployeeDetail>("/employees", { method: "POST", body });
      if (f.employeeType === "GUEST_TRAINER" && f.gFiliereId) {
        const contract = {
          seasonId: Number(f.gSeasonId || seasons.data?.find((x) => x.current)?.id), filiereId: Number(f.gFiliereId), diplomaYear: n(f.gDiplomaYear), workStartDate: f.gStart || undefined,
          hourlyRateMad: n(f.gRate), monthsPerYear: n(f.gMonths), hoursPerMonth: n(f.gHours), beneficiariesCount: n(f.gCount),
        };
        const existing = employee?.guestContracts[0];
        await (existing
          ? api(`/employees/${saved.id}/guest-contracts/${existing.id}`, { method: "PUT", body: contract })
          : api(`/employees/${saved.id}/guest-contracts`, { method: "POST", body: contract }));
      }
      return saved;
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

  const sections = [
    { id: "s1", title: t("hr.section.job") }, { id: "s2", title: t("hr.section.personal") }, { id: "s3", title: t("hr.section.qualifications") },
    { id: "s4", title: t("hr.section.en") }, { id: "s5", title: t("hr.section.guestPreview") },
  ];
  const withCurrent = (list: string[], cur?: string) => (cur && !list.includes(cur) ? [cur, ...list] : list);

  return (
    <form onSubmit={handleSubmit((f) => { setFormError(undefined); save.mutate(f); })} noValidate className="space-y-6">
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-4 py-3 text-sm text-danger">{formError}</p>}
      <NumberedSection id="s1" num={1} title={sections[0].title} tone="muted" banner={t("hr.banner.sections")}>
        <div className="grid gap-4 sm:grid-cols-2">
          {needsProvince ? (
            <Field label={t("hr.field.province")} required error={err("provincialDirectorateId")}>
              <Select invalid={!!errors.provincialDirectorateId} {...register("provincialDirectorateId", { validate: (x) => !needsProvince || !!x || t("common.required") })}>
                <option value="">{t("common.select")}</option>
                {provinces.data?.map((p) => <option key={p.id} value={p.id}>{name(p)}</option>)}
              </Select>
            </Field>
          ) : (
            <Field label={t("hr.field.province")}><Input disabled readOnly value={employee?.provincialDirectorateNameAr ?? me.provinceNameAr ?? ""} /></Field>
          )}
          <Field label={t("hr.field.center")} required={centerRequired} hint={employee ? t("hr.field.centerHint") : undefined} error={err("centerId")}>
            <Select disabled={!!employee || noCenter} invalid={!!errors.centerId} {...register("centerId", { validate: (x) => !centerRequired || !!x || t("common.required") })}>
              <option value="">{type === "ADMINISTRATIVE" || noCenter ? t("hr.provincial") : t("common.select")}</option>
              {employee?.centerId && <option value={employee.centerId}>{employee.centerNameAr}</option>}
              {centers.data?.filter((c) => c.id !== employee?.centerId).map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
            </Select>
          </Field>
        </div>
        <OptionField control={control} name="employeeType" legend={t("hr.legend.type")}
          options={TYPES.map((x) => ({ value: x, label: t(`hr.opt.type.${x}` as MessageKey) }))} />
        <OptionField control={control} name="administrativeStatus" legend={t("hr.legend.status")}
          options={STATUSES.map((x) => ({ value: x, label: t(`hr.opt.status.${x}` as MessageKey) }))} />
        {(errors.employeeType || errors.administrativeStatus) && <p role="alert" className="text-xs text-danger">{t("common.required")}</p>}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("hr.field.otherSector")} required={status === "OTHER_SECTOR"} error={err("otherSectorText")}>
            <Input disabled={status !== "OTHER_SECTOR"} invalid={!!errors.otherSectorText}
              {...register("otherSectorText", { validate: (x) => status !== "OTHER_SECTOR" || !!x.trim() || t("common.required") })} />
          </Field>
          <Field label={t("hr.field.delegating")} required={status === "FOREIGN_VOLUNTEER"} error={err("delegatingBody")}>
            <Input disabled={status !== "FOREIGN_VOLUNTEER"} invalid={!!errors.delegatingBody}
              {...register("delegatingBody", { validate: (x) => status !== "FOREIGN_VOLUNTEER" || !!x.trim() || t("common.required") })} />
          </Field>
        </div>
        {/* les deux groupes sont obligatoires : validation portée par des champs masqués */}
        <input type="hidden" {...register("employeeType", required)} />
        <input type="hidden" {...register("administrativeStatus", required)} />
      </NumberedSection>

      <NumberedSection id="s2" num={2} title={sections[1].title}>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-12">
          <Field label={t("hr.field.nameAr")} required error={err("fullNameAr")} className="lg:col-span-4"><Input invalid={!!errors.fullNameAr} {...register("fullNameAr", required)} /></Field>
          <Field label={t("hr.field.nameFr")} className="lg:col-span-4"><Input className="ltr-field" {...register("fullNameFr")} /></Field>
          <Field label={t("hr.field.gender")} required error={err("gender")} className="lg:col-span-2">
            <Select invalid={!!errors.gender} {...register("gender", required)}>
              <option value="">{t("common.select")}</option>
              <option value="F">{label("Gender", "F")}</option>
              <option value="M">{label("Gender", "M")}</option>
            </Select>
          </Field>
          <Field className="lg:col-span-2" label={t("hr.field.birthDate")}><Input type="date" className="ltr-field" {...register("birthDate")} /></Field>
          <Field className="lg:col-span-3" label={t("hr.field.birthPlace")}><Input {...register("birthPlace")} /></Field>
          <Field className="lg:col-span-3" label={t("hr.field.idType")}>
            <Select {...register("idDocumentType")}>
              <option value="">{t("common.select")}</option>
              {ID_TYPES.map((x) => <option key={x} value={x}>{label("IdDocumentType", x)}</option>)}
            </Select>
          </Field>
          <Field className="lg:col-span-2" label={t("hr.field.idNumber")}><Input className="ltr-field" {...register("idDocumentNumber")} /></Field>
          <Field label={t("hr.field.address")} className="lg:col-span-4"><Input {...register("address")} /></Field>
          <Field className="lg:col-span-4" label={t("hr.field.phone")}><Input type="tel" className="ltr-field" {...register("phone")} /></Field>
          <Field label={t("hr.field.email")} error={err("email")} className="lg:col-span-8"><Input type="email" className="ltr-field" {...register("email")} /></Field>
        </div>
      </NumberedSection>

      <NumberedSection id="s3" num={3} title={sections[2].title}>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-12">
          <Field className="lg:col-span-3" label={t("hr.field.education")}>
            <Select {...register("educationLevel")}>
              <option value="">{t("common.select")}</option>
              {LEVELS.map((x) => <option key={x} value={x}>{label("EducationLevel", x)}</option>)}
            </Select>
          </Field>
          <Field label={t("hr.field.diploma")} className="lg:col-span-3"><Input {...register("diplomaTitle")} /></Field>
          <Field className="lg:col-span-3" label={t("hr.field.specialty")}><Input {...register("specialty")} /></Field>
          <Field className="lg:col-span-3" label={t("hr.field.diplomaDate")}><Input type="date" className="ltr-field" {...register("diplomaDate")} /></Field>
          <Field label={t("hr.field.experience")} className="sm:col-span-2 lg:col-span-12"><Input {...register("experienceDescription")} /></Field>
          <Field label={t("hr.field.otherQualifications")} className="sm:col-span-2 lg:col-span-12"><Textarea {...register("otherQualifications")} /></Field>
        </div>
      </NumberedSection>

      <NumberedSection id="s4" num={4} title={sections[3].title} tone="info" banner={t("hr.banner.en")}>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          <Field label={t("hr.field.grade")}>
            <Select disabled={!en} {...register("enGrade")}>
              <option value="">{t("common.select")}</option>
              {withCurrent(GRADES, employee?.enGrade).map((x) => <option key={x} value={x}>{x}</option>)}
            </Select>
          </Field>
          <Field label={t("hr.field.scale")}>
            <Select disabled={!en} {...register("enScale")}>
              <option value="">{t("common.select")}</option>
              {withCurrent(SCALES, employee?.enScale).map((x) => <option key={x} value={x}>{x}</option>)}
            </Select>
          </Field>
          <Field label={t("hr.field.rank")} required={en} error={err("rankNumber")}>
            <Input className="ltr-field" disabled={!en} invalid={!!errors.rankNumber} {...register("rankNumber", { validate: (x) => !en || !!x.trim() || t("common.required") })} />
          </Field>
          <Field label={t("hr.field.hireDate")}><Input type="date" className="ltr-field" {...register("hireDate")} /></Field>
          <Field label={t("hr.field.activity")}>
            <Select {...register("activityStatus")}>{ACTIVITIES.map((x) => <option key={x} value={x}>{label("ActivityStatus", x)}</option>)}</Select>
          </Field>
        </div>
      </NumberedSection>

      <NumberedSection id="s5" num={5} title={sections[4].title} tone="muted" banner={guest ? (employee && employee.guestContracts.length > 1 ? t("hr.guest.manageInDetail") : undefined) : t("hr.guest.onlyGuest")}>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label={t("hr.guest.fullName")} className="lg:col-span-1"><Input disabled readOnly value={v.fullNameAr ?? ""} /></Field>
          <Field label={t("hr.guest.diplomaType")}><Input disabled readOnly value={v.diplomaTitle ?? ""} /></Field>
          <Field label={t("hr.guest.diplomaYearLong")}><Input type="number" min={1950} max={2100} className="ltr-field" disabled={!guest} {...register("gDiplomaYear")} /></Field>
          <Field label={t("hr.guest.startLong")}><Input type="date" className="ltr-field" disabled={!guest} {...register("gStart")} /></Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          <Field label={t("hr.guest.rateLong")}><Input type="number" step="0.01" min={0} className="ltr-field" disabled={!guest} {...register("gRate")} /></Field>
          <Field label={t("hr.guest.monthsLong")}><Input type="number" min={1} max={12} className="ltr-field" disabled={!guest} {...register("gMonths")} /></Field>
          <Field label={t("hr.guest.hoursLong")}><Input type="number" step="0.01" min={0} className="ltr-field" disabled={!guest} {...register("gHours")} /></Field>
          <Field label={t("hr.guest.filiereLong")}>
            <Select disabled={!guest} {...register("gFiliereId")}>
              <option value="">{t("common.select")}</option>
              {filiereOptions.map((f) => <option key={f.id} value={f.id}>{name(f)}</option>)}
            </Select>
          </Field>
          <Field label={t("hr.guest.countLong")}><Input type="number" min={0} className="ltr-field" disabled={!guest} {...register("gCount")} /></Field>
        </div>
      </NumberedSection>

      <div className="flex justify-start gap-3 pt-2">
        <Button type="submit" loading={save.isPending} className="min-h-12 px-7">{employee ? t("ben.saveChanges") : t("common.save")}</Button>
        <Button variant="secondary" className="min-h-12 px-6" onClick={() => router.back()}>{t("common.cancel")}</Button>
      </div>
    </form>
  );
}
