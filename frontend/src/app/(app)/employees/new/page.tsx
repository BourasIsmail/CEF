"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { EmployeeForm } from "@/components/employees/employee-form";
import { PageHeader } from "@/components/ui/card";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";

export default function NewEmployeePage() {
  const me = useMe();
  const router = useRouter();
  const { t } = useI18n();
  useEffect(() => { if (!can.write(me)) router.replace("/employees"); }, [me, router]);
  if (!can.write(me)) return null;
  return (
    <>
      <PageHeader back={{ href: "/employees", label: t("hr.title") }} title={t("hr.new")} />
      <EmployeeForm />
    </>
  );
}
