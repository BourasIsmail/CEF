"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useFieldArray, useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Field, Input, Select } from "@/components/ui/form";
import { Modal } from "@/components/ui/modal";
import { OptionField } from "@/components/ui/option-group";
import { NumberedSection, SectionNav } from "@/components/ui/section";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n, type MessageKey } from "@/lib/i18n";
import { useAssociations, useCommunes, usePartnerTypes, useProvinces, useRegions, useSpaceTypes } from "@/lib/queries";
import type { AdministrativeStatus, BuildingType, CenterDetail, EmployeeType, PartnershipEvaluation, PartnershipMeasure, SpaceType, StaffSummary } from "@/lib/types";

interface AssociationValues {
  id?: number;
  associationId: string;
  agreementSubject: string;
  agreementDate: string;
  agreementReference: string;
  partnershipEvaluation: PartnershipEvaluation | "";
  measureTaken: PartnershipMeasure | "";
  measureOtherText: string;
  partnerTypeIds: string[];
}

interface Values {
  regionId: string;
  provincialDirectorateId: string;
  communeId: string;
  nameAr: string;
  areaType: "RURAL" | "URBAN";
  address: string;
  buildingType: BuildingType | "";
  floorsCount: string;
  floorAreas: string[];
  spaceIds: string[];
  spaceOther: string;
  landStatus: string;
  landOwnerText: string;
  managementMode: string;
  associations: AssociationValues[];
}

const FIXED_FLOORS: Record<BuildingType, number> = { ONE_FLOOR: 1, TWO_FLOORS: 2, THREE_FLOORS: 3, OTHER: 0 };
const BUILDINGS: BuildingType[] = ["ONE_FLOOR", "TWO_FLOORS", "THREE_FLOORS", "OTHER"];
const LANDS = ["OWNED_BY_EN", "RENTED_BY_EN", "PROVIDED_TO_EN", "ASSOCIATION", "OTHER_SECTOR"] as const;
const MODES = ["DIRECT_EN", "ASSOCIATION_WITH_EN_DIRECTOR", "ASSOCIATION_FULL"] as const;
const EVALS: PartnershipEvaluation[] = ["GOOD", "FAIR", "AVERAGE", "WEAK"];
const MEASURES: PartnershipMeasure[] = ["EXPAND", "CONTINUE", "TERMINATE", "OTHER"];

export function expectedFloors(type: BuildingType | "", count: string): number {
  if (!type) return 0;
  return type === "OTHER" ? Math.max(0, Math.min(20, Number(count) || 0)) : FIXED_FLOORS[type];
}

const blankAssociation = (): AssociationValues => ({
  associationId: "", agreementSubject: "", agreementDate: "", agreementReference: "", partnershipEvaluation: "", measureTaken: "", measureOtherText: "", partnerTypeIds: [],
});

function initialValues(center: CenterDetail | undefined, me: { regionId?: number; provinceId?: number }): Values {
  const floors = center?.floorAreas ?? [];
  const other = center?.spaces.find((s) => s.specification);
  const n = expectedFloors(center?.buildingType ?? "", String(center?.floorsCount ?? ""));
  return {
    regionId: String(center?.regionalDirectorateId ?? me.regionId ?? ""),
    provincialDirectorateId: String(center?.provincialDirectorateId ?? me.provinceId ?? ""),
    communeId: center ? String(center.communeId) : "", nameAr: center?.nameAr ?? "", areaType: center?.areaType ?? "URBAN", address: center?.address ?? "",
    buildingType: center?.buildingType ?? "", floorsCount: center?.floorsCount ? String(center.floorsCount) : "",
    floorAreas: Array.from({ length: n }, (_, i) => { const f = floors.find((x) => x.floorNumber === i + 1); return f ? String(f.areaM2) : ""; }),
    spaceIds: (center?.spaces ?? []).map((s) => String(s.spaceTypeId)), spaceOther: other?.specification ?? "",
    landStatus: center?.landStatus ?? "", landOwnerText: center?.landOwnerText ?? "", managementMode: center?.managementMode ?? "",
    associations: (center?.associations ?? []).map((a) => ({
      id: a.id, associationId: String(a.associationId), agreementSubject: a.agreementSubject ?? "", agreementDate: a.agreementDate ?? "",
      agreementReference: a.agreementReference ?? "", partnershipEvaluation: a.partnershipEvaluation ?? "", measureTaken: a.measureTaken ?? "",
      measureOtherText: a.measureOtherText ?? "", partnerTypeIds: a.partners.map((p) => String(p.id)),
    })),
  };
}

