"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Download, FileUp, Pencil, Trash2 } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useRef, useState, type ReactNode } from "react";
import { AuditHistory } from "@/components/audit/history";
import { RequestStatusBadge } from "@/components/requests/request-badge";
import { Button } from "@/components/ui/button";
import { Card, PageHeader } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm";
import { Field, Input, Textarea } from "@/components/ui/form";
import { Modal } from "@/components/ui/modal";
import { ErrorState, Spinner } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api, download } from "@/lib/api";
import { errorText } from "@/lib/errors";
import { useI18n, type MessageKey } from "@/lib/i18n";
import type { RequestDetail, RequestDocumentSlot } from "@/lib/types";

const MAX_BYTES = 20 * 1024 * 1024;

type Decision = "validate" | "approve" | "return" | "reject";
const PATHS: Record<Decision, string> = { validate: "regional-validation", approve: "approval", return: "return", reject: "rejection" };
const COMMENT_REQUIRED: Record<Decision, boolean> = { validate: false, approve: false, return: true, reject: true };

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 border-b border-line py-2 text-sm last:border-0">
      <dt className="text-muted">{label}</dt>
      <dd className="text-end font-medium">{children || "—"}</dd>
    </div>
  );
}

export default function RequestDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();
  const { t, label, date, dateTime, num } = useI18n();
  const [decision, setDecision] = useState<Decision | null>(null);
  const [confirm, setConfirm] = useState<"delete" | "submit" | "withdraw" | RequestDocumentSlot | null>(null);
  const [history, setHistory] = useState(false);

  const q = useQuery({ queryKey: ["requests", id], queryFn: () => api<RequestDetail>(`/filiere-requests/${id}`) });
  const refresh = () => { qc.invalidateQueries({ queryKey: ["requests"] }); qc.invalidateQueries({ queryKey: ["filieres"] }); qc.invalidateQueries({ queryKey: ["centers"] }); };
  const fail = (e: unknown) => { setConfirm(null); toast.error(errorText(e, t)); };

  const simple = useMutation({
    mutationFn: (a: "submit" | "withdraw") => api(`/filiere-requests/${id}/${a}`, { method: "POST", body: {} }).then(() => a),
    onSuccess: (a) => { refresh(); setConfirm(null); toast.success(t(`req.done.${a}` as MessageKey)); }, onError: fail,
  });
  const remove = useMutation({
    mutationFn: () => api(`/filiere-requests/${id}`, { method: "DELETE" }),
    onSuccess: () => { refresh(); toast.success(t("req.deleted")); router.replace("/requests"); }, onError: fail,
  });
  const removeDoc = useMutation({
    mutationFn: (d: RequestDocumentSlot) => api(`/filiere-requests/${id}/documents/${d.document!.id}`, { method: "DELETE" }),
    onSuccess: () => { refresh(); setConfirm(null); }, onError: fail,
  });

  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  const r = q.data;
  const can = (a: string) => r.availableActions.includes(a);
  const editable = can("EDIT");
  const uploaded = r.documents.filter((d) => d.document).length;

  return (
    <>
      <Link href="/requests" className="mb-2 inline-block text-sm text-brand-700 hover:underline">← {t("req.title")}</Link>
      <PageHeader
        title={<span className="flex flex-wrap items-center gap-3">{r.proposedNameAr} <RequestStatusBadge status={r.status} /></span>}
        subtitle={<><span className="ltr-field inline-block">{r.requestNumber}</span> · {label("RequestType", r.requestType)} · {r.centerNameAr}</>}
        actions={<>
          {editable && <Button variant="secondary" icon={<Pencil className="size-4" />} onClick={() => router.push(`/requests/${r.id}/edit`)}>{t("common.edit")}</Button>}
          {can("DELETE") && <Button variant="danger" icon={<Trash2 className="size-4" />} onClick={() => setConfirm("delete")}>{t("req.action.delete")}</Button>}
          {can("WITHDRAW") && <Button variant="secondary" onClick={() => setConfirm("withdraw")}>{t("req.action.withdraw")}</Button>}
          {can("SUBMIT") && <Button onClick={() => setConfirm("submit")}>{t("req.action.submit")}</Button>}
          {can("VALIDATE") && <Button onClick={() => setDecision("validate")}>{t("req.action.validate")}</Button>}
          {can("APPROVE") && <Button onClick={() => setDecision("approve")}>{t("req.action.approve")}</Button>}
          {can("RETURN") && <Button variant="secondary" onClick={() => setDecision("return")}>{t("req.action.return")}</Button>}
          {can("REJECT") && <Button variant="danger" onClick={() => setDecision("reject")}>{t("req.action.reject")}</Button>}
        </>}
      />

      <div className="space-y-6">
        <div className="grid gap-6 lg:grid-cols-2">
          <Card title={t("req.section.info")}>
            <dl>
              <Info label={t("req.field.type")}>{label("RequestType", r.requestType)}</Info>
              <Info label={t("req.field.center")}>{r.centerNameAr}</Info>
              <Info label={t("req.field.association")}>{r.associationNameAr}</Info>
              {r.targetFiliereId && <Info label={t("req.field.target")}><Link href={`/filieres/${r.targetFiliereId}`} className="text-brand-700 hover:underline">{r.targetFiliereNameAr}</Link></Info>}
              <Info label={t("req.field.nameFr")}><span className="ltr-field inline-block">{r.proposedNameFr}</span></Info>
              <Info label={t("req.field.duration")}>{r.durationMonths != null ? num(r.durationMonths) : ""}</Info>
              <Info label={t("req.field.expected")}>{r.expectedBeneficiaries != null ? num(r.expectedBeneficiaries) : ""}</Info>
              {r.modificationSummary && <Info label={t("req.field.summary")}>{r.modificationSummary}</Info>}
              {r.resultingFiliereId && <Info label={t("req.resulting")}><Link href={`/filieres/${r.resultingFiliereId}`} className="text-brand-700 hover:underline">#{r.resultingFiliereId}</Link></Info>}
            </dl>
          </Card>
          <Card title={t("req.section.decisions")}>
            <dl>
              <Info label={t("req.regional")}>{r.regionalDecisionAt ? `${date(r.regionalDecisionAt)}${r.regionalComment ? ` — ${r.regionalComment}` : ""}` : ""}</Info>
              <Info label={t("req.national")}>{r.nationalDecisionAt ? `${date(r.nationalDecisionAt)}${r.nationalComment ? ` — ${r.nationalComment}` : ""}` : ""}</Info>
            </dl>
            <h3 className="mb-2 mt-5 text-sm font-semibold">{t("req.section.history")}</h3>
            <ol className="space-y-2 text-sm">
              {r.history.map((h, i) => (
                <li key={i} className="rounded-lg bg-surface px-3 py-2">
                  <div className="flex flex-wrap items-center gap-2"><RequestStatusBadge status={h.toStatus} /><span className="text-xs text-muted">{h.actor} · {dateTime(h.occurredAt)}</span></div>
                  {h.comment && <p className="mt-1 text-muted">{h.comment}</p>}
                </li>
              ))}
            </ol>
          </Card>
        </div>

        <Card title={`${t("req.section.documents")} — ${t("req.doc.progress", { n: uploaded, total: r.documents.length })}`}>
          <p className="mb-4 text-xs text-muted">{t("req.section.documents.hint")}</p>
          <ul className="space-y-3">
            {r.documents.map((slot) => (
              <DocumentRow key={slot.typeCode} requestId={r.id} slot={slot} editable={editable} onChange={refresh} onDelete={() => setConfirm(slot)} />
            ))}
          </ul>
        </Card>

        {history
          ? <AuditHistory path={`/filiere-requests/${r.id}/history`} title={t("ben.section.history")} />
          : <Button variant="secondary" onClick={() => setHistory(true)}>{t("ben.section.history")}</Button>}
      </div>

      <DecisionModal request={r} decision={decision} onClose={() => setDecision(null)} onDone={(d) => { refresh(); toast.success(t(`req.done.${d}` as MessageKey)); setDecision(null); }} />
      <ConfirmDialog open={confirm === "delete"} danger loading={remove.isPending} title={t("req.action.delete")} message={t("req.delete.confirm", { name: r.proposedNameAr })}
        onClose={() => setConfirm(null)} onConfirm={() => remove.mutate()} />
      <ConfirmDialog open={confirm === "submit"} loading={simple.isPending} title={t("req.action.submit")} message={`${t("req.doc.progress", { n: uploaded, total: r.documents.length })}`}
        onClose={() => setConfirm(null)} onConfirm={() => simple.mutate("submit")} />
      <ConfirmDialog open={confirm === "withdraw"} loading={simple.isPending} title={t("req.action.withdraw")} message={r.requestNumber}
        onClose={() => setConfirm(null)} onConfirm={() => simple.mutate("withdraw")} />
      <ConfirmDialog open={!!confirm && typeof confirm === "object"} danger loading={removeDoc.isPending} title={t("common.delete")} message={t("req.doc.delete.confirm")}
        onClose={() => setConfirm(null)} onConfirm={() => typeof confirm === "object" && confirm && removeDoc.mutate(confirm)} />
    </>
  );
}

