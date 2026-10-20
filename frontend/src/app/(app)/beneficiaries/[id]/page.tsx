"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Trash2 } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { EnrollmentSection } from "@/components/beneficiaries/enrollment-section";
import { StatusBadge } from "@/components/beneficiaries/status-badge";
import { AuditHistory } from "@/components/audit/history";
import { Button } from "@/components/ui/button";
import { Badge, Card, PageHeader } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm";
import { ErrorState, Spinner } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
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

  return (
    <>
      <Link href="/beneficiaries" className="mb-2 inline-block text-sm text-brand-700 hover:underline">← {t("ben.title")}</Link>
      <PageHeader
        title={<span className="flex flex-wrap items-center gap-3">{b.fullNameAr} <StatusBadge status={latest?.status} /></span>}
        subtitle={<>{b.fullNameFr && <span className="ltr-field inline-block">{b.fullNameFr} · </span>}<span className="ltr-field inline-block">{b.registrationNumber}</span> · {b.centerNameAr}</>}
        actions={writer && <>
          <Button variant="secondary" icon={<Pencil className="size-4" />} onClick={() => router.push(`/beneficiaries/${b.id}/edit`)}>{t("common.edit")}</Button>
          <Button variant="danger" icon={<Trash2 className="size-4" />} onClick={() => setDeleting(true)}>{t("common.delete")}</Button>
        </>}
      />

      <div className="space-y-6">
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

        {showHistory
          ? <AuditHistory path={`/beneficiaries/${b.id}/history`} title={t("ben.section.history")} />
          : <Button variant="secondary" onClick={() => setShowHistory(true)}>{t("ben.section.history")}</Button>}
      </div>

      <ConfirmDialog open={deleting} danger loading={remove.isPending} title={t("common.delete")} message={t("ben.delete.confirm", { name: b.fullNameAr })}
        onClose={() => setDeleting(false)} onConfirm={() => remove.mutate()} />
    </>
  );
}
