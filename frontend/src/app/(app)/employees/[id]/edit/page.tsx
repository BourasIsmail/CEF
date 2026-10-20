"use client";

import { useQuery } from "@tanstack/react-query";
import { useParams } from "next/navigation";
import { EmployeeForm } from "@/components/employees/employee-form";
import { PageHeader } from "@/components/ui/card";
import { ErrorState, Spinner } from "@/components/ui/states";
import { api } from "@/lib/api";
import { useI18n } from "@/lib/i18n";
import type { EmployeeDetail } from "@/lib/types";

export default function EditEmployeePage() {
  const { id } = useParams<{ id: string }>();
  const { t } = useI18n();
  const q = useQuery({ queryKey: ["employees", id], queryFn: () => api<EmployeeDetail>(`/employees/${id}`) });
  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  return (
    <>
      <PageHeader title={`${t("hr.edit")} : ${q.data.fullNameAr}`} />
      <EmployeeForm employee={q.data} />
    </>
  );
}
