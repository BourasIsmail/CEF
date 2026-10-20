"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { AuditHistory } from "@/components/audit/history";
import { mo } from "@/components/requests/document-checklist";
import { RequestPath, RequestStatusBadge } from "@/components/requests/request-badge";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm";
import { Input, Textarea } from "@/components/ui/form";
import { ErrorState, Spinner } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api, download } from "@/lib/api";
import { errorText } from "@/lib/errors";
import { useI18n, type MessageKey } from "@/lib/i18n";
import type { RequestDetail } from "@/lib/types";

type Decision = "validate" | "approve" | "return" | "reject";
const PATHS: Record<Decision, string> = { validate: "regional-validation", approve: "approval", return: "return", reject: "rejection" };
const COMMENT_REQUIRED: Record<Decision, boolean> = { validate: false, approve: false, return: true, reject: true };
type StepState = "done" | "cur" | "todo" | "bad";

/** État des 4 étapes du parcours selon le statut (et l'instance qui a rejeté). */
function steps(r: RequestDetail): StepState[] {
  switch (r.status) {
    case "DRAFT": case "RETURNED": return ["cur", "todo", "todo", "todo"];
    case "SUBMITTED": return ["done", "cur", "todo", "todo"];
    case "REGIONAL_VALIDATED": return ["done", "done", "cur", "todo"];
    case "APPROVED": return ["done", "done", "done", "done"];
    case "REJECTED": return r.nationalDecisionAt ? ["done", "done", "bad", "todo"] : ["done", "bad", "todo", "todo"];
  }
}

function Panel({ title, children, className = "border-line" }: { title: string; children: React.ReactNode; className?: string }) {
  return (
    <section className={`flex flex-col gap-3.5 rounded-xl border bg-white p-6 ${className}`}>
      <h2 className="text-[19px] font-semibold">{title}</h2>
      {children}
    </section>
  );
}

