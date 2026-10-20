"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { BeneficiaryForm } from "@/components/beneficiaries/beneficiary-form";
import { PageHeader } from "@/components/ui/card";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";

export default function NewBeneficiaryPage() {
  const me = useMe();
  const router = useRouter();
  const { t } = useI18n();
  useEffect(() => { if (!can.write(me)) router.replace("/beneficiaries"); }, [me, router]);
  if (!can.write(me)) return null;
  return (
    <>
      <PageHeader back={{ href: "/beneficiaries", label: t("ben.title") }} title={t("ben.new")} />
      <BeneficiaryForm />
    </>
  );
}
