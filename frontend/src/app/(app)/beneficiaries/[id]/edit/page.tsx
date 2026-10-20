"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { BeneficiaryForm } from "@/components/beneficiaries/beneficiary-form";
import { ErrorState, Spinner } from "@/components/ui/states";
import { api } from "@/lib/api";
import { useI18n } from "@/lib/i18n";
import type { BeneficiaryDetail } from "@/lib/types";

export default function EditBeneficiaryPage() {
  const { id } = useParams<{ id: string }>();
  const { t, label } = useI18n();
  const q = useQuery({ queryKey: ["beneficiaries", id], queryFn: () => api<BeneficiaryDetail>(`/beneficiaries/${id}`) });
  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  const latest = [...q.data.enrollments].sort((x, y) => y.startDate.localeCompare(x.startDate))[0];
  return (
    <>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link href={`/beneficiaries/${id}`} className="text-sm font-semibold text-brand-700 hover:underline">› {t("ben.title")}</Link>
          <h1 className="mt-1.5 text-3xl font-bold">{`${t("ben.edit")}: ${q.data.fullNameAr}`}</h1>
          <p className="mt-1 text-sm text-muted">
            {q.data.fullNameFr && <><span className="ltr-field inline-block">{q.data.fullNameFr}</span> · </>}
            {t("ben.col.reg")} <span className="ltr-field inline-block">{q.data.registrationNumber}</span> · {q.data.centerNameAr}
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          {latest && <span className="rounded-full bg-brand-50 px-4 py-2 text-sm font-semibold text-brand-700">{t("ben.col.status")}: {label("EnrollmentStatus", latest.status)}</span>}
          {latest && <Link href={`/beneficiaries/${id}/bulletin`} className="rounded-lg border border-brand-600/40 px-4 py-2 text-sm font-semibold text-brand-700 hover:bg-brand-50">{t("ben.exam.bulletin")}</Link>}
        </div>
      </div>
      <BeneficiaryForm beneficiary={q.data} />
    </>
  );
}