/** Formulaire d'un centre, dans l'ordre et avec les libellés des maquettes (sections c1 … c5 + ressources humaines). */
export function CenterForm({ center }: { center?: CenterDetail }) {
  const me = useMe();
  const spaceTypes = useSpaceTypes();
  if (!spaceTypes.data) return null;
  return <Form center={center} spaceTypes={spaceTypes.data} me={me} />;
}

function Form({ center, spaceTypes, me }: { center?: CenterDetail; spaceTypes: SpaceType[]; me: ReturnType<typeof useMe> }) {
  const { t, name, label, num } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const qc = useQueryClient();
  const admin = me.role === "ADMIN";
  const [formError, setFormError] = useState<string>();
  const [newAssociation, setNewAssociation] = useState(false);

  const { register, handleSubmit, control, setValue, setError, getValues, formState: { errors } } =
    useForm<Values>({ defaultValues: initialValues(center, me) });
  const assoc = useFieldArray({ control, name: "associations" });

  const regionId = useWatch({ control, name: "regionId" });
  const provinceId = useWatch({ control, name: "provincialDirectorateId" });
  const buildingType = useWatch({ control, name: "buildingType" });
  const floorsCount = useWatch({ control, name: "floorsCount" });
  const landStatus = useWatch({ control, name: "landStatus" });
  const spaceIds = useWatch({ control, name: "spaceIds" });
  const associations = useWatch({ control, name: "associations" });
  const floorAreas = useWatch({ control, name: "floorAreas" });

  const regions = useRegions();
  const provinces = useProvinces(regionId ? Number(regionId) : undefined);
  const communes = useCommunes(provinceId ? Number(provinceId) : undefined);
  const directory = useAssociations();
  const partnerTypes = usePartnerTypes();
  const staff = useQuery({
    queryKey: ["employees", "summary", center?.id], enabled: !!center,
    queryFn: () => api<StaffSummary>("/employees/summary", { query: { centerId: center!.id } }),
  });

  const floors = expectedFloors(buildingType, floorsCount);
  useEffect(() => {
    const current = getValues("floorAreas") ?? [];
    if (current.length !== floors) setValue("floorAreas", Array.from({ length: floors }, (_, i) => current[i] ?? ""));
  }, [floors, getValues, setValue]);

  const totalArea = (floorAreas ?? []).reduce((s, x) => s + (Number(x) || 0), 0);
  const needsOwner = landStatus === "PROVIDED_TO_EN" || landStatus === "OTHER_SECTOR";
  const otherSpace = spaceTypes.find((s) => s.requiresSpecification);
  const otherChecked = !!otherSpace && (spaceIds ?? []).includes(String(otherSpace.id));

  const save = useMutation({
    mutationFn: (v: Values) => {
      const body = {
        provincialDirectorateId: admin ? Number(v.provincialDirectorateId) : undefined,
        communeId: Number(v.communeId), nameAr: v.nameAr, nameFr: center?.nameFr, areaType: v.areaType, address: v.address || undefined,
        buildingType: v.buildingType || undefined,
        floorsCount: v.buildingType === "OTHER" ? Number(v.floorsCount) : undefined,
        floorAreas: v.buildingType ? v.floorAreas.map((a, i) => ({ floorNumber: i + 1, areaM2: Number(a) })) : [],
        landStatus: v.landStatus || undefined, landOwnerText: needsOwner ? v.landOwnerText : undefined, managementMode: v.managementMode || undefined,
        spaces: v.spaceIds.map((id) => ({ spaceTypeId: Number(id), specification: otherSpace && Number(id) === otherSpace.id ? v.spaceOther : undefined })),
        associations: v.associations.map((a) => ({
          id: a.id, associationId: Number(a.associationId),
          agreementSubject: a.agreementSubject || undefined, agreementDate: a.agreementDate || undefined, agreementReference: a.agreementReference || undefined,
          partnershipEvaluation: a.partnershipEvaluation || undefined, measureTaken: a.measureTaken || undefined,
          measureOtherText: a.measureTaken === "OTHER" ? a.measureOtherText : undefined, partnerTypeIds: a.partnerTypeIds.map(Number),
        })),
        active: center?.active ?? true,
      };
      return center
        ? api<CenterDetail>(`/centers/${center.id}`, { method: "PUT", body })
        : api<CenterDetail>("/centers", { method: "POST", body });
    },
    onSuccess: (c) => {
      qc.invalidateQueries({ queryKey: ["centers"] });
      toast.success(t(center ? "centers.updated" : "centers.created"));
      router.push(`/centers/${c.id}`);
    },
    onError: (e) => {
      if (!applyServerErrors(e, setError)) setFormError(errorText(e, t));
      window.scrollTo({ top: 0, behavior: "smooth" });
    },
  });

  const required = { required: t("common.required") };
  const regionLabel = center?.regionalDirectorateNameAr ?? me.regionNameAr;
  const provinceLabel = center?.provincialDirectorateNameAr ?? me.provinceNameAr;
  const ordinal = (n: number) => (n <= 10 ? t(`centers.ord.${n}` as MessageKey) : String(n));

  const sections = [
    { id: "c1", title: t("centers.section.general") }, { id: "c2", title: t("centers.section.building") }, { id: "c2b", title: t("centers.section.spaces") },
    { id: "c3", title: t("centers.section.land") }, { id: "c4", title: t("centers.section.management") }, { id: "c5", title: t("centers.section.associations") },
    { id: "c6", title: t("centers.hr.title") },
  ];

  return (
    <form onSubmit={handleSubmit((v) => { setFormError(undefined); save.mutate(v); })} noValidate className="space-y-6">
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-4 py-3 text-sm text-danger">{formError}</p>}
      <SectionNav sections={sections} />

      <NumberedSection id="c1" num={1} title={sections[0].title}>
        <OptionField control={control} name="areaType" legend={t("centers.field.area")}
          options={(["RURAL", "URBAN"] as const).map((a) => ({ value: a, label: label("AreaType", a) }))} />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {admin && !center ? (
            <>
              <Field label={t("centers.field.region")} required>
                <Select value={regionId} onChange={(e) => { setValue("regionId", e.target.value); setValue("provincialDirectorateId", ""); setValue("communeId", ""); }}>
                  <option value="">{t("common.select")}</option>
                  {regions.data?.map((r) => <option key={r.id} value={r.id}>{name(r)}</option>)}
                </Select>
              </Field>
              <Field label={t("centers.field.province")} required error={errors.provincialDirectorateId?.message}>
                <Select invalid={!!errors.provincialDirectorateId} {...register("provincialDirectorateId", { ...required, onChange: () => setValue("communeId", "") })}>
                  <option value="">{t("common.select")}</option>
                  {provinces.data?.map((p) => <option key={p.id} value={p.id}>{name(p)}</option>)}
                </Select>
              </Field>
            </>
          ) : (
            <>
              <Field label={t("centers.field.region")}><Input disabled readOnly value={regionLabel ?? ""} /></Field>
              <Field label={t("centers.field.province")} hint={center ? undefined : t("centers.field.provinceHint")}><Input disabled readOnly value={provinceLabel ?? ""} /></Field>
            </>
          )}
          <Field label={t("centers.field.name")} required error={errors.nameAr?.message} className="lg:col-span-2"><Input invalid={!!errors.nameAr} {...register("nameAr", required)} /></Field>
          <Field label={t("centers.field.commune")} required error={errors.communeId?.message}>
            <Select invalid={!!errors.communeId} {...register("communeId", required)}>
              <option value="">{t("common.select")}</option>
              {communes.data?.map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
            </Select>
          </Field>
          <Field label={t("centers.field.address")} className="lg:col-span-3"><Input {...register("address")} /></Field>
        </div>
      </NumberedSection>

      <NumberedSection id="c2" num={2} title={sections[1].title}>
        <OptionField control={control} name="buildingType" legend={t("centers.field.buildingLegend")}
          options={BUILDINGS.map((b) => ({ value: b, label: t(`centers.opt.building.${b}` as MessageKey) }))} />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label={t("centers.field.floorsOther")} required={buildingType === "OTHER"} error={errors.floorsCount?.message}>
            <Input type="number" min={1} max={20} disabled={buildingType !== "OTHER"} invalid={!!errors.floorsCount}
              {...register("floorsCount", { validate: (x) => buildingType !== "OTHER" || !!x || t("common.required") })} />
          </Field>
          {Array.from({ length: floors }, (_, i) => (
            <Field key={i} label={t("centers.field.floorAreaOrd", { ord: ordinal(i + 1) })} required error={errors.floorAreas?.[i]?.message}>
              <Input type="number" step="0.01" min="0.01" invalid={!!errors.floorAreas?.[i]} {...register(`floorAreas.${i}` as const, { required: t("common.required") })} />
            </Field>
          ))}
          <Field label={t("centers.field.totalAreaLong")}><Input disabled readOnly value={totalArea > 0 ? num(totalArea, 2) : ""} /></Field>
        </div>
      </NumberedSection>

      <NumberedSection id="c2b" num={3} title={sections[2].title}>
        <OptionField control={control} name="spaceIds" multiple legend={t("centers.field.spacesLegend")}
          options={spaceTypes.map((s) => ({ value: String(s.id), label: name(s) }))} />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label={t("centers.field.spaceOther")} required={otherChecked} error={errors.spaceOther?.message} className="lg:col-span-2">
            <Input disabled={!otherChecked} invalid={!!errors.spaceOther} {...register("spaceOther", { validate: (x) => !otherChecked || !!x.trim() || t("common.required") })} />
          </Field>
          <Field label={t("centers.field.spacesCount")}><Input disabled readOnly value={num((spaceIds ?? []).length)} /></Field>
        </div>
      </NumberedSection>

      <NumberedSection id="c3" num={4} title={sections[3].title}>
        <OptionField control={control} name="landStatus" legend={t("centers.field.landLegend")}
          options={LANDS.map((l) => ({ value: l, label: t(`centers.opt.land.${l}` as MessageKey) }))} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("centers.field.landOwnerLong")} required={needsOwner} error={errors.landOwnerText?.message}>
            <Input disabled={!needsOwner} invalid={!!errors.landOwnerText} {...register("landOwnerText", { validate: (x) => !needsOwner || !!x?.trim() || t("common.required") })} />
          </Field>
        </div>
      </NumberedSection>

      <NumberedSection id="c4" num={5} title={sections[4].title}>
        <OptionField control={control} name="managementMode" legend={t("centers.field.mgmtLegend")}
          options={MODES.map((m) => ({ value: m, label: t(`centers.opt.mgmt.${m}` as MessageKey) }))} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("centers.field.associationsCount")}><Input disabled readOnly value={num(assoc.fields.length)} /></Field>
        </div>
      </NumberedSection>

      <div id="c5" className="scroll-mt-20 space-y-6">
        {assoc.fields.length === 0 && (
          <NumberedSection id="c5-none" num={6} title={sections[5].title}><p className="text-sm text-muted">{t("centers.association.none")}</p></NumberedSection>
        )}
        {assoc.fields.map((f, i) => (
          <NumberedSection key={f.id} id={`c5-${i}`} num={6 + i} title={t("centers.association.title", { n: i + 1 })}>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Field label={t("centers.association.name")} required error={errors.associations?.[i]?.associationId?.message} className="sm:col-span-2">
                <Select invalid={!!errors.associations?.[i]?.associationId} {...register(`associations.${i}.associationId` as const, required)}>
                  <option value="">{t("common.select")}</option>
                  {directory.data?.map((a) => <option key={a.id} value={a.id}>{name(a)}</option>)}
                </Select>
              </Field>
              <Field label={t("centers.association.subject")} className="sm:col-span-2"><Input {...register(`associations.${i}.agreementSubject` as const)} /></Field>
              <Field label={t("centers.association.date")}><Input type="date" className="ltr-field" {...register(`associations.${i}.agreementDate` as const)} /></Field>
              <Field label={t("centers.association.reference")}><Input {...register(`associations.${i}.agreementReference` as const)} /></Field>
            </div>
            <OptionField control={control} name={`associations.${i}.partnerTypeIds` as const} multiple legend={t("centers.field.partnersLegend")}
              options={(partnerTypes.data ?? []).map((p) => ({ value: String(p.id), label: name(p) }))} />
            <OptionField control={control} name={`associations.${i}.partnershipEvaluation` as const} legend={t("centers.field.evalLegend")}
              options={EVALS.map((e) => ({ value: e, label: label("PartnershipEvaluation", e) }))} />
            <OptionField control={control} name={`associations.${i}.measureTaken` as const} legend={t("centers.field.measureLegend")}
              options={MEASURES.map((m) => ({ value: m, label: label("PartnershipMeasure", m) }))} />
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label={t("centers.field.measureOtherLong")} required={associations?.[i]?.measureTaken === "OTHER"} error={errors.associations?.[i]?.measureOtherText?.message}>
                <Input disabled={associations?.[i]?.measureTaken !== "OTHER"} invalid={!!errors.associations?.[i]?.measureOtherText}
                  {...register(`associations.${i}.measureOtherText` as const, { validate: (x) => associations?.[i]?.measureTaken !== "OTHER" || !!x.trim() || t("common.required") })} />
              </Field>
            </div>
            <div className="flex justify-end">
              <Button variant="ghost" className="text-danger" onClick={() => assoc.remove(i)}>{t("centers.association.remove")}</Button>
            </div>
          </NumberedSection>
        ))}
        <div className="flex flex-wrap gap-3">
          <Button variant="secondary" onClick={() => assoc.append(blankAssociation())}>{t("centers.association.addLong")}</Button>
          <Button variant="ghost" onClick={() => setNewAssociation(true)}>{t("centers.association.new")}</Button>
        </div>
      </div>

      <NumberedSection id="c6" num={6 + Math.max(assoc.fields.length, 1)} title={t("centers.hr.title")}>
        {center ? <StaffTable summary={staff.data} centerId={center.id} /> : <p className="text-sm text-muted">{t("centers.hr.afterCreate")}</p>}
      </NumberedSection>

      <div className="sticky bottom-0 -mx-4 flex justify-start gap-3 border-t border-line bg-surface/95 px-4 py-3 backdrop-blur lg:-mx-8 lg:px-8">
        <Button type="submit" loading={save.isPending} className="min-h-12 px-7">{t("common.save")}</Button>
        <Button variant="secondary" className="min-h-12 px-6" onClick={() => router.back()}>{t("common.cancel")}</Button>
      </div>

      <Modal open={newAssociation} onClose={() => setNewAssociation(false)} title={t("centers.association.new")}>
        {newAssociation && <NewAssociation onClose={() => setNewAssociation(false)} />}
      </Modal>
    </form>
  );
}