export default function RequestDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();
  const { t, date, dateTime, num } = useI18n();
  const [confirm, setConfirm] = useState<"delete" | "submit" | "withdraw" | null>(null);
  const [history, setHistory] = useState(false);
  const [comment, setComment] = useState("");
  const [code, setCode] = useState("");
  const [nameFr, setNameFr] = useState("");

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
  const decide = useMutation({
    mutationFn: (d: Decision) => api(`/filiere-requests/${id}/${PATHS[d]}`, {
      method: "POST",
      body: d === "approve" ? { comment: comment || undefined, code: code || undefined, nameFr: nameFr || undefined } : { comment: comment || undefined },
    }).then(() => d),
    onSuccess: (d) => { refresh(); setComment(""); toast.success(t(`req.done.${d}` as MessageKey)); },
    onError: (e) => toast.error(errorText(e, t)),
  });

  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  const r = q.data;
  const can = (a: string) => r.availableActions.includes(a);
  const uploaded = r.documents.filter((d) => d.document).length;
  const decisions = (["validate", "approve", "return", "reject"] as const).filter((d) => can(d.toUpperCase()));
  const regionalStage = can("VALIDATE");
  const titleKey = r.requestType === "CREATE" ? "req.detail.createTitle" : "req.detail.modifyTitle";
  const filiereName = r.proposedNameAr || r.targetFiliereNameAr || "";
  const info: [string, React.ReactNode][] = [
    [t("req.field.type"), t(r.requestType === "CREATE" ? "req.info.type.create" : "req.info.type.modify")],
    [t("req.info.name"), [r.proposedNameAr || r.targetFiliereNameAr, r.proposedNameFr].filter(Boolean).join(" · ")],
    [t("req.field.center"), r.centerNameAr],
    [t("req.field.association"), r.associationNameAr],
    [t("req.info.duration"), r.durationMonths != null ? t("req.info.months", { n: num(r.durationMonths) }) : ""],
    [t("req.field.expected"), r.expectedBeneficiaries != null ? num(r.expectedBeneficiaries) : ""],
  ];
  if (r.targetFiliereId) info.splice(2, 0, [t("req.field.target"), <Link key="t" href={`/filieres/${r.targetFiliereId}`} className="text-brand-700 hover:underline">{r.targetFiliereNameAr}</Link>]);
  if (r.modificationSummary) info.push([t("req.field.summary"), r.modificationSummary]);
  if (r.resultingFiliereId) info.push([t("req.resulting"), <Link key="r" href={`/filieres/${r.resultingFiliereId}`} className="text-brand-700 hover:underline">#{r.resultingFiliereId}</Link>]);

  const stepLabels = steps(r);
  const created = r.history.find((h) => h.toStatus === "DRAFT");

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <Link href="/requests" className="text-sm font-semibold text-brand-700 hover:underline">› {t("req.title")}</Link>
          <h1 className="mt-1.5 text-[28px] font-bold">{t(titleKey, { no: r.requestNumber, name: filiereName })}</h1>
          <p className="mt-1 text-sm text-muted">
            {t("req.detail.subtitle", { center: r.centerNameAr, province: r.provinceNameAr, date: date(r.submittedAt ?? created?.occurredAt ?? r.createdAt) })}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <RequestStatusBadge status={r.status} large />
          {can("EDIT") && <Button variant="secondary" onClick={() => router.push(`/requests/${r.id}/edit`)}>{t("common.edit")}</Button>}
          {can("DELETE") && <Button variant="danger" onClick={() => setConfirm("delete")}>{t("req.action.delete")}</Button>}
          {can("WITHDRAW") && <Button variant="secondary" onClick={() => setConfirm("withdraw")}>{t("req.action.withdraw")}</Button>}
          {can("SUBMIT") && <Button onClick={() => setConfirm("submit")}>{t("req.form.submit")}</Button>}
        </div>
      </div>

      <section className="rounded-xl border border-line bg-white px-6 py-5">
        <RequestPath state={stepLabels} />
        <ol className="sr-only">{stepLabels.map((s, i) => <li key={i}>{t(`req.step.${i + 1}.who` as MessageKey)}: {s}</li>)}</ol>
      </section>

      <div className="flex flex-wrap items-start gap-4">
        <div className="flex min-w-0 flex-[3_1_480px] flex-col gap-4">
          <Panel title={t("req.section.info")}>
            <div className="flex flex-wrap gap-3">
              {info.map(([k, v]) => (
                <div key={k} className="min-w-[200px] flex-[1_1_200px] rounded-lg bg-surface px-3.5 py-3">
                  <div className="text-xs text-muted">{k}</div>
                  <div className="mt-0.5 text-[15px] font-semibold">{v || "—"}</div>
                </div>
              ))}
            </div>
          </Panel>

          <Panel title={`${t("req.section.documents")} (${num(uploaded)} / ${num(r.documents.length)})`}>
            {r.documents.map((slot) => {
              const d = slot.document;
              return (
                <div key={slot.typeCode} className="flex flex-wrap items-center justify-between gap-2.5 rounded-[10px] border border-line px-3.5 py-3">
                  <span className="min-w-0 flex-[1_1_360px] text-sm leading-7">{slot.position}. {t(`doc.${slot.typeCode}` as MessageKey)}</span>
                  <span className="flex items-center gap-2.5 text-[13px] text-muted">
                    {d ? mo(d.sizeBytes) : t("req.doc.tagMissing")}
                    {d && (
                      <button type="button" className="min-h-11 rounded-lg border border-brand-600 bg-white px-4 text-sm font-semibold text-brand-700 hover:bg-brand-50"
                        onClick={() => download(`/filiere-requests/${r.id}/documents/${d.id}`, undefined, d.originalFilename).catch((e) => toast.error(errorText(e, t)))}>
                        {t("req.doc.preview")}
                      </button>
                    )}
                  </span>
                </div>
              );
            })}
          </Panel>
        </div>

        <div className="flex min-w-0 flex-[2_1_320px] flex-col gap-4">
          {decisions.length > 0 && (
            <Panel title={t(regionalStage ? "req.decision.regional" : "req.decision.national")} className="border-2 border-brand-600">
              {can("APPROVE") && r.requestType === "CREATE" && (
                <div className="grid gap-3">
                  <p className="m-0 rounded-lg bg-brand-50 px-3 py-2 text-sm text-brand-700">{t("req.action.approveHint")}</p>
                  <label className="flex flex-col gap-1.5 text-[13px] font-medium"><span>{t("req.action.code")}</span>
                    <Input className="ltr-field uppercase" value={code} onChange={(e) => setCode(e.target.value)} /></label>
                  <label className="flex flex-col gap-1.5 text-[13px] font-medium"><span>{t("req.action.nameFr")}</span>
                    <Input className="ltr-field" value={nameFr} onChange={(e) => setNameFr(e.target.value)} /></label>
                </div>
              )}
              <label className="flex flex-col gap-1.5 text-[13px] font-medium">
                <span>{t("req.decision.comment")}</span>
                <Textarea rows={4} value={comment} onChange={(e) => setComment(e.target.value)} />
              </label>
              {decisions.map((d) => (
                <button key={d} type="button" disabled={decide.isPending || (COMMENT_REQUIRED[d] && !comment.trim())} onClick={() => decide.mutate(d)}
                  className={`min-h-12 rounded-lg border text-[15px] font-semibold disabled:opacity-50 ${
                    d === "validate" || d === "approve" ? "border-transparent bg-brand-600 text-white hover:bg-brand-700"
                      : d === "return" ? "border-brand-600 bg-white text-brand-700 hover:bg-brand-50" : "border-[#A4332B] bg-white text-[#A4332B] hover:bg-danger-50"}`}>
                  {t(`req.decision.${d}` as MessageKey)}
                </button>
              ))}
              <p className="m-0 text-[13px] leading-7 text-muted">{t("req.decision.help")}</p>
            </Panel>
          )}

          <Panel title={t("req.history.title")}>
            {r.history.slice().reverse().map((h, i) => (
              <div key={i} className="flex items-start gap-3 text-sm leading-7">
                <span className="mt-2.5 size-2.5 shrink-0 rounded-full bg-brand-600" />
                <div>
                  <strong>{t(`req.hist.${h.toStatus}` as MessageKey)}</strong>
                  {h.comment && <p className="m-0 text-muted">{h.comment}</p>}
                  <span className="text-muted">{h.actor} · {dateTime(h.occurredAt)}</span>
                </div>
              </div>
            ))}
          </Panel>
        </div>
      </div>

      {history
        ? <AuditHistory path={`/filiere-requests/${r.id}/history`} title={t("ben.section.history")} />
        : <div><Button variant="secondary" onClick={() => setHistory(true)}>{t("ben.section.history")}</Button></div>}

      <ConfirmDialog open={confirm === "delete"} danger loading={remove.isPending} title={t("req.action.delete")} message={t("req.delete.confirm", { name: filiereName })}
        onClose={() => setConfirm(null)} onConfirm={() => remove.mutate()} />
      <ConfirmDialog open={confirm === "submit"} loading={simple.isPending} title={t("req.form.submit")} message={t("req.doc.progress", { n: uploaded, total: r.documents.length })}
        onClose={() => setConfirm(null)} onConfirm={() => simple.mutate("submit")} />
      <ConfirmDialog open={confirm === "withdraw"} loading={simple.isPending} title={t("req.action.withdraw")} message={r.requestNumber}
        onClose={() => setConfirm(null)} onConfirm={() => simple.mutate("withdraw")} />
    </div>
  );
}
