"use client";

import { Badge } from "@/components/ui/card";
import { useI18n } from "@/lib/i18n";
import type { RequestStatus } from "@/lib/types";

const tone = { DRAFT: "neutral", SUBMITTED: "warn", RETURNED: "danger", REGIONAL_VALIDATED: "brand", APPROVED: "ok", REJECTED: "danger" } as const;

export function RequestStatusBadge({ status }: { status: RequestStatus }) {
  const { label } = useI18n();
  return <Badge tone={tone[status]}>{label("RequestStatus", status)}</Badge>;
}
