"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { RequestForm } from "@/components/requests/request-form";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";

export default function NewRequestPage() {
  const me = useMe();
  const router = useRouter();
  const { t } = useI18n();
  useEffect(() => { if (!can.write(me)) router.replace("/requests"); }, [me, router]);
  if (!can.write(me)) return null;
  return (
    <>
      <div className="mb-5">
        <Link href="/requests" className="text-sm font-semibold text-brand-700 hover:underline">› {t("req.title")}</Link>
        <h1 className="mt-1.5 text-[28px] font-bold">{t("req.form.titleCreate")}</h1>
      </div>
      <RequestForm />
    </>
  );
}