/** Tableau « الموارد البشرية العاملة بالمركز » : calculé à partir des fiches du personnel (jamais saisi). */
function StaffTable({ summary, centerId }: { summary?: StaffSummary; centerId: number }) {
  const { t, num } = useI18n();
  const m = (type: EmployeeType, ...status: AdministrativeStatus[]) => status.reduce((s, x) => s + (summary?.matrix?.[type]?.[x] ?? 0), 0);
  const rows: [string, string, number][] = [
    [t("centers.hr.g.pedagogical"), t("centers.hr.r.pedEn"), m("PEDAGOGICAL", "ENTRAIDE_NATIONALE")],
    ["", t("centers.hr.r.pedAssoc"), m("PEDAGOGICAL", "ASSOCIATION")],
    ["", t("centers.hr.r.pedOther"), m("PEDAGOGICAL", "OTHER_SECTOR")],
    ["", t("centers.hr.r.pedPromo"), m("PEDAGOGICAL", "PROMOTION_NATIONALE")],
    ["", t("centers.hr.r.pedForeign"), m("PEDAGOGICAL", "FOREIGN_VOLUNTEER")],
    [t("centers.hr.g.director"), t("centers.hr.r.directorEn"), summary?.byType?.CENTER_DIRECTOR ?? 0],
    [t("centers.hr.g.admin"), t("centers.hr.r.adminEn"), m("ADMINISTRATIVE", "ENTRAIDE_NATIONALE")],
    ["", t("centers.hr.r.adminAssoc"), m("ADMINISTRATIVE", "ASSOCIATION")],
    ["", t("centers.hr.r.adminOther"), m("ADMINISTRATIVE", "OTHER_SECTOR", "PROMOTION_NATIONALE")],
    [t("centers.hr.g.guest"), t("centers.hr.r.guest"), summary?.byType?.GUEST_TRAINER ?? 0],
  ];
  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted">{t("centers.hr.note")}</p>
        <Link href={`/employees?centerId=${centerId}`} className="text-sm font-medium text-brand-700 hover:underline">{t("centers.hr.list")}</Link>
      </div>
      <table className="w-full text-sm">
        <thead className="bg-surface text-muted">
          <tr>
            <th className="px-3 py-2 text-start font-semibold">{t("centers.hr.col.type")}</th>
            <th className="px-3 py-2 text-start font-semibold">{t("centers.hr.col.status")}</th>
            <th className="px-3 py-2 text-start font-semibold">{t("centers.hr.col.count")}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(([group, status, n], i) => (
            <tr key={i} className="border-t border-line">
              <td className="px-3 py-2 font-semibold">{group}</td>
              <td className="px-3 py-2">{status}</td>
              <td className="px-3 py-2 tabular-nums">{num(n)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function NewAssociation({ onClose }: { onClose: () => void }) {
  const { t } = useI18n();
  const qc = useQueryClient();
  const toast = useToast();
  const [nameAr, setNameAr] = useState("");
  const [nameFr, setNameFr] = useState("");
  const [error, setError] = useState<string>();
  const create = useMutation({
    mutationFn: () => api("/associations", { method: "POST", body: { nameAr, nameFr: nameFr || undefined } }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["associations"] }); toast.success(t("common.saved")); onClose(); },
    onError: (e) => setError(errorText(e, t)),
  });
  return (
    <div className="space-y-4">
      <Field label={t("centers.association.newNameAr")} required><Input value={nameAr} onChange={(e) => setNameAr(e.target.value)} /></Field>
      <Field label={t("centers.association.newNameFr")}><Input className="ltr-field" value={nameFr} onChange={(e) => setNameFr(e.target.value)} /></Field>
      {error && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{error}</p>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button disabled={!nameAr.trim()} loading={create.isPending} onClick={() => create.mutate()}>{t("common.save")}</Button>
      </div>
    </div>
  );
}
