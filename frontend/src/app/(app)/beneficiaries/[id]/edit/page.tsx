"use client";

import { useQuery } from "@tanstack/react-query";
import { useParams } from "next/navigation";
import { BeneficiaryForm } from "@/components/beneficiaries/beneficiary-form";
import { PageHeader } from "@/components/ui/card";
import { ErrorState, Spinner } from "@/components/ui/states";
import { api } from "@/lib/api";
import { useI18n } from "@/lib/i18n";
import type { BeneficiaryDetail } from "@/lib/types";

export default function EditBeneficiaryPage() {
  const { id } = useParams<{ id: string }>();
  const { t } = useI18n();
  const q = useQuery({ queryKey: ["beneficiaries", id], queryFn: () => api<BeneficiaryDetail>(`/beneficiaries/${id}`) });
  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  return (
    <>
      <PageHeader title={`${t("ben.edit")} : ${q.data.fullNameAr}`} subtitle={<span className="ltr-field inline-block">{q.data.registrationNumber}</span>} />
      <BeneficiaryForm beneficiary={q.data} />
    </>
  );
}
