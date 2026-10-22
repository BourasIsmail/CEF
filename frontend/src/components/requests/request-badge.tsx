"use client";

import { useI18n } from "@/lib/i18n";
import type { RequestStatus } from "@/lib/types";

/** Couleurs de la maquette « طلبات الشعب ». */
const colors: Record<RequestStatus, string> = {
  DRAFT: "bg-[#eef0f3] text-[#3f4a5a]",
  SUBMITTED: "bg-[#FBF1DC] text-[#6B4300]",
  RETURNED: "bg-[#FFE9D2] text-[#8A3F00]",
  REGIONAL_VALIDATED: "bg-[#E4EEF9] text-[#1B4A7F]",
  APPROVED: "bg-[#DFF1E5] text-[#1C6B3A]",
  REJECTED: "bg-[#F9E3E0] text-[#9A2B22]",
};

export function RequestStatusBadge({ status, large }: { status: RequestStatus; large?: boolean }) {
  const { label } = useI18n();
  return (
    <span className={`inline-block whitespace-nowrap rounded-full font-semibold ${large ? "px-4 py-2 text-sm" : "px-2.5 py-1 text-xs"} ${colors[status]}`}>
      {label("RequestStatus", status)}
    </span>
  );
}

const STEP_LABELS = [1, 2, 3, 4] as const;

/** Parcours province → région → national → système, partagé par la liste (statique) et le détail. */
export function RequestPath({ state }: { state?: ("done" | "cur" | "todo" | "bad")[] }) {
  const { t } = useI18n();
  return (
    <ol className="flex flex-wrap gap-3">
      {STEP_LABELS.map((n, i) => {
        const s = state?.[i];
        const box = s === "cur" ? "border-[#C9922B] bg-[#FBF1DC]" : s === "done" ? "border-[#9db8de] bg-[#e6eef9]"
          : s === "bad" ? "border-[#E3B6B0] bg-[#F9E3E0]" : "border-line bg-surface";
        const dot = !state ? "bg-brand-600" : s === "done" ? "bg-brand-600" : s === "cur" ? "bg-[#8A5A00]" : s === "bad" ? "bg-[#9A2B22]" : "bg-[#bcc1c9]";
        return (
          <li key={n} className={`flex min-w-[190px] flex-1 items-center gap-3 rounded-[10px] border px-3.5 py-3 text-sm leading-6 ${state ? box : "border-transparent bg-surface"}`}>
            <span className={`grid size-8 shrink-0 place-items-center rounded-full font-bold text-white ${dot}`}>{n}</span>
            <span><strong>{t(`req.step.${n}.who`)}</strong><br />{t(`req.step.${n}.what`)}</span>
          </li>
        );
      })}
    </ol>
  );
}