function DocumentRow({ requestId, slot, editable, onChange, onDelete }: {
  requestId: number; slot: RequestDocumentSlot; editable: boolean; onChange: () => void; onDelete: () => void;
}) {
  const { t, locale } = useI18n();
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const d = slot.document;
  const typeName = locale === "ar" ? slot.typeNameAr : t(`doc.${slot.typeCode}` as MessageKey);

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
    <li className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-line px-4 py-3">
      <div className="flex min-w-0 flex-1 items-start gap-3">
        <CheckCircle2 className={`mt-0.5 size-5 shrink-0 ${d ? "text-ok" : "text-line"}`} aria-hidden />
        <div className="min-w-0">
          <div className="text-sm font-medium">{slot.position}. {typeName}</div>
          <div className="text-xs text-muted">{d ? `${d.originalFilename} · ${(d.sizeBytes / 1024 / 1024).toFixed(2)} MB · ${d.uploadedBy}` : t("req.doc.missing")}</div>
        </div>
      </div>
      <div className="flex gap-2">
        {d && <Button variant="ghost" icon={<Download className="size-4" />}
          onClick={() => download(`/filiere-requests/${requestId}/documents/${d.id}`, undefined, d.originalFilename).catch((e) => toast.error(errorText(e, t)))}>{t("req.doc.download")}</Button>}
        {editable && (
          <>
            <input ref={input} type="file" accept="application/pdf,.pdf" className="hidden" aria-label={typeName}
              onChange={(e) => { pick(e.target.files?.[0]); e.target.value = ""; }} />
            <Button variant="secondary" icon={<FileUp className="size-4" />} loading={upload.isPending} onClick={() => input.current?.click()}>{t(d ? "req.doc.replace" : "req.doc.upload")}</Button>
            {d && <Button variant="ghost" className="px-2 text-danger" aria-label={t("common.delete")} onClick={onDelete}><Trash2 className="size-4" /></Button>}
          </>
        )}
      </div>
    </li>
  );
}

