"use client";

import { useQuery } from "@tanstack/react-query";
import { useParams } from "next/navigation";
import { RequestForm } from "@/components/requests/request-form";
import { PageHeader } from "@/components/ui/card";
import { ErrorState, Spinner } from "@/components/ui/states";
import { api } from "@/lib/api";
import { useI18n } from "@/lib/i18n";
import type { RequestDetail } from "@/lib/types";

export default function EditRequestPage() {
  const { id } = useParams<{ id: string }>();
  const { t } = useI18n();
  const q = useQuery({ queryKey: ["requests", id], queryFn: () => api<RequestDetail>(`/filiere-requests/${id}`) });
  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  return (
    <>
      <PageHeader title={t("req.edit")} subtitle={<span className="ltr-field inline-block">{q.data.requestNumber}</span>} />
      <RequestForm request={q.data} />
    </>
  );
}
