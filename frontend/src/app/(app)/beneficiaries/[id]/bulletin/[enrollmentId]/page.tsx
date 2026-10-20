"use client";

import { useQuery } from "@tanstack/react-query";
import { Printer } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ResultBadge } from "@/components/beneficiaries/status-badge";
import { Button } from "@/components/ui/button";
import { ErrorState, Spinner } from "@/components/ui/states";
import { api } from "@/lib/api";
import { useI18n } from "@/lib/i18n";
import type { Bulletin, SessionView } from "@/lib/types";

/** بيان النقط : version imprimable (le menu et les boutons sont masqués à l'impression). */
export default function BulletinPage() {
  const { id, enrollmentId } = useParams<{ id: string; enrollmentId: string }>();
  const { t, name, label, num, date } = useI18n();
  const q = useQuery({ queryKey: ["bulletin", enrollmentId], queryFn: () => api<Bulletin>(`/enrollments/${enrollmentId}/bulletin`) });

  if (q.isPending) return <Spinner />;
  if (q.isError) return <ErrorState error={q.error} />;
  const b = q.data;
  const sessions: SessionView[] = [...b.sessions].sort((x) => (x.type === "NORMAL" ? -1 : 1));
  const grade = (s: SessionView, moduleId: number) => {
    const g = s.grades.find((x) => x.moduleId === moduleId);
    return g?.status === "ABSENT" ? label("ExamStatus", "ABSENT") : g?.grade != null ? num(g.grade, 2) : "—";
  };

  return (
    <div className="bulletin">
      <div className="mb-4 flex items-center justify-between print:hidden">
        <Link href={`/beneficiaries/${id}`} className="text-sm text-brand-700 hover:underline">← {t("common.back")}</Link>
        <Button icon={<Printer className="size-4" />} onClick={() => window.print()}>{t("ben.exam.print")}</Button>
      </div>

      <article className="mx-auto max-w-4xl rounded-xl border border-line bg-white p-8 print:max-w-none print:border-0 print:p-0">
        <header className="mb-6 border-b border-line pb-4 text-center">
          <p className="text-sm text-muted">{t("app.org")}</p>
          <h1 className="mt-1 text-xl font-bold">{t("ben.exam.bulletin")}</h1>
          <p className="text-sm text-muted">{t("app.name")}</p>
        </header>

        <dl className="mb-6 grid gap-x-8 gap-y-2 text-sm sm:grid-cols-2">
          <div className="flex justify-between"><dt className="text-muted">{t("ben.col.name")}</dt><dd className="font-semibold">{b.beneficiaryNameAr}</dd></div>
          <div className="flex justify-between"><dt className="text-muted">{t("ben.col.reg")}</dt><dd className="ltr-field font-semibold">{b.registrationNumber}</dd></div>
          <div className="flex justify-between"><dt className="text-muted">{t("ben.col.center")}</dt><dd className="font-semibold">{b.centerNameAr}</dd></div>
          <div className="flex justify-between"><dt className="text-muted">{t("ben.col.filiere")}</dt><dd className="font-semibold">{b.filiereNameAr}</dd></div>
          <div className="flex justify-between"><dt className="text-muted">{t("ben.filter.season")}</dt><dd className="font-semibold">{b.seasonLabel}</dd></div>
          <div className="flex justify-between"><dt className="text-muted">{t("ben.enroll.exam")}</dt><dd className="font-semibold">{b.examDate ? date(b.examDate) : "—"}</dd></div>
        </dl>

        {sessions.length === 0 ? <p className="text-center text-muted">{t("ben.exam.noSession")}</p> : (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="bg-surface">
                <th className="border border-line px-3 py-2 text-start">{t("ben.exam.module")}</th>
                <th className="border border-line px-3 py-2 text-center">{t("ben.exam.coefficient")}</th>
                {sessions.map((s) => <th key={s.type} className="border border-line px-3 py-2 text-center">{label("SessionType", s.type)}</th>)}
              </tr>
            </thead>
            <tbody>
              {b.modules.map((m) => (
                <tr key={m.id}>
                  <td className="border border-line px-3 py-2">{name(m)}</td>
                  <td className="border border-line px-3 py-2 text-center tabular-nums">{num(m.coefficient, 2)}</td>
                  {sessions.map((s) => <td key={s.type} className="border border-line px-3 py-2 text-center tabular-nums">{grade(s, m.id)}</td>)}
                </tr>
              ))}
              <tr className="font-semibold">
                <td className="border border-line px-3 py-2" colSpan={2}>{t("ben.exam.modulesAverage")}</td>
                {sessions.map((s) => <td key={s.type} className="border border-line px-3 py-2 text-center tabular-nums">{s.modulesAverage != null ? num(s.modulesAverage, 2) : "—"}</td>)}
              </tr>
              <tr className="font-semibold">
                <td className="border border-line px-3 py-2" colSpan={2}>{t("ben.exam.final")}</td>
                {sessions.map((s) => <td key={s.type} className="border border-line px-3 py-2 text-center tabular-nums">{s.finalExamStatus === "ABSENT" ? label("ExamStatus", "ABSENT") : s.finalExamGrade != null ? num(s.finalExamGrade, 2) : "—"}</td>)}
              </tr>
              <tr className="bg-brand-50 font-bold">
                <td className="border border-line px-3 py-2" colSpan={2}>{t("ben.exam.overall")}</td>
                {sessions.map((s) => <td key={s.type} className="border border-line px-3 py-2 text-center tabular-nums">{s.overallAverage != null ? num(s.overallAverage, 2) : "—"}</td>)}
              </tr>
              <tr>
                <td className="border border-line px-3 py-2" colSpan={2}>{t("ben.exam.result")}</td>
                {sessions.map((s) => (
                  <td key={s.type} className="border border-line px-3 py-2 text-center">
                    <ResultBadge result={s.result} />{s.exceptionalPass && <div className="mt-1 text-xs text-warn">{t("ben.exam.exceptional")}</div>}
                  </td>
                ))}
              </tr>
              <tr>
                <td className="border border-line px-3 py-2" colSpan={2}>{t("ben.exam.mention")}</td>
                {sessions.map((s) => <td key={s.type} className="border border-line px-3 py-2 text-center">{s.mention ?? "—"}</td>)}
              </tr>
            </tbody>
          </table>
        )}
        {b.diploma && <p className="mt-6 text-sm">{t("ben.diploma.title")} : <b className="ltr-field">{b.diploma.serialNumber}</b></p>}
      </article>
    </div>
  );
}
