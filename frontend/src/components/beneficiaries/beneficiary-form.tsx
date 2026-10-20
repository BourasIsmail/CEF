"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { ExamSessionModal } from "./exam-session-modal";
import { Button } from "@/components/ui/button";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { OptionField } from "@/components/ui/option-group";
import { NumberedSection, SectionNav } from "@/components/ui/section";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { COUNTRIES } from "@/lib/countries";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n, type MessageKey } from "@/lib/i18n";
import { useCentersOptions, useSeasons } from "@/lib/queries";
import type { BeneficiaryDetail, Bulletin, CenterFiliere, EnrollmentStatus, EnrollmentView, SessionType, StatCategory } from "@/lib/types";

const ID_TYPES = ["NATIONAL_ID", "PASSPORT", "RESIDENCE_CARD", "BIRTH_CERTIFICATE", "OTHER"] as const;
const LEVELS = ["NONE", "PRIMARY", "LOWER_SECONDARY", "UPPER_SECONDARY", "BACCALAUREATE", "UNIVERSITY", "VOCATIONAL", "OTHER"] as const;
const FAMILY = ["SINGLE", "MARRIED", "DIVORCED", "WIDOWED", "OTHER"] as const;
const DISABILITIES = ["MOTOR", "AUDITORY", "VISUAL", "INTELLECTUAL", "OTHER"] as const;
const MIGRATIONS = ["REGULAR", "IRREGULAR", "REFUGEE", "ASYLUM_SEEKER", "IRREGULAR_MIGRATION_SITUATION", "OTHER"] as const;
const STATUSES: EnrollmentStatus[] = ["ENROLLED", "DEPARTED", "GRADUATED", "FAILED", "ABSENT"];
type Cat = "vulnerable" | "disability" | "migrant" | "other";

interface Values {
  centerId: string; registrationNumber: string; registrationDate: string; gender: "M" | "F" | "";
  fullNameAr: string; fullNameFr: string; birthDate: string; birthPlace: string;
  idDocumentType: string; idDocumentNumber: string; phone: string; address: string; educationLevel: string; additionalInfo: string;
  familyStatus: string; familyStatusOtherText: string;
  categories: Cat[]; disabilityType: string; disabilityOtherText: string;
  migrationStatus: string; migrationOtherText: string; originCountry: string; otherSocialSituation: string;
  seasonId: string; filiereId: string; startDate: string; examDate: string;
  diplomaSerial: string; diplomaReceived: string; diplomaInfo: string;
}

const today = () => new Date().toISOString().slice(0, 10);

/** L'inscription affichée dans le formulaire : la plus récente. */
function latestEnrollment(b?: BeneficiaryDetail): EnrollmentView | undefined {
  return b ? [...b.enrollments].sort((x, y) => y.startDate.localeCompare(x.startDate))[0] : undefined;
}

