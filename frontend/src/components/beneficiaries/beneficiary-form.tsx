"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox, Field, Input, Select, Textarea } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n } from "@/lib/i18n";
import { useCentersOptions, useSeasons } from "@/lib/queries";
import type { BeneficiaryDetail, CenterFiliere } from "@/lib/types";

const ID_TYPES = ["NATIONAL_ID", "PASSPORT", "RESIDENCE_CARD", "BIRTH_CERTIFICATE", "OTHER"] as const;
const LEVELS = ["NONE", "PRIMARY", "LOWER_SECONDARY", "UPPER_SECONDARY", "BACCALAUREATE", "UNIVERSITY", "VOCATIONAL", "OTHER"] as const;
const FAMILY = ["SINGLE", "MARRIED", "DIVORCED", "WIDOWED", "OTHER"] as const;
const DISABILITIES = ["MOTOR", "AUDITORY", "VISUAL", "INTELLECTUAL", "OTHER"] as const;
const MIGRATIONS = ["REGULAR", "IRREGULAR", "REFUGEE", "ASYLUM_SEEKER", "IRREGULAR_MIGRATION_SITUATION", "OTHER"] as const;

interface Values {
  centerId: string; rankNumber: string; registrationNumber: string; registrationDate: string; gender: "M" | "F" | "";
  fullNameAr: string; fullNameFr: string; birthDate: string; birthPlace: string;
  idDocumentType: string; idDocumentNumber: string; phone: string; educationLevel: string; address: string; additionalInfo: string;
  familyStatus: string; familyStatusOtherText: string;
  vulnerable: boolean; disability: boolean; disabilityType: string; disabilityOtherText: string;
  migrant: boolean; migrationStatus: string; migrationOtherText: string; originCountry: string; otherSocialSituation: string;
  enroll: boolean; filiereId: string; seasonId: string; startDate: string; examDate: string;
}

const today = () => new Date().toISOString().slice(0, 10);

function initial(b: BeneficiaryDetail | undefined, centerId: string): Values {
  return {
    centerId: b ? String(b.centerId) : centerId,
    rankNumber: b ? String(b.rankNumber) : "", registrationNumber: b?.registrationNumber ?? "", registrationDate: b?.registrationDate ?? today(),
    gender: b?.gender ?? "", fullNameAr: b?.fullNameAr ?? "", fullNameFr: b?.fullNameFr ?? "", birthDate: b?.birthDate ?? "", birthPlace: b?.birthPlace ?? "",
    idDocumentType: b?.idDocumentType ?? "", idDocumentNumber: b?.idDocumentNumber ?? "", phone: b?.phone ?? "", educationLevel: b?.educationLevel ?? "",
    address: b?.address ?? "", additionalInfo: b?.additionalInfo ?? "",
    familyStatus: b?.familyStatus ?? "", familyStatusOtherText: b?.familyStatusOtherText ?? "",
    vulnerable: b?.vulnerable ?? false,
    disability: !!b?.disabilityType, disabilityType: b?.disabilityType ?? "", disabilityOtherText: b?.disabilityOtherText ?? "",
    migrant: !!b?.migrationStatus, migrationStatus: b?.migrationStatus ?? "", migrationOtherText: b?.migrationOtherText ?? "", originCountry: b?.originCountry ?? "",
    otherSocialSituation: b?.otherSocialSituation ?? "",
    enroll: false, filiereId: "", seasonId: "", startDate: today(), examDate: "",
  };
}

function ageOf(birth: string): number | null {
  if (!birth) return null;
  const d = new Date(birth);
  if (Number.isNaN(d.getTime())) return null;
  const now = new Date();
  let age = now.getFullYear() - d.getFullYear();
  if (now.getMonth() < d.getMonth() || (now.getMonth() === d.getMonth() && now.getDate() < d.getDate())) age--;
  return age >= 0 ? age : null;
}