function DecisionModal({ request, decision, onClose, onDone }: { request: RequestDetail; decision: Decision | null; onClose: () => void; onDone: (d: Decision) => void }) {
  const { t } = useI18n();
  return (
    <Modal open={!!decision} onClose={onClose} title={decision ? t(`req.action.${decision}` as MessageKey) : ""}>
      {decision && <DecisionForm request={request} decision={decision} onClose={onClose} onDone={onDone} />}
    </Modal>
  );
}

function DecisionForm({ request, decision, onClose, onDone }: { request: RequestDetail; decision: Decision; onClose: () => void; onDone: (d: Decision) => void }) {
  const { t } = useI18n();
  const [comment, setComment] = useState("");
  const [code, setCode] = useState("");
  const [nameFr, setNameFr] = useState("");
  const [error, setError] = useState<string>();
  const needsComment = COMMENT_REQUIRED[decision];
  const submit = useMutation({
    mutationFn: () => api(`/filiere-requests/${request.id}/${PATHS[decision]}`, {
      method: "POST",
      body: decision === "approve" ? { comment: comment || undefined, code: code || undefined, nameFr: nameFr || undefined } : { comment: comment || undefined },
    }),
    onSuccess: () => onDone(decision),
    onError: (e) => setError(errorText(e, t)),
  });
  return (
    <div className="space-y-4">
      {decision === "approve" && request.requestType === "CREATE" && (
        <>
          <p className="rounded-lg bg-brand-50 px-3 py-2 text-sm text-brand-700">{t("req.action.approveHint")}</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("req.action.code")} hint={t("req.action.codeHint")}><Input className="ltr-field uppercase" value={code} onChange={(e) => setCode(e.target.value)} /></Field>
            <Field label={t("req.action.nameFr")}><Input className="ltr-field" value={nameFr} onChange={(e) => setNameFr(e.target.value)} /></Field>
          </div>
        </>
      )}
      <Field label={t("req.action.comment")} required={needsComment}><Textarea value={comment} onChange={(e) => setComment(e.target.value)} /></Field>
      {error && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{error}</p>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button variant={decision === "reject" ? "danger" : "primary"} loading={submit.isPending}
          disabled={needsComment && !comment.trim()} onClick={() => { setError(undefined); submit.mutate(); }}>{t("common.confirm")}</Button>
      </div>
    </div>
  );
}
