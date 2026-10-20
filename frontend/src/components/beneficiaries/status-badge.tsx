"use client";

import { Badge } from "@/components/ui/card";
import { useI18n } from "@/lib/i18n";
import type { EnrollmentStatus, ExamResult } from "@/lib/types";

const tone = { ENROLLED: "brand", DEPARTED: "warn", GRADUATED: "ok", FAILED: "danger", ABSENT: "neutral" } as const;
const resultTone = { PASSED: "ok", FAILED: "danger", ABSENT: "neutral", INCOMPLETE: "warn" } as const;

export function StatusBadge({ status }: { status?: EnrollmentStatus | null }) {
  const { t, label } = useI18n();
  if (!status) return <span className="text-muted">{t("ben.noEnrollment")}</span>;
  return <Badge tone={tone[status]}>{label("EnrollmentStatus", status)}</Badge>;
}

export function ResultBadge({ result }: { result: ExamResult }) {
  const { label } = useI18n();
  return <Badge tone={resultTone[result]}>{label("ExamResult", result)}</Badge>;
}
