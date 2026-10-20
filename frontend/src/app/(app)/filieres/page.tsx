"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { FiliereEditor } from "@/components/filieres/filiere-panel";
import { FiliereFormModal } from "@/components/filieres/filiere-form-modal";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/card";
import { ErrorState, Spinner } from "@/components/ui/states";
import { api } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import type { FiliereSummary, PageResponse } from "@/lib/types";

export default function FilieresPage() {
  return (
    <Suspense fallback={<Spinner />}>
      <Workspace />
    </Suspense>
  );
}

/** Écran maître / détail de la maquette : liste des filières à gauche, données, modules et centres à droite. */
function Workspace() {
  const me = useMe();
  const router = useRouter();
  const params = useSearchParams();
  const { t, name, num } = useI18n();
  const admin = can.admin(me);
  const [creating, setCreating] = useState(false);

  const list = useQuery({
    queryKey: ["filieres", "list", "all"],
    queryFn: () => api<PageResponse<FiliereSummary>>("/filieres", { query: { size: 100, sort: "code,asc" } }).then((p) => p.content),
  });

  if (list.isPending) return <Spinner />;
  if (list.isError) return <ErrorState error={list.error} />;

  const filieres = list.data;
  const wanted = Number(params.get("id"));
  const selected = filieres.find((f) => f.id === wanted) ?? filieres[0];
  const programs = new Set(filieres.map((f) => f.programCode));

  return (
    <>
      <PageHeader title={t("filieres.title")} subtitle={t("filieres.subtitle.program", { program: [...programs].join(" / ") || "CEF" })} />
      <div className="flex flex-wrap items-start gap-4">
        <section className="flex min-w-0 flex-[1_1_17.5rem] flex-col gap-2.5 rounded-xl border border-line bg-white p-4">
          <div className="flex items-center justify-between gap-2 px-1">
            <h2 className="text-[17px] font-bold">{t("filieres.list")}</h2>
            {admin && <Button variant="secondary" onClick={() => setCreating(true)}>+ {t("filieres.add")}</Button>}
          </div>
          {filieres.map((f) => {
            const on = f.id === selected?.id;
            return (
              <Link key={f.id} href={`/filieres?id=${f.id}`} scroll={false}
                className={`flex flex-col gap-1 rounded-[10px] border p-3.5 ${on ? "border-brand-600 bg-brand-50" : "border-line bg-white hover:bg-brand-50/50"} ${f.active ? "" : "opacity-60"}`}>
                <span className="text-[15px] font-semibold">{name(f)}</span>
                <span className="text-[13px] text-muted">
                  {t("filieres.meta", { months: t("filieres.months", { n: f.durationMonths }), modules: num(f.modulesCount), centers: num(f.centersCount) })}
                </span>
              </Link>
            );
          })}
          {filieres.length === 0 && <p className="px-1 text-sm text-muted">{t("filieres.pick")}</p>}
        </section>

        <div className="min-w-0 flex-[3_1_32.5rem]">
          {selected ? <FiliereEditor id={selected.id} admin={admin} /> : null}
        </div>
      </div>
      <FiliereFormModal filiere={null} open={creating} onClose={() => setCreating(false)} onSaved={(id) => router.push(`/filieres?id=${id}`)} />
    </>
  );
}
