"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { RequestForm } from "@/components/requests/request-form";
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
      <div className="mb-5">
        <Link href="/requests" className="text-sm font-semibold text-brand-700 hover:underline">› {t("req.title")}</Link>
        <h1 className="mt-1.5 text-[28px] font-bold">{t(q.data.requestType === "CREATE" ? "req.form.titleCreate" : "req.form.titleModify")}</h1>
        <p className="mt-1 text-sm text-muted">{t("req.form.draftSubtitle", { no: q.data.requestNumber, center: q.data.centerNameAr })}</p>
      </div>
      <RequestForm request={q.data} />
    </>
  );
}
