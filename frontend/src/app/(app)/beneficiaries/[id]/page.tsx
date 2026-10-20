"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { EnrollmentResults } from "@/components/beneficiaries/enrollment-results";
import { EnrollmentSection } from "@/components/beneficiaries/enrollment-section";
import { AuditHistory } from "@/components/audit/history";
import { Button } from "@/components/ui/button";
import { Badge, Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm";
import { ErrorState, Spinner } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api, download } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { errorText } from "@/lib/errors";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import { countryName } from "@/lib/countries";
import type { BeneficiaryDetail } from "@/lib/types";

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 border-b border-line py-2 text-sm last:border-0">
      <dt className="text-muted">{label}</dt>
      <dd className="text-end font-medium">{children || "—"}</dd>
    </div>
  );
}

export default function BeneficiaryDetailPage() {
  const { id } = useParams<{ id: string }>();
  const me = useMe();
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();
  const { t, label, date, num, locale } = useI18n();
  const [pdfBusy, setPdfBusy] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [showHistory, setShowHistory] = useState(false);

  const q = useQuery({ queryKey: ["beneficiaries", id], queryFn: () => api<BeneficiaryDetail>(`/beneficiaries/${id}`) });
  const remove = useMutation({
    mutationFn: () => api(`/beneficiaries/${id}`, { method: "DELETE" }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["beneficiaries"] }); toast.success(t("ben.deleted")); router.replace("/beneficiaries"); },
    onError: (e) => { setDeleting(false); toast.error(errorText(e, t)); },
  });

  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  const b = q.data;
  const writer = can.write(me) && (me.role === "ADMIN" || me.provinceId === b.provincialDirectorateId);
  const latest = [...b.enrollments].sort((x, y) => y.startDate.localeCompare(x.startDate))[0];
  const social = [
    b.vulnerable && t("ben.field.vulnerable"),
    b.disabilityType && `${t("ben.field.disability")} (${label("DisabilityType", b.disabilityType)}${b.disabilityOtherText ? `: ${b.disabilityOtherText}` : ""})`,
    b.migrationStatus && `${t("ben.field.migrant")} (${label("MigrationStatus", b.migrationStatus)}${b.migrationOtherText ? `: ${b.migrationOtherText}` : ""}${b.originCountry ? ` — ${countryName(b.originCountry, locale)}` : ""})`,
    b.otherSocialSituation && `${t("ben.field.otherSocial")}: ${b.otherSocialSituation}`,
  ].filter(Boolean) as string[];

  const statusTone = { ENROLLED: "bg-blue-50 text-blue-800", DEPARTED: "bg-warn-50 text-warn", GRADUATED: "bg-ok-50 text-ok", FAILED: "bg-danger-50 text-danger", ABSENT: "bg-surface text-muted" } as const;
  const madeUpPass = latest?.status === "GRADUATED" && latest.sessions.some((x) => x.type === "MAKEUP" && x.result === "PASSED");
  const statusText = latest ? (madeUpPass ? t("ben.chip.afterMakeup", { status: label("EnrollmentStatus", latest.status) }) : label("EnrollmentStatus", latest.status)) : t("ben.noEnrollment");
  const tiles: [string, string][] = [
    [t("ben.field.rank"), num(b.rankNumber)],
    [t("ben.col.age"), t("ben.ageYears", { n: num(b.age) })],
    [t("ben.tile.category"), t(`category.${b.category}`)],
    [t("ben.field.family"), b.familyStatus ? `${label("FamilyStatus", b.familyStatus)}${b.familyStatusOtherText ? `: ${b.familyStatusOtherText}` : ""}` : "—"],
  ];

  async function pdf() {
    if (!latest) return;
    setPdfBusy(true);
    try {
      await download(`/enrollments/${latest.id}/bulletin.pdf`, { lang: locale }, `bulletin-${b.registrationNumber}.pdf`);
    } catch (e) {
      toast.error(errorText(e, t));
    } finally {
      setPdfBusy(false);
    }
  }

  return (
    <>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div>
          <Link href="/beneficiaries" className="text-sm font-semibold text-brand-700 hover:underline">› {t("ben.title")}</Link>
          <h1 className="mt-1.5 text-3xl font-bold">{b.fullNameAr}</h1>
          <p className="mt-1 text-sm text-muted">
            {b.fullNameFr && <span className="ltr-field inline-block">{b.fullNameFr}</span>}
            {b.fullNameFr && " · "}{t("ben.col.reg")} <span className="ltr-field inline-block">{b.registrationNumber}</span>
            {" · "}{b.centerNameAr}
            {latest && <> · {locale === "fr" ? latest.filiereNameFr : latest.filiereNameAr} · {latest.seasonLabel}</>}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          {latest && <Button variant="secondary" loading={pdfBusy} onClick={pdf}>{t("ben.detail.pdf")}</Button>}
          {writer && <Button variant="secondary" onClick={() => router.push(`/beneficiaries/${b.id}/edit`)}>{t("ben.detail.editFile")}</Button>}
          <span className={`rounded-full px-4 py-2 text-sm font-semibold ${latest ? statusTone[latest.status] : "bg-surface text-muted"}`}>{statusText}</span>
        </div>
      </div>

      <div className="space-y-5">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {tiles.map(([k, v]) => (
            <div key={k} className="rounded-xl border border-line bg-white px-5 py-4">
              <div className="text-sm text-muted">{k}</div>
              <div className="mt-1 text-lg font-semibold">{v}</div>
            </div>
          ))}
        </div>

        {latest && <EnrollmentResults enrollment={latest} writer={writer} />}

        <h2 className="pt-4 text-xl font-bold">{t("ben.detail.file")}</h2>
        <div className="grid gap-6 lg:grid-cols-2">
          <Card title={t("ben.section.personal")}>
            <dl>
              <Info label={t("ben.field.rank")}>{num(b.rankNumber)}</Info>
              <Info label={t("ben.field.regDate")}>{date(b.registrationDate)}</Info>
              <Info label={t("ben.field.gender")}>{label("Gender", b.gender)}</Info>
              <Info label={t("ben.field.birthDate")}>{date(b.birthDate)} ({num(b.age)})</Info>
              <Info label={t("ben.field.birthPlace")}>{b.birthPlace}</Info>
              <Info label={t("ben.field.idType")}>{b.idDocumentType ? `${label("IdDocumentType", b.idDocumentType)} ${b.idDocumentNumber ?? ""}` : ""}</Info>
              <Info label={t("ben.field.phone")}><span className="ltr-field inline-block">{b.phone}</span></Info>
              <Info label={t("ben.field.education")}>{b.educationLevel ? label("EducationLevel", b.educationLevel) : ""}</Info>
              <Info label={t("ben.field.address")}>{b.address}</Info>
              <Info label={t("ben.field.family")}>{b.familyStatus ? `${label("FamilyStatus", b.familyStatus)}${b.familyStatusOtherText ? `: ${b.familyStatusOtherText}` : ""}` : ""}</Info>
              {b.additionalInfo && <Info label={t("ben.field.info")}>{b.additionalInfo}</Info>}
            </dl>
          </Card>
          <Card title={t("ben.section.social")}>
            {social.length === 0 ? <p className="text-sm text-muted">—</p> : (
              <ul className="mb-4 flex flex-wrap gap-2">{social.map((s) => <li key={s}><Badge tone="brand">{s}</Badge></li>)}</ul>
            )}
            <dl><Info label={t("ben.field.category")}><Badge tone={b.category === "REMAINING" ? "neutral" : "brand"}>{t(`category.${b.category}`)}</Badge></Info></dl>
          </Card>
        </div>

        <EnrollmentSection beneficiary={b} writer={writer} />

        {writer && <div><Button variant="danger" icon={<Trash2 className="size-4" />} onClick={() => setDeleting(true)}>{t("common.delete")}</Button></div>}

        {showHistory
          ? <AuditHistory path={`/beneficiaries/${b.id}/history`} title={t("ben.section.history")} />
          : <Button variant="secondary" onClick={() => setShowHistory(true)}>{t("ben.section.history")}</Button>}
      </div>

      <ConfirmDialog open={deleting} danger loading={remove.isPending} title={t("common.delete")} message={t("ben.delete.confirm", { name: b.fullNameAr })}
        onClose={() => setDeleting(false)} onConfirm={() => remove.mutate()} />
    </>
  );
}
