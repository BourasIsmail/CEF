"use client";

import { useMutation } from "@tanstack/react-query";
import { useRef } from "react";
import { useToast } from "@/components/ui/toast";
import { api, download } from "@/lib/api";
import { errorText } from "@/lib/errors";
import { useI18n, type MessageKey } from "@/lib/i18n";
import type { RequestDocumentSlot } from "@/lib/types";

const MAX_BYTES = 20 * 1024 * 1024;
export const mo = (bytes: number) => `${(bytes / 1024 / 1024).toFixed(1)} Mo`;

type Slot = Pick<RequestDocumentSlot, "typeCode" | "position" | "document">;

/** Les 8 pièces du formulaire (maquette « طلب جديد ») : étiquette « مرفوعة / ناقصة » et bouton de dépôt. */
export function DocumentChecklist({ requestId, slots, editable, onChange }: {
  requestId?: number; slots: Slot[]; editable: boolean; onChange: () => void;
}) {
  return (
    <div className="flex flex-col gap-2.5">
      {slots.map((slot) => <ChecklistRow key={slot.typeCode} requestId={requestId} slot={slot} editable={editable} onChange={onChange} />)}
    </div>
  );
}

function ChecklistRow({ requestId, slot, editable, onChange }: { requestId?: number; slot: Slot; editable: boolean; onChange: () => void }) {
  const { t } = useI18n();
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const d = slot.document;
  const typeName = t(`doc.${slot.typeCode}` as MessageKey);

  const upload = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append("file", file);
      return api(`/filiere-requests/${requestId}/documents/${slot.typeCode}`, { method: "POST", form });
    },
    onSuccess: () => { onChange(); toast.success(t("req.doc.uploaded")); },
    onError: (e) => toast.error(errorText(e, t)),
  });

  function pick(file?: File) {
    if (!file) return;
    if (file.size > MAX_BYTES) return toast.error(t("req.doc.tooLarge"));
    if (!file.name.toLowerCase().endsWith(".pdf") && file.type !== "application/pdf") return toast.error(t("req.doc.notPdf"));
    upload.mutate(file);
  }

  return (
    <div className={`flex flex-wrap items-center justify-between gap-3 rounded-[10px] border px-4 py-3.5 ${d ? "border-line bg-white" : "border-[#E3B6B0] bg-[#FFF8F7]"}`}>
      <div className="min-w-0 flex-[1_1_380px]">
        <div className="text-sm font-semibold leading-7">{slot.position}. {typeName}</div>
        <div className="mt-0.5 text-[13px] text-muted">{d ? `${d.originalFilename} · ${mo(d.sizeBytes)}` : t("req.doc.noFile")}</div>
      </div>
      <div className="flex items-center gap-2.5">
        <span className={`whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ${d ? "bg-[#DFF1E5] text-[#1C6B3A]" : "bg-[#F9E3E0] text-[#9A2B22]"}`}>
          {d ? t("req.doc.tagUp") : t("req.doc.tagMissing")}
        </span>
        {d && requestId && (
          <button type="button" className="min-h-11 rounded-lg px-3 text-sm font-semibold text-brand-700 hover:underline"
            onClick={() => download(`/filiere-requests/${requestId}/documents/${d.id}`, undefined, d.originalFilename).catch((e) => toast.error(errorText(e, t)))}>
            {t("req.doc.download")}
          </button>
        )}
        <input ref={input} type="file" accept="application/pdf,.pdf" className="hidden" aria-label={typeName} disabled={!editable || !requestId}
          onChange={(e) => { pick(e.target.files?.[0]); e.target.value = ""; }} />
        <button type="button" disabled={!editable || !requestId || upload.isPending} title={!requestId ? t("req.form.saveFirst") : undefined}
          onClick={() => input.current?.click()}
          className="min-h-11 rounded-lg border border-brand-600 bg-white px-4 text-sm font-semibold text-brand-700 hover:bg-brand-50 disabled:opacity-50">
          {d ? t("req.doc.replace") : t("req.doc.pick")}
        </button>
      </div>
    </div>
  );
}
