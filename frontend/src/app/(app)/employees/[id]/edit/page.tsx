"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { EmployeeForm } from "@/components/employees/employee-form";
import { AssignmentsCard, TeachingCard } from "@/components/employees/employee-sections";
import { PageHeader } from "@/components/ui/card";
import { ErrorState, Spinner } from "@/components/ui/states";
import { api } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { can } from "@/lib/permissions";
import { useI18n } from "@/lib/i18n";
import type { EmployeeDetail } from "@/lib/types";

export default function EditEmployeePage() {
  const { id } = useParams<{ id: string }>();
  const me = useMe();
  const { t, label } = useI18n();
  const q = useQuery({ queryKey: ["employees", id], queryFn: () => api<EmployeeDetail>(`/employees/${id}`) });
  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  return (
    <>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link href={`/employees/${id}`} className="text-sm font-semibold text-brand-700 hover:underline">› {t("hr.title")}</Link>
          <h1 className="mt-1.5 text-3xl font-bold">{`${t("hr.edit")}: ${q.data.fullNameAr}`}</h1>
          <p className="mt-1 text-sm text-muted">
            {label("EmployeeType", q.data.employeeType)}
            {q.data.fullNameFr && <> · <span className="ltr-field inline-block">{q.data.fullNameFr}</span></>}
            {q.data.rankNumber && <> · <span className="ltr-field inline-block">{q.data.rankNumber}</span></>}
            {q.data.centerNameAr && <> · {q.data.centerNameAr}</>}
          </p>
        </div>
        <span className={`rounded-full px-4 py-2 text-sm font-semibold ${q.data.activityStatus === "ACTIVE" ? "bg-ok-50 text-ok" : "bg-warn-50 text-warn"}`}>{label("ActivityStatus", q.data.activityStatus)}</span>
      </div>
      <EmployeeForm employee={q.data} />
      <div className="mt-6 space-y-5">
        <AssignmentsCard employee={q.data} writer={can.write(me)} />
        <TeachingCard employee={q.data} writer={can.write(me)} />
      </div>
    </>
  );
}
