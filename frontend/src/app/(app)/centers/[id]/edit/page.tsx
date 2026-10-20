"use client";

import { useQuery } from "@tanstack/react-query";
import { useParams } from "next/navigation";
import { CenterForm } from "@/components/centers/center-form";
import { PageHeader } from "@/components/ui/card";
import { ErrorState, Spinner } from "@/components/ui/states";
import { api } from "@/lib/api";
import { useI18n } from "@/lib/i18n";
import type { CenterDetail } from "@/lib/types";

export default function EditCenterPage() {
  const { id } = useParams<{ id: string }>();
  const { t, name, num } = useI18n();
  const q = useQuery({ queryKey: ["centers", id], queryFn: () => api<CenterDetail>(`/centers/${id}`) });
  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  return (
    <>
      <PageHeader title={`${t("centers.edit")}: ${name(q.data)}`}
        subtitle={t("centers.header.counts", { b: num(q.data.beneficiariesCount), f: num(q.data.filieresCount), s: num(q.data.staffCount) })} />
      <CenterForm center={q.data} />
    </>
  );
}
