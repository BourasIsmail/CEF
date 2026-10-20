"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { CenterForm } from "@/components/centers/center-form";
import { PageHeader } from "@/components/ui/card";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";

export default function NewCenterPage() {
  const me = useMe();
  const router = useRouter();
  const { t } = useI18n();
  useEffect(() => { if (!can.write(me)) router.replace("/centers"); }, [me, router]);
  if (!can.write(me)) return null;
  return (
    <>
      <PageHeader title={t("centers.new")} />
      <CenterForm />
    </>
  );
}
