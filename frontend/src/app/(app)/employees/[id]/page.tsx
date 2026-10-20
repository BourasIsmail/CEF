"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Trash2 } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { AuditHistory } from "@/components/audit/history";
import { AssignmentsCard, GuestContractsCard, TeachingCard } from "@/components/employees/employee-sections";
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
import type { EmployeeDetail } from "@/lib/types";

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 border-b border-line py-2 text-sm last:border-0">
      <dt className="text-muted">{label}</dt>
      <dd className="text-end font-medium">{children || "—"}</dd>
    </div>
  );
}

export default function EmployeeDetailPage() {
  const { id } = useParams<{ id: string }>();
  const me = useMe();
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();
  const { t, label, date } = useI18n();
  const [deleting, setDeleting] = useState(false);
  const [history, setHistory] = useState(false);

  const q = useQuery({ queryKey: ["employees", id], queryFn: () => api<EmployeeDetail>(`/employees/${id}`) });
  const remove = useMutation({
    mutationFn: () => api(`/employees/${id}`, { method: "DELETE" }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["employees"] }); qc.invalidateQueries({ queryKey: ["centers"] }); toast.success(t("hr.deleted")); router.replace("/employees"); },
    onError: (e) => { setDeleting(false); toast.error(errorText(e, t)); },
  });

  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  const e = q.data;
  const writer = can.write(me) && (me.role === "ADMIN" || me.provinceId === e.provincialDirectorateId);

  return (
    <>
      <Link href="/employees" className="mb-2 inline-block text-sm text-brand-700 hover:underline">← {t("hr.title")}</Link>
      <PageHeader
        title={<span className="flex flex-wrap items-center gap-3">{e.fullNameAr} <Badge tone="brand">{label("EmployeeType", e.employeeType)}</Badge>
          <Badge tone={e.activityStatus === "ACTIVE" ? "ok" : "warn"}>{label("ActivityStatus", e.activityStatus)}</Badge></span>}
        subtitle={<>{e.fullNameFr && <span className="ltr-field inline-block">{e.fullNameFr} · </span>}{e.centerNameAr ?? e.provincialDirectorateNameAr}</>}
        actions={writer && <>
          <Button variant="secondary" icon={<Pencil className="size-4" />} onClick={() => router.push(`/employees/${e.id}/edit`)}>{t("common.edit")}</Button>
          <Button variant="danger" icon={<Trash2 className="size-4" />} onClick={() => setDeleting(true)}>{t("common.delete")}</Button>
        </>}
      />
      <div className="space-y-6">
        <div className="grid gap-6 lg:grid-cols-2">
          <Card title={t("hr.section.personal")}>
            <dl>
              <Info label={t("hr.field.gender")}>{label("Gender", e.gender)}</Info>
              <Info label={t("hr.field.birthDate")}>{e.birthDate ? date(e.birthDate) : ""}{e.birthPlace ? ` — ${e.birthPlace}` : ""}</Info>
              <Info label={t("hr.field.idType")}>{e.idDocumentType ? `${label("IdDocumentType", e.idDocumentType)} ${e.idDocumentNumber ?? ""}` : ""}</Info>
              <Info label={t("hr.field.phone")}><span className="ltr-field inline-block">{e.phone}</span></Info>
              <Info label={t("hr.field.email")}><span className="ltr-field inline-block">{e.email}</span></Info>
              <Info label={t("hr.field.address")}>{e.address}</Info>
            </dl>
          </Card>
          <Card title={t("hr.section.job")}>
            <dl>
              <Info label={t("hr.field.status")}>{label("AdministrativeStatus", e.administrativeStatus)}</Info>
              {e.otherSectorText && <Info label={t("hr.field.otherSector")}>{e.otherSectorText}</Info>}
              {e.delegatingBody && <Info label={t("hr.field.delegating")}>{e.delegatingBody}</Info>}
              <Info label={t("hr.field.center")}>{e.centerNameAr ?? t("hr.provincial")}</Info>
              <Info label={t("hr.field.hireDate")}>{e.hireDate ? date(e.hireDate) : ""}</Info>
              {e.administrativeStatus === "ENTRAIDE_NATIONALE" && (
                <>
                  <Info label={t("hr.field.rank")}><span className="ltr-field inline-block">{e.rankNumber}</span></Info>
                  <Info label={t("hr.field.grade")}>{e.enGrade}</Info>
                  <Info label={t("hr.field.scale")}>{e.enScale}</Info>
                </>
              )}
            </dl>
          </Card>
        </div>
        <Card title={t("hr.section.qualifications")}>
          <dl>
            <Info label={t("hr.field.education")}>{e.educationLevel ? label("EducationLevel", e.educationLevel) : ""}</Info>
            <Info label={t("hr.field.diploma")}>{e.diplomaTitle}{e.diplomaDate ? ` (${date(e.diplomaDate)})` : ""}</Info>
            <Info label={t("hr.field.specialty")}>{e.specialty}</Info>
            <Info label={t("hr.field.experience")}>{e.experienceDescription}</Info>
            {e.otherQualifications && <Info label={t("hr.field.otherQualifications")}>{e.otherQualifications}</Info>}
          </dl>
        </Card>
        {e.extras.length > 0 && (
          <Card title={t("hr.extras.title")}>
            <dl>{e.extras.map((x) => <Info key={x.id} label={x.label}>{x.value}</Info>)}</dl>
          </Card>
        )}
        <AssignmentsCard employee={e} writer={writer} />
        <TeachingCard employee={e} writer={writer} />
        <GuestContractsCard employee={e} writer={writer} />
        {history
          ? <AuditHistory path={`/employees/${e.id}/history`} title={t("hr.section.history")} />
          : <Button variant="secondary" onClick={() => setHistory(true)}>{t("hr.section.history")}</Button>}
      </div>
      <ConfirmDialog open={deleting} danger loading={remove.isPending} title={t("common.delete")} message={t("hr.delete.confirm", { name: e.fullNameAr })}
        onClose={() => setDeleting(false)} onConfirm={() => remove.mutate()} />
    </>
  );
}