/** Fiche d'un bénéficiaire : données personnelles (situation familiale en dernier), situation sociale à choix multiple, inscription. */
export function BeneficiaryForm({ beneficiary }: { beneficiary?: BeneficiaryDetail }) {
  const { t, name, label, num } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const qc = useQueryClient();
  const [formError, setFormError] = useState<string>();
  const centers = useCentersOptions();
  const seasons = useSeasons();

  const { register, handleSubmit, control, setError, formState: { errors } } =
    useForm<Values>({ defaultValues: initial(beneficiary, "") });
  const v = useWatch({ control });
  const centerId = v.centerId ?? "";
  const filieres = useQuery({
    queryKey: ["centers", centerId, "filieres"],
    queryFn: () => api<CenterFiliere[]>(`/centers/${centerId}/filieres`),
    enabled: !!centerId && !beneficiary,
  });
  const age = ageOf(v.birthDate ?? "");

  const save = useMutation({
    mutationFn: (f: Values) => {
      const body = {
        centerId: Number(f.centerId),
        rankNumber: f.rankNumber ? Number(f.rankNumber) : undefined,
        registrationNumber: f.registrationNumber || undefined,
        registrationDate: f.registrationDate, gender: f.gender,
        fullNameAr: f.fullNameAr, fullNameFr: f.fullNameFr || undefined, birthDate: f.birthDate, birthPlace: f.birthPlace || undefined,
        idDocumentType: f.idDocumentType || undefined, idDocumentNumber: f.idDocumentNumber || undefined,
        address: f.address || undefined, phone: f.phone || undefined, educationLevel: f.educationLevel || undefined,
        familyStatus: f.familyStatus || undefined, familyStatusOtherText: f.familyStatus === "OTHER" ? f.familyStatusOtherText : undefined,
        additionalInfo: f.additionalInfo || undefined,
        vulnerable: f.vulnerable,
        disabilityType: f.disability ? f.disabilityType || undefined : undefined,
        disabilityOtherText: f.disability && f.disabilityType === "OTHER" ? f.disabilityOtherText : undefined,
        migrationStatus: f.migrant ? f.migrationStatus || undefined : undefined,
        migrationOtherText: f.migrant && f.migrationStatus === "OTHER" ? f.migrationOtherText : undefined,
        originCountry: f.migrant ? f.originCountry || undefined : undefined,
        otherSocialSituation: f.otherSocialSituation || undefined,
        enrollment: !beneficiary && f.enroll
          ? { filiereId: Number(f.filiereId), seasonId: f.seasonId ? Number(f.seasonId) : undefined, startDate: f.startDate, examDate: f.examDate || undefined }
          : undefined,
      };
      return beneficiary
        ? api<BeneficiaryDetail>(`/beneficiaries/${beneficiary.id}`, { method: "PUT", body })
        : api<BeneficiaryDetail>("/beneficiaries", { method: "POST", body });
    },
    onSuccess: (b) => {
      qc.invalidateQueries({ queryKey: ["beneficiaries"] });
      qc.invalidateQueries({ queryKey: ["centers"] });
      toast.success(t(beneficiary ? "ben.updated" : "ben.created"));
      router.push(`/beneficiaries/${b.id}`);
    },
    onError: (e) => {
      if (!applyServerErrors(e, setError)) setFormError(errorText(e, t));
      window.scrollTo({ top: 0, behavior: "smooth" });
    },
  });

  const required = { required: t("common.required") };
  const err = (k: keyof Values) => errors[k]?.message as string | undefined;

  return (
    <form onSubmit={handleSubmit((f) => { setFormError(undefined); save.mutate(f); })} noValidate className="space-y-6">
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-4 py-3 text-sm text-danger">{formError}</p>}

      <Card title={t("ben.section.personal")}>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label={t("ben.field.center")} required error={err("centerId")} className="lg:col-span-3">
            <Select disabled={!!beneficiary} invalid={!!errors.centerId} {...register("centerId", required)}>
              <option value="">{t("common.select")}</option>
              {beneficiary && <option value={beneficiary.centerId}>{beneficiary.centerNameAr}</option>}
              {centers.data?.filter((c) => c.id !== beneficiary?.centerId).map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.field.rank")} hint={beneficiary ? undefined : t("ben.field.rankHint")} error={err("rankNumber")}>
            <Input type="number" min={1} className="ltr-field" {...register("rankNumber")} />
          </Field>
          <Field label={t("ben.field.reg")} hint={beneficiary ? undefined : t("ben.field.regHint")} error={err("registrationNumber")}>
            <Input className="ltr-field" {...register("registrationNumber")} />
          </Field>
          <Field label={t("ben.field.regDate")} required error={err("registrationDate")}>
            <Input type="date" className="ltr-field" invalid={!!errors.registrationDate} {...register("registrationDate", required)} />
          </Field>
          <Field label={t("ben.field.nameAr")} required error={err("fullNameAr")} className="lg:col-span-2">
            <Input invalid={!!errors.fullNameAr} {...register("fullNameAr", required)} />
          </Field>
          <Field label={t("ben.field.gender")} required error={err("gender")}>
            <Select invalid={!!errors.gender} {...register("gender", required)}>
              <option value="">{t("common.select")}</option>
              <option value="F">{label("Gender", "F")}</option>
              <option value="M">{label("Gender", "M")}</option>
            </Select>
          </Field>
          <Field label={t("ben.field.nameFr")} error={err("fullNameFr")} className="lg:col-span-2">
            <Input className="ltr-field" {...register("fullNameFr")} />
          </Field>
          <Field label={t("ben.field.birthDate")} required error={err("birthDate")}>
            <Input type="date" className="ltr-field" invalid={!!errors.birthDate} {...register("birthDate", required)} />
          </Field>
          <Field label={t("ben.field.age")}><Input disabled readOnly value={age == null ? "" : num(age)} /></Field>
          <Field label={t("ben.field.birthPlace")}><Input {...register("birthPlace")} /></Field>
          <Field label={t("ben.field.idType")}>
            <Select {...register("idDocumentType")}>
              <option value="">{t("common.select")}</option>
              {ID_TYPES.map((x) => <option key={x} value={x}>{label("IdDocumentType", x)}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.field.idNumber")}><Input className="ltr-field" {...register("idDocumentNumber")} /></Field>
          <Field label={t("ben.field.phone")}><Input type="tel" className="ltr-field" {...register("phone")} /></Field>
          <Field label={t("ben.field.education")}>
            <Select {...register("educationLevel")}>
              <option value="">{t("common.select")}</option>
              {LEVELS.map((x) => <option key={x} value={x}>{label("EducationLevel", x)}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.field.address")} className="lg:col-span-2"><Input {...register("address")} /></Field>
          <Field label={t("ben.field.info")} className="sm:col-span-2 lg:col-span-3"><Textarea {...register("additionalInfo")} /></Field>
          <Field label={t("ben.field.family")}>
            <Select {...register("familyStatus")}>
              <option value="">{t("common.select")}</option>
              {FAMILY.map((x) => <option key={x} value={x}>{label("FamilyStatus", x)}</option>)}
            </Select>
          </Field>
          {v.familyStatus === "OTHER" && (
            <Field label={t("ben.field.familyOther")} required error={err("familyStatusOtherText")}>
              <Input invalid={!!errors.familyStatusOtherText} {...register("familyStatusOtherText", required)} />
            </Field>
          )}
        </div>
      </Card>

      <Card title={t("ben.section.social")}>
        <p className="mb-4 text-xs text-muted">{t("ben.section.social.hint")}</p>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="rounded-lg border border-line p-4"><Checkbox label={t("ben.field.vulnerable")} {...register("vulnerable")} /></div>

          <div className="space-y-3 rounded-lg border border-line p-4">
            <Checkbox label={t("ben.field.disability")} {...register("disability")} />
            {v.disability && (
              <>
                <Field label={t("ben.field.disabilityType")} required error={err("disabilityType")}>
                  <Select invalid={!!errors.disabilityType} {...register("disabilityType", { validate: (x) => !v.disability || !!x || t("common.required") })}>
                    <option value="">{t("common.select")}</option>
                    {DISABILITIES.map((x) => <option key={x} value={x}>{label("DisabilityType", x)}</option>)}
                  </Select>
                </Field>
                {v.disabilityType === "OTHER" && (
                  <Field label={t("ben.field.disabilityOther")} required error={err("disabilityOtherText")}>
                    <Input invalid={!!errors.disabilityOtherText} {...register("disabilityOtherText", { validate: (x) => !(v.disability && v.disabilityType === "OTHER") || !!x.trim() || t("common.required") })} />
                  </Field>
                )}
              </>
            )}
          </div>

          <div className="space-y-3 rounded-lg border border-line p-4">
            <Checkbox label={t("ben.field.migrant")} {...register("migrant")} />
            {v.migrant && (
              <>
                <Field label={t("ben.field.migrationStatus")} required error={err("migrationStatus")}>
                  <Select invalid={!!errors.migrationStatus} {...register("migrationStatus", { validate: (x) => !v.migrant || !!x || t("common.required") })}>
                    <option value="">{t("common.select")}</option>
                    {MIGRATIONS.map((x) => <option key={x} value={x}>{label("MigrationStatus", x)}</option>)}
                  </Select>
                </Field>
                {v.migrationStatus === "OTHER" && (
                  <Field label={t("ben.field.migrationOther")} required error={err("migrationOtherText")}>
                    <Input invalid={!!errors.migrationOtherText} {...register("migrationOtherText", { validate: (x) => !(v.migrant && v.migrationStatus === "OTHER") || !!x.trim() || t("common.required") })} />
                  </Field>
                )}
                <Field label={t("ben.field.origin")}><Input {...register("originCountry")} /></Field>
              </>
            )}
          </div>

          <div className="rounded-lg border border-line p-4">
            <Field label={t("ben.field.otherSocial")} hint={t("ben.field.otherSocialHint")}><Input {...register("otherSocialSituation")} /></Field>
          </div>
        </div>
      </Card>

      {!beneficiary && (
        <Card title={t("ben.section.training")}>
          <Checkbox label={t("ben.enroll.now")} {...register("enroll")} />
          {v.enroll && (
            <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Field label={t("ben.enroll.filiere")} required error={err("filiereId")}>
                <Select invalid={!!errors.filiereId} {...register("filiereId", { validate: (x) => !v.enroll || !!x || t("common.required") })}>
                  <option value="">{t("common.select")}</option>
                  {filieres.data?.filter((f) => f.active).map((f) => <option key={f.filiereId} value={f.filiereId}>{name(f)}</option>)}
                </Select>
              </Field>
              <Field label={t("ben.enroll.season")}>
                <Select {...register("seasonId")}>
                  <option value="">{t("ben.enroll.currentSeason")}</option>
                  {seasons.data?.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
                </Select>
              </Field>
              <Field label={t("ben.enroll.start")} required error={err("startDate")}>
                <Input type="date" className="ltr-field" {...register("startDate", { validate: (x) => !v.enroll || !!x || t("common.required") })} />
              </Field>
              <Field label={t("ben.enroll.exam")}><Input type="date" className="ltr-field" {...register("examDate")} /></Field>
            </div>
          )}
        </Card>
      )}

      <div className="sticky bottom-0 -mx-4 flex justify-end gap-2 border-t border-line bg-surface/95 px-4 py-3 backdrop-blur lg:-mx-8 lg:px-8">
        <Button variant="secondary" onClick={() => router.back()}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>
    </form>
  );
}
