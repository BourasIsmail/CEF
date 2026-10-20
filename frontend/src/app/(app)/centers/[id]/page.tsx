"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Power, Trash2, X } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Badge, Card, PageHeader } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm";
import { Select } from "@/components/ui/form";
import { ErrorState, Spinner } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { errorText } from "@/lib/errors";
import { useI18n } from "@/lib/i18n";
import { can } from "@/lib/permissions";
import { useFilieresOptions } from "@/lib/queries";
import type { CenterDetail, CenterFiliere } from "@/lib/types";

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 border-b border-line py-2 text-sm last:border-0">
      <dt className="text-muted">{label}</dt>
      <dd className="text-end font-medium">{children || "—"}</dd>
    </div>
  );
}

export default function CenterDetailPage() {
  const { id } = useParams<{ id: string }>();
  const me = useMe();
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();
  const { t, name, label, num, date } = useI18n();
  const [confirm, setConfirm] = useState<"delete" | "toggle" | CenterFiliere | null>(null);
  const [pick, setPick] = useState("");

  const center = useQuery({ queryKey: ["centers", id], queryFn: () => api<CenterDetail>(`/centers/${id}`) });
  const filieres = useQuery({ queryKey: ["centers", id, "filieres"], queryFn: () => api<CenterFiliere[]>(`/centers/${id}/filieres`) });
  const options = useFilieresOptions();
  const refresh = () => qc.invalidateQueries({ queryKey: ["centers"] });
  const fail = (e: unknown) => { setConfirm(null); toast.error(errorText(e, t)); };

  const toggle = useMutation({
    mutationFn: (value: boolean) => api(`/centers/${id}/active`, { method: "PATCH", query: { value } }),
    onSuccess: (_, v) => { refresh(); setConfirm(null); toast.success(t(v ? "centers.activated" : "centers.deactivated")); }, onError: fail,
  });
  const remove = useMutation({
    mutationFn: () => api(`/centers/${id}`, { method: "DELETE" }),
    onSuccess: () => { refresh(); toast.success(t("centers.deleted")); router.replace("/centers"); }, onError: fail,
  });
  const assign = useMutation({
    mutationFn: (filiereId: number) => api(`/centers/${id}/filieres/${filiereId}`, { method: "PUT" }),
    onSuccess: () => { refresh(); setPick(""); }, onError: (e) => toast.error(errorText(e, t)),
  });
  const unassign = useMutation({
    mutationFn: (filiereId: number) => api(`/centers/${id}/filieres/${filiereId}`, { method: "DELETE" }),
    onSuccess: () => { refresh(); setConfirm(null); }, onError: fail,
  });

  if (center.isPending) return <Spinner />;
  if (center.isError) return <ErrorState error={center.error} />;
  const c = center.data;
  const writer = can.write(me) && (me.role === "ADMIN" || me.provinceId === c.provincialDirectorateId);
  const assigned = new Set(filieres.data?.map((f) => f.filiereId));
  const available = options.data?.filter((f) => !assigned.has(f.id)) ?? [];

  return (
    <>
      <Link href="/centers" className="mb-2 inline-block text-sm text-brand-700 hover:underline">← {t("centers.title")}</Link>
      <PageHeader
        title={<span className="flex flex-wrap items-center gap-3">{name(c)} {!c.active && <Badge>{t("common.inactive")}</Badge>}</span>}
        subtitle={<><span className="ltr-field inline-block">{c.code}</span> · {c.provincialDirectorateNameAr} · {c.communeNameAr}</>}
        actions={writer && <>
          <Button variant="secondary" icon={<Pencil className="size-4" />} onClick={() => router.push(`/centers/${c.id}/edit`)}>{t("common.edit")}</Button>
          <Button variant="secondary" icon={<Power className="size-4" />} onClick={() => setConfirm("toggle")}>{t(c.active ? "centers.deactivate" : "centers.activate")}</Button>
          <Button variant="danger" icon={<Trash2 className="size-4" />} onClick={() => setConfirm("delete")}>{t("common.delete")}</Button>
        </>}
      />

      <div className="space-y-6">
        <div className="grid gap-4 sm:grid-cols-3">
          <Link href={`/beneficiaries?centerId=${c.id}`} className="rounded-xl border border-line bg-white p-5 hover:bg-brand-50/40">
            <div className="text-sm text-muted">{t("centers.col.beneficiaries")}</div>
            <div className="text-3xl font-bold text-brand-700">{num(c.beneficiariesCount)}</div>
          </Link>
          <Link href={`/employees?centerId=${c.id}`} className="rounded-xl border border-line bg-white p-5 hover:bg-brand-50/40">
            <div className="text-sm text-muted">{t("centers.hr.total")}</div>
            <div className="text-3xl font-bold text-brand-700">{num(c.staffCount)}</div>
          </Link>
          <div className="rounded-xl border border-line bg-white p-5">
            <div className="text-sm text-muted">{t("centers.col.filieres")}</div>
            <div className="text-3xl font-bold text-brand-700">{num(c.filieresCount)}</div>
          </div>
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          <Card title={t("centers.section.general")}>
            <dl>
              <Info label={t("centers.field.region")}>{c.regionalDirectorateNameAr}</Info>
              <Info label={t("centers.field.province")}>{c.provincialDirectorateNameAr}</Info>
              <Info label={t("centers.field.commune")}>{c.communeNameAr}</Info>
              <Info label={t("centers.field.area")}>{label("AreaType", c.areaType)}</Info>
              <Info label={t("centers.field.address")}>{c.address}</Info>
              <Info label={t("centers.field.land")}>{c.landStatus ? label("LandStatus", c.landStatus) : ""}</Info>
              {c.landOwnerText && <Info label={t("centers.field.landOwner")}>{c.landOwnerText}</Info>}
              <Info label={t("centers.field.management")}>{c.managementMode ? label("ManagementMode", c.managementMode) : ""}</Info>
            </dl>
          </Card>
          <Card title={t("centers.section.building")}>
            <dl>
              <Info label={t("centers.field.building")}>{c.buildingType ? label("BuildingType", c.buildingType) : ""}</Info>
              {c.floorAreas.map((f) => <Info key={f.floorNumber} label={t("centers.field.floorArea", { n: f.floorNumber })}>{num(f.areaM2, 2)}</Info>)}
              {c.totalAreaM2 != null && c.floorAreas.length > 0 && <Info label={t("centers.field.totalArea")}>{num(c.totalAreaM2, 2)} m²</Info>}
            </dl>
          </Card>
        </div>

        <Card title={`${t("centers.section.spaces")} (${t("centers.spacesCount", { n: c.spacesCount })})`}>
          {c.spaces.length === 0 ? <p className="text-sm text-muted">—</p> : (
            <ul className="flex flex-wrap gap-2">
              {c.spaces.map((s) => <li key={s.spaceTypeId}><Badge tone="brand">{name(s)}{s.specification ? ` : ${s.specification}` : ""}</Badge></li>)}
            </ul>
          )}
        </Card>

        <Card title={t("centers.section.associations")}>
          {c.associations.length === 0 ? <p className="text-sm text-muted">{t("centers.association.none")}</p> : (
            <div className="space-y-4">
              {c.associations.map((a) => (
                <dl key={a.id} className="rounded-lg border border-line px-4 py-2">
                  <Info label={t("centers.association.name")}>{a.associationNameAr}</Info>
                  <Info label={t("centers.association.subject")}>{a.agreementSubject}</Info>
                  <Info label={t("centers.association.date")}>{a.agreementDate ? date(a.agreementDate) : ""}</Info>
                  <Info label={t("centers.association.reference")}>{a.agreementReference}</Info>
                  <Info label={t("centers.association.evaluation")}>{a.partnershipEvaluation ? label("PartnershipEvaluation", a.partnershipEvaluation) : ""}</Info>
                  <Info label={t("centers.association.measure")}>{a.measureTaken ? `${label("PartnershipMeasure", a.measureTaken)}${a.measureOtherText ? ` : ${a.measureOtherText}` : ""}` : ""}</Info>
                  <Info label={t("centers.association.partners")}>{a.partners.map((p) => name(p)).join("، ")}</Info>
                </dl>
              ))}
            </div>
          )}
        </Card>

        <Card title={t("centers.section.filieres")}>
          {filieres.data?.length === 0 && <p className="mb-3 text-sm text-muted">{t("centers.filieres.none")}</p>}
          <ul className="mb-4 flex flex-wrap gap-2">
            {filieres.data?.map((f) => (
              <li key={f.filiereId} className="flex items-center gap-1 rounded-full border border-line bg-white py-1 ps-3 pe-1 text-sm">
                <Link href={`/filieres/${f.filiereId}`} className="hover:underline">{name(f)}</Link>
                {can.admin(me) && (
                  <button type="button" aria-label={t("common.delete")} onClick={() => setConfirm(f)} className="rounded-full p-1 text-muted hover:bg-danger-50 hover:text-danger"><X className="size-3.5" /></button>
                )}
              </li>
            ))}
          </ul>
          {can.admin(me) && (
            <div className="flex max-w-md gap-2">
              <Select value={pick} onChange={(e) => setPick(e.target.value)} aria-label={t("centers.filieres.assign")}>
                <option value="">{t("centers.filieres.pick")}</option>
                {available.map((f) => <option key={f.id} value={f.id}>{name(f)}</option>)}
              </Select>
              <Button icon={<Plus className="size-4" />} disabled={!pick} loading={assign.isPending} onClick={() => assign.mutate(Number(pick))}>{t("centers.filieres.assign")}</Button>
            </div>
          )}
        </Card>
      </div>

      <ConfirmDialog open={confirm === "delete"} danger loading={remove.isPending} title={t("common.delete")}
        message={t("centers.delete.confirm", { name: name(c) })} onClose={() => setConfirm(null)} onConfirm={() => remove.mutate()} />
      <ConfirmDialog open={confirm === "toggle"} loading={toggle.isPending} title={t(c.active ? "centers.deactivate" : "centers.activate")}
        message={name(c)} onClose={() => setConfirm(null)} onConfirm={() => toggle.mutate(!c.active)} />
      <ConfirmDialog open={!!confirm && typeof confirm === "object"} danger loading={unassign.isPending} title={t("common.delete")}
        message={t("centers.filieres.remove.confirm", { name: typeof confirm === "object" && confirm ? name(confirm) : "" })}
        onClose={() => setConfirm(null)} onConfirm={() => typeof confirm === "object" && confirm && unassign.mutate(confirm.filiereId)} />
    </>
  );
}
