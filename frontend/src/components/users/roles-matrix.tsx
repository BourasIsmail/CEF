"use client";

import { useI18n } from "@/lib/i18n";

/** Matrice des profils et permissions (maquette « Rôles et permissions »). */
export function RolesMatrix() {
  const { t } = useI18n();
  const rows: [string, string, string, string][] = [
    [t("roles.r1"), t("roles.r1.p"), t("roles.r1.r"), t("roles.r1.a")],
    [t("roles.r2"), t("roles.edit"), t("roles.view"), t("roles.full")],
    [t("roles.r3"), t("roles.edit"), t("roles.view"), t("roles.full")],
    [t("roles.r4"), t("roles.edit"), t("roles.view"), t("roles.full")],
    [t("roles.r5"), t("roles.r5.pr"), t("roles.r5.pr"), t("roles.full")],
    [t("roles.r6"), t("roles.r6.p"), t("roles.r6.r"), t("roles.r6.a")],
    [t("roles.r7"), t("roles.r7.p"), t("roles.r7.r"), t("roles.r1.a")],
    [t("roles.r8"), t("roles.no"), t("roles.no"), t("roles.full")],
    [t("roles.r9"), t("roles.no"), t("roles.no"), t("roles.show")],
  ];
  const th = "border border-[#5b84bb] bg-[#0c4797] px-3 py-3.5 text-center font-semibold text-white";
  return (
    <section className="mb-6 space-y-4 rounded-xl border border-line bg-white p-5">
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr>
              <th className={th}>{t("roles.col.function")}</th>
              <th className={th}>{t("roles.col.provincial")}</th>
              <th className={th}>{t("roles.col.regional")}</th>
              <th className={th}>{t("roles.col.admin")}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r[0]}>
                <td className="border border-line bg-[#f4f5f7] p-3 text-start font-semibold leading-relaxed">{r[0]}</td>
                {r.slice(1).map((c, i) => <td key={i} className="border border-line p-3 text-center leading-relaxed">{c}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="rounded-lg bg-[#f4f5f7] px-4 py-3 text-[13px] leading-relaxed text-muted">{t("roles.future")}</p>
    </section>
  );
}