function initial(b: BeneficiaryDetail | undefined, e: EnrollmentView | undefined): Values {
  const categories: Cat[] = [];
  if (b?.vulnerable) categories.push("vulnerable");
  if (b?.disabilityType) categories.push("disability");
  if (b?.migrationStatus) categories.push("migrant");
  if (b?.otherSocialSituation) categories.push("other");
  return {
    centerId: b ? String(b.centerId) : "", registrationNumber: b?.registrationNumber ?? "", registrationDate: b?.registrationDate ?? today(),
    gender: b?.gender ?? "", fullNameAr: b?.fullNameAr ?? "", fullNameFr: b?.fullNameFr ?? "", birthDate: b?.birthDate ?? "", birthPlace: b?.birthPlace ?? "",
    idDocumentType: b?.idDocumentType ?? "", idDocumentNumber: b?.idDocumentNumber ?? "", phone: b?.phone ?? "", address: b?.address ?? "",
    educationLevel: b?.educationLevel ?? "", additionalInfo: b?.additionalInfo ?? "",
    familyStatus: b?.familyStatus ?? "", familyStatusOtherText: b?.familyStatusOtherText ?? "",
    categories, disabilityType: b?.disabilityType ?? "", disabilityOtherText: b?.disabilityOtherText ?? "",
    migrationStatus: b?.migrationStatus ?? "", migrationOtherText: b?.migrationOtherText ?? "", originCountry: b?.originCountry ?? "",
    otherSocialSituation: b?.otherSocialSituation ?? "",
    seasonId: e ? String(e.seasonId) : "", filiereId: e ? String(e.filiereId) : "", startDate: e?.startDate ?? today(), examDate: e?.examDate ?? "",
    diplomaSerial: e?.diploma?.serialNumber ?? "", diplomaReceived: e?.diploma?.receivedDate ?? "", diplomaInfo: e?.diploma?.additionalInfo ?? "",
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

/** Même règle de priorité que le serveur : migrant > handicap > autre > reste. */
function categoryOf(c: Cat[]): StatCategory {
  if (c.includes("migrant")) return "MIGRANT";
  if (c.includes("disability")) return "DISABLED";
  if (c.includes("other")) return "OTHER";
  return "REMAINING";
}

/**
 * Fiche d'un bénéficiaire, dans l'ordre des maquettes : 1 données personnelles (situation familiale en dernier),
 * 2 situation sociale à choix multiple, 3 formation, 4 résultats de fin de formation, 5 diplôme.
 */
export function BeneficiaryForm({ beneficiary }: { beneficiary?: BeneficiaryDetail }) {
  const { t, name, label, num, locale } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const qc = useQueryClient();
  const [formError, setFormError] = useState<string>();
  const [session, setSession] = useState<SessionType | null>(null);
  const centers = useCentersOptions();
  const seasons = useSeasons();
  const enrollment = latestEnrollment(beneficiary);

  const { register, handleSubmit, control, setError, formState: { errors } } =
    useForm<Values>({ defaultValues: initial(beneficiary, enrollment) });
  const v = useWatch({ control });
  const cats = (v.categories ?? []) as Cat[];
  const centerId = v.centerId ?? "";

  const filieres = useQuery({
    queryKey: ["centers", centerId, "filieres"], queryFn: () => api<CenterFiliere[]>(`/centers/${centerId}/filieres`), enabled: !!centerId,
  });
  const bulletin = useQuery({
    queryKey: ["bulletin", enrollment?.id], queryFn: () => api<Bulletin>(`/enrollments/${enrollment!.id}/bulletin`), enabled: !!enrollment,
  });
  const age = ageOf(v.birthDate ?? "");
  const status: EnrollmentStatus = enrollment?.status ?? "ENROLLED";
  const departed = status === "DEPARTED";
  const graduated = status === "GRADUATED";
  const lockedFiliere = !!enrollment && (enrollment.sessions.length > 0 || departed);

  const save = useMutation({
    mutationFn: async (f: Values) => {
      const c = f.categories as Cat[];
      const dis = c.includes("disability");
      const mig = c.includes("migrant");
      const enrollmentBody = f.filiereId
        ? { filiereId: Number(f.filiereId), seasonId: f.seasonId && !enrollment ? Number(f.seasonId) : undefined, startDate: f.startDate, examDate: f.examDate || undefined }
        : undefined;
      const body = {
        centerId: Number(f.centerId), registrationNumber: f.registrationNumber || undefined, registrationDate: f.registrationDate, gender: f.gender,
        fullNameAr: f.fullNameAr, fullNameFr: f.fullNameFr || undefined, birthDate: f.birthDate, birthPlace: f.birthPlace || undefined,
        idDocumentType: f.idDocumentType || undefined, idDocumentNumber: f.idDocumentNumber || undefined,
        address: f.address || undefined, phone: f.phone || undefined, educationLevel: f.educationLevel || undefined,
        familyStatus: f.familyStatus || undefined, familyStatusOtherText: f.familyStatus === "OTHER" ? f.familyStatusOtherText : undefined,
        additionalInfo: f.additionalInfo || undefined,
        vulnerable: c.includes("vulnerable"),
        disabilityType: dis ? f.disabilityType || undefined : undefined,
        disabilityOtherText: dis && f.disabilityType === "OTHER" ? f.disabilityOtherText : undefined,
        migrationStatus: mig ? f.migrationStatus || undefined : undefined,
        migrationOtherText: mig && f.migrationStatus === "OTHER" ? f.migrationOtherText : undefined,
        originCountry: mig ? f.originCountry || undefined : undefined,
        otherSocialSituation: c.includes("other") ? f.otherSocialSituation || undefined : undefined,
        enrollment: !beneficiary ? enrollmentBody : undefined,
      };
      const saved = beneficiary
        ? await api<BeneficiaryDetail>(`/beneficiaries/${beneficiary.id}`, { method: "PUT", body })
        : await api<BeneficiaryDetail>("/beneficiaries", { method: "POST", body });
      if (beneficiary && enrollmentBody) {
        if (enrollment && !departed) await api(`/enrollments/${enrollment.id}`, { method: "PUT", body: { ...enrollmentBody, filiereId: lockedFiliere ? enrollment.filiereId : enrollmentBody.filiereId } });
        if (!enrollment) await api(`/beneficiaries/${beneficiary.id}/enrollments`, { method: "POST", body: enrollmentBody });
      }
      if (beneficiary && enrollment && graduated && f.diplomaSerial.trim()) {
        await api(`/enrollments/${enrollment.id}/diploma`, {
          method: "PUT", body: { serialNumber: f.diplomaSerial.trim(), receivedDate: f.diplomaReceived || undefined, additionalInfo: f.diplomaInfo || undefined },
        });
      }
      return saved;
    },
    onSuccess: (b) => {
      qc.invalidateQueries({ queryKey: ["beneficiaries"] });
      qc.invalidateQueries({ queryKey: ["centers"] });
      qc.invalidateQueries({ queryKey: ["bulletin"] });
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
  const sections = [
    { id: "p1", title: t("ben.section.personal") }, { id: "p2", title: t("ben.section.social") }, { id: "p3", title: t("ben.section.training") },
    { id: "p4", title: t("ben.section.results") }, { id: "p5", title: t("ben.section.diploma") },
  ];
  const lastSession = bulletin.data?.sessions.at(-1);
  const sessionLabel = lastSession ? label("SessionType", lastSession.type) : t("ben.results.none");
  const countryOptions = COUNTRIES.some((c) => c.code === v.originCountry) || !v.originCountry ? COUNTRIES : [{ code: v.originCountry, ar: v.originCountry, fr: v.originCountry }, ...COUNTRIES];

  return (
    <form onSubmit={handleSubmit((f) => { setFormError(undefined); save.mutate(f); })} noValidate className="space-y-6">
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-4 py-3 text-sm text-danger">{formError}</p>}
      <SectionNav sections={sections} />

      <NumberedSection id="p1" num={1} title={sections[0].title}>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label={t("ben.field.rank")}><Input disabled readOnly value={beneficiary ? num(beneficiary.rankNumber) : t("ben.rank.auto")} /></Field>
          <Field label={t("ben.field.reg")} hint={beneficiary ? undefined : t("ben.field.regHint")} error={err("registrationNumber")}><Input className="ltr-field" {...register("registrationNumber")} /></Field>
          <Field label={t("ben.field.regDate")} required error={err("registrationDate")}><Input type="date" className="ltr-field" invalid={!!errors.registrationDate} {...register("registrationDate", required)} /></Field>
          <Field label={t("ben.field.gender")} required error={err("gender")}>
            <Select invalid={!!errors.gender} {...register("gender", required)}>
              <option value="">{t("common.select")}</option>
              <option value="F">{label("Gender", "F")}</option>
              <option value="M">{label("Gender", "M")}</option>
            </Select>
          </Field>
          <Field label={t("ben.field.nameAr")} required error={err("fullNameAr")} className="lg:col-span-2"><Input invalid={!!errors.fullNameAr} {...register("fullNameAr", required)} /></Field>
          <Field label={t("ben.field.nameFr")} error={err("fullNameFr")} className="lg:col-span-2"><Input className="ltr-field" {...register("fullNameFr")} /></Field>
          <Field label={t("ben.field.birthDate")} required error={err("birthDate")}><Input type="date" className="ltr-field" invalid={!!errors.birthDate} {...register("birthDate", required)} /></Field>
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
          <Field label={t("ben.field.address")} className="lg:col-span-2"><Input {...register("address")} /></Field>
          <Field label={t("ben.field.education")}>
            <Select {...register("educationLevel")}>
              <option value="">{t("common.select")}</option>
              {LEVELS.map((x) => <option key={x} value={x}>{label("EducationLevel", x)}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.field.info")} className="sm:col-span-2 lg:col-span-4"><Textarea {...register("additionalInfo")} /></Field>
        </div>
        <OptionField control={control} name="familyStatus" legend={t("ben.legend.family")}
          options={FAMILY.map((x) => ({ value: x, label: x === "OTHER" ? t("ben.family.other") : label("FamilyStatus", x) }))} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("ben.field.familyOther")} required={v.familyStatus === "OTHER"} error={err("familyStatusOtherText")}>
            <Input disabled={v.familyStatus !== "OTHER"} invalid={!!errors.familyStatusOtherText}
              {...register("familyStatusOtherText", { validate: (x) => v.familyStatus !== "OTHER" || !!x.trim() || t("common.required") })} />
          </Field>
        </div>
      </NumberedSection>

      <NumberedSection id="p2" num={2} title={sections[1].title} tone="info"
        banner={t("ben.banner.social", { category: t(`category.${categoryOf(cats)}` as MessageKey) })}>
        <OptionField control={control} name="categories" multiple legend={t("ben.cat.legend")}
          options={[
            { value: "vulnerable", label: t("ben.cat.vulnerable") }, { value: "disability", label: t("ben.cat.disability") },
            { value: "migrant", label: t("ben.cat.migrant") }, { value: "other", label: t("ben.cat.other") },
          ]} />
        <OptionField control={control} name="disabilityType" legend={t("ben.legend.disability")} disabled={!cats.includes("disability")}
          options={DISABILITIES.map((x) => ({ value: x, label: label("DisabilityType", x) }))} />
        <OptionField control={control} name="migrationStatus" legend={t("ben.legend.migration")} disabled={!cats.includes("migrant")}
          options={MIGRATIONS.map((x) => ({ value: x, label: label("MigrationStatus", x) }))} />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label={t("ben.field.origin")}>
            <Select disabled={!cats.includes("migrant")} {...register("originCountry")}>
              <option value="">{t("common.select")}</option>
              {countryOptions.map((c) => <option key={c.code} value={c.code}>{locale === "ar" ? c.ar : c.fr}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.field.disabilityOther")} error={err("disabilityOtherText")}>
            <Input disabled={!(cats.includes("disability") && v.disabilityType === "OTHER")} invalid={!!errors.disabilityOtherText}
              {...register("disabilityOtherText", { validate: (x) => !(cats.includes("disability") && v.disabilityType === "OTHER") || !!x.trim() || t("common.required") })} />
          </Field>
          <Field label={t("ben.field.migrationOther")} error={err("migrationOtherText")}>
            <Input disabled={!(cats.includes("migrant") && v.migrationStatus === "OTHER")} invalid={!!errors.migrationOtherText}
              {...register("migrationOtherText", { validate: (x) => !(cats.includes("migrant") && v.migrationStatus === "OTHER") || !!x.trim() || t("common.required") })} />
          </Field>
          <Field label={t("ben.field.otherSocial")} error={err("otherSocialSituation")}>
            <Input disabled={!cats.includes("other")} invalid={!!errors.otherSocialSituation}
              {...register("otherSocialSituation", { validate: (x) => !cats.includes("other") || !!x.trim() || t("common.required") })} />
          </Field>
        </div>
      </NumberedSection>

      <NumberedSection id="p3" num={3} title={sections[2].title} tone="muted" banner={t("ben.banner.training")}>
        <div>
          <StatusPills status={status} />
          {beneficiary && <p className="mt-1 text-xs text-muted">{t("ben.enroll.statusAuto")}</p>}
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label={t("ben.enroll.season")}>
            <Select disabled={!!enrollment} {...register("seasonId")}>
              <option value="">{t("ben.enroll.currentSeason")}</option>
              {seasons.data?.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.field.center")} required error={err("centerId")}>
            <Select disabled={!!beneficiary} invalid={!!errors.centerId} {...register("centerId", required)}>
              <option value="">{t("common.select")}</option>
              {beneficiary && <option value={beneficiary.centerId}>{beneficiary.centerNameAr}</option>}
              {centers.data?.filter((c) => c.id !== beneficiary?.centerId).map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.enroll.filiere")} hint={lockedFiliere ? t("ben.enroll.lockedFiliere") : undefined} error={err("filiereId")}>
            <Select disabled={lockedFiliere || !centerId} {...register("filiereId")}>
              <option value="">{t("common.select")}</option>
              {filieres.data?.filter((f) => f.active || f.filiereId === enrollment?.filiereId).map((f) => <option key={f.filiereId} value={f.filiereId}>{name(f)}</option>)}
            </Select>
          </Field>
          <Field label={t("ben.enroll.start")} required={!!v.filiereId} error={err("startDate")}>
            <Input type="date" className="ltr-field" disabled={departed} {...register("startDate", { validate: (x) => !v.filiereId || !!x || t("common.required") })} />
          </Field>
          <Field label={t("ben.enroll.exam")}><Input type="date" className="ltr-field" disabled={departed} {...register("examDate")} /></Field>
          <Field label={t("ben.enroll.departDate")}><Input type="date" className="ltr-field" disabled readOnly value={enrollment?.departureDate ?? ""} /></Field>
          <Field label={t("ben.enroll.departReason")} className="sm:col-span-2"><Input disabled readOnly value={enrollment?.departureReason ?? ""} /></Field>
        </div>
      </NumberedSection>

      <NumberedSection id="p4" num={4} title={sections[3].title} tone="warn" banner={t("ben.banner.results")}>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label={t("ben.exam.number")}><Input disabled readOnly className="ltr-field" value={lastSession?.examNumber ?? ""} /></Field>
          <Field label={t("ben.results.currentSession")}><Input disabled readOnly value={sessionLabel} /></Field>
          <Field label={t("ben.exam.overall")}><Input disabled readOnly value={lastSession?.overallAverage != null ? num(lastSession.overallAverage, 2) : ""} /></Field>
          <Field label={t("ben.exam.mention")}><Input disabled readOnly value={lastSession?.mention ?? ""} /></Field>
        </div>
        {enrollment && !departed && (
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => setSession("NORMAL")}>{t("ben.results.manage")} — {label("SessionType", "NORMAL")}</Button>
            {bulletin.data && (bulletin.data.makeupAllowed || bulletin.data.sessions.some((s) => s.type === "MAKEUP")) && (
              <Button variant="secondary" onClick={() => setSession("MAKEUP")}>{t("ben.results.manage")} — {label("SessionType", "MAKEUP")}</Button>
            )}
          </div>
        )}
      </NumberedSection>

      <NumberedSection id="p5" num={5} title={sections[4].title} tone="warn" banner={t("ben.banner.diploma")}>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label={t("ben.diploma.received")}><Input type="date" className="ltr-field" disabled={!graduated} {...register("diplomaReceived")} /></Field>
          <Field label={t("ben.diploma.serial")} className="lg:col-span-2"><Input className="ltr-field" disabled={!graduated} {...register("diplomaSerial")} /></Field>
          <Field label={t("ben.diploma.info")} className="sm:col-span-2 lg:col-span-4"><Textarea disabled={!graduated} {...register("diplomaInfo")} /></Field>
        </div>
      </NumberedSection>

      <div className="sticky bottom-0 -mx-4 flex justify-start gap-3 border-t border-line bg-surface/95 px-4 py-3 backdrop-blur lg:-mx-8 lg:px-8">
        <Button type="submit" loading={save.isPending} className="min-h-12 px-7">{t("common.save")}</Button>
        <Button variant="secondary" className="min-h-12 px-6" onClick={() => router.back()}>{t("common.cancel")}</Button>
      </div>

      {enrollment && session && <ExamSessionModal open enrollmentId={enrollment.id} type={session} onClose={() => setSession(null)} />}
    </form>
  );
}

/** Statut d'inscription : affichage seul, en puces (il est déduit des résultats). */
function StatusPills({ status }: { status: EnrollmentStatus }) {
  const { t, label } = useI18n();
  return (
    <fieldset className="min-w-0 border-0 p-0">
      <legend className="mb-2.5 p-0 text-[13px] font-medium">{t("ben.legend.status")}</legend>
      <div className="flex flex-wrap gap-2.5">
        {STATUSES.map((s) => (
          <span key={s} className={`flex min-h-11 items-center gap-2 rounded-lg border px-4 text-sm ${s === status ? "border-brand-600 bg-brand-50 font-semibold" : "border-line bg-white text-muted"}`}>
            <input type="radio" disabled readOnly checked={s === status} className="accent-brand-600" aria-label={label("EnrollmentStatus", s)} /> {label("EnrollmentStatus", s)}
          </span>
        ))}
      </div>
    </fieldset>
  );
}
