"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import clsx from "clsx";
import { Plus, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Controller, useFieldArray, useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox, Field, Input, Select } from "@/components/ui/form";
import { Modal } from "@/components/ui/modal";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { errorText } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n } from "@/lib/i18n";
import { useAssociations, useCommunes, usePartnerTypes, useProvinces, useRegions, useSpaceTypes } from "@/lib/queries";
import type { Association, BuildingType, CenterDetail, PartnershipEvaluation, PartnershipMeasure, SpaceType } from "@/lib/types";

interface AssociationValues {
  id?: number;
  associationId: string;
  agreementSubject: string;
  agreementDate: string;
  agreementReference: string;
  partnershipEvaluation: PartnershipEvaluation | "";
  measureTaken: PartnershipMeasure | "";
  measureOtherText: string;
  partnerTypeIds: number[];
}

interface Values {
  regionId: string;
  provincialDirectorateId: string;
  communeId: string;
  code: string;
  nameAr: string;
  nameFr: string;
  areaType: "RURAL" | "URBAN";
  address: string;
  buildingType: BuildingType | "";
  floorsCount: string;
  floorAreas: string[];
  landStatus: string;
  landOwnerText: string;
  managementMode: string;
  spaces: { spaceTypeId: number; checked: boolean; specification: string }[];
  associations: AssociationValues[];
  active: boolean;
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

function initialValues(center: CenterDetail | undefined, spaceTypes: SpaceType[], me: { regionId?: number; provinceId?: number }): Values {
  const chosen = new Map((center?.spaces ?? []).map((s) => [s.spaceTypeId, s.specification ?? ""]));
  const floors = center?.floorAreas ?? [];
  const n = expectedFloors(center?.buildingType ?? "", String(center?.floorsCount ?? ""));
  return {
    regionId: String(center?.regionalDirectorateId ?? me.regionId ?? ""),
    provincialDirectorateId: String(center?.provincialDirectorateId ?? me.provinceId ?? ""),
    communeId: center ? String(center.communeId) : "",
    code: center?.code ?? "",
    nameAr: center?.nameAr ?? "",
    nameFr: center?.nameFr ?? "",
    areaType: center?.areaType ?? "URBAN",
    address: center?.address ?? "",
    buildingType: center?.buildingType ?? "",
    floorsCount: center?.floorsCount ? String(center.floorsCount) : "",
    floorAreas: Array.from({ length: n }, (_, i) => {
      const f = floors.find((x) => x.floorNumber === i + 1);
      return f ? String(f.areaM2) : "";
    }),
    landStatus: center?.landStatus ?? "",
    landOwnerText: center?.landOwnerText ?? "",
    managementMode: center?.managementMode ?? "",
    spaces: spaceTypes.map((s) => ({ spaceTypeId: s.id, checked: chosen.has(s.id), specification: chosen.get(s.id) ?? "" })),
    associations: (center?.associations ?? []).map((a) => ({
      id: a.id, associationId: String(a.associationId), agreementSubject: a.agreementSubject ?? "", agreementDate: a.agreementDate ?? "",
      agreementReference: a.agreementReference ?? "", partnershipEvaluation: a.partnershipEvaluation ?? "", measureTaken: a.measureTaken ?? "",
      measureOtherText: a.measureOtherText ?? "", partnerTypeIds: a.partners.map((p) => p.id),
    })),
    active: center?.active ?? true,
  };
}

/** Formulaire d'un centre : sections dans l'ordre de la maquette (données générales → ... → association partenaire). */
export function CenterForm({ center }: { center?: CenterDetail }) {
  const me = useMe();
  const spaceTypes = useSpaceTypes();
  if (!spaceTypes.data) return null;
  return <Form center={center} spaceTypes={spaceTypes.data} me={me} />;
}

function Form({ center, spaceTypes, me }: { center?: CenterDetail; spaceTypes: SpaceType[]; me: ReturnType<typeof useMe> }) {
  const { t, name, label } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const qc = useQueryClient();
  const admin = me.role === "ADMIN";
  const [formError, setFormError] = useState<string>();
  const [newAssociation, setNewAssociation] = useState(false);

  const { register, handleSubmit, control, setValue, setError, getValues, formState: { errors } } =
    useForm<Values>({ defaultValues: initialValues(center, spaceTypes, me) });
  const assoc = useFieldArray({ control, name: "associations" });

  const regionId = useWatch({ control, name: "regionId" });
  const provinceId = useWatch({ control, name: "provincialDirectorateId" });
  const buildingType = useWatch({ control, name: "buildingType" });
  const floorsCount = useWatch({ control, name: "floorsCount" });
  const landStatus = useWatch({ control, name: "landStatus" });
  const spaces = useWatch({ control, name: "spaces" });
  const associations = useWatch({ control, name: "associations" });
  const floorAreas = useWatch({ control, name: "floorAreas" });

  const regions = useRegions();
  const provinces = useProvinces(regionId ? Number(regionId) : undefined);
  const communes = useCommunes(provinceId ? Number(provinceId) : undefined);
  const directory = useAssociations();
  const partnerTypes = usePartnerTypes();

  const floors = expectedFloors(buildingType, floorsCount);
  // le nombre de champs « surface d'étage » suit le type de bâtiment / le nombre d'étages
  useEffect(() => {
    const current = getValues("floorAreas") ?? [];
    if (current.length !== floors) setValue("floorAreas", Array.from({ length: floors }, (_, i) => current[i] ?? ""));
  }, [floors, getValues, setValue]);

  const totalArea = (floorAreas ?? []).reduce((s, v) => s + (Number(v) || 0), 0);
  const needsOwner = landStatus === "PROVIDED_TO_EN" || landStatus === "OTHER_SECTOR";

  const save = useMutation({
    mutationFn: (v: Values) => {
      const body = {
        provincialDirectorateId: admin ? Number(v.provincialDirectorateId) : undefined,
        communeId: Number(v.communeId),
        code: v.code.trim() || undefined,
        nameAr: v.nameAr, nameFr: v.nameFr || undefined, areaType: v.areaType, address: v.address || undefined,
        buildingType: v.buildingType || undefined,
        floorsCount: v.buildingType === "OTHER" ? Number(v.floorsCount) : undefined,
        floorAreas: v.buildingType ? v.floorAreas.map((a, i) => ({ floorNumber: i + 1, areaM2: Number(a) })) : [],
        landStatus: v.landStatus || undefined,
        landOwnerText: needsOwner ? v.landOwnerText : undefined,
        managementMode: v.managementMode || undefined,
        spaces: v.spaces.filter((s) => s.checked).map((s) => ({ spaceTypeId: s.spaceTypeId, specification: s.specification || undefined })),
        associations: v.associations.map((a) => ({
          id: a.id, associationId: Number(a.associationId),
          agreementSubject: a.agreementSubject || undefined, agreementDate: a.agreementDate || undefined,
          agreementReference: a.agreementReference || undefined,
          partnershipEvaluation: a.partnershipEvaluation || undefined,
          measureTaken: a.measureTaken || undefined,
          measureOtherText: a.measureTaken === "OTHER" ? a.measureOtherText : undefined,
          partnerTypeIds: a.partnerTypeIds,
        })),
        active: v.active,
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

  return (
    <form onSubmit={handleSubmit((v) => { setFormError(undefined); save.mutate(v); })} noValidate className="space-y-6">
      {formError && <p role="alert" className="rounded-lg bg-danger-50 px-4 py-3 text-sm text-danger">{formError}</p>}

      <Card title={t("centers.section.general")}>
        <div className="grid gap-4 sm:grid-cols-2">
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
              <Field label={t("centers.field.region")}><Input disabled value={regionLabel ?? ""} readOnly /></Field>
              <Field label={t("centers.field.province")} hint={center ? undefined : t("centers.field.provinceHint")}><Input disabled value={provinceLabel ?? ""} readOnly /></Field>
            </>
          )}
          <Field label={t("centers.field.name")} required error={errors.nameAr?.message} className="sm:col-span-2">
            <Input invalid={!!errors.nameAr} {...register("nameAr", required)} />
          </Field>
          <Field label={t("centers.field.commune")} required error={errors.communeId?.message}>
            <Select invalid={!!errors.communeId} {...register("communeId", required)}>
              <option value="">{t("common.select")}</option>
              {communes.data?.map((c) => <option key={c.id} value={c.id}>{name(c)}</option>)}
            </Select>
          </Field>
          <Field label={t("centers.field.area")} required>
            <div className="flex gap-2">
              {(["URBAN", "RURAL"] as const).map((a) => (
                <label key={a} className="flex flex-1 cursor-pointer items-center gap-2 rounded-lg border border-line px-3 py-2 text-sm has-[:checked]:border-brand-600 has-[:checked]:bg-brand-50">
                  <input type="radio" value={a} className="accent-brand-600" {...register("areaType")} /> {label("AreaType", a)}
                </label>
              ))}
            </div>
          </Field>
          <Field label={t("centers.field.nameFr")}><Input className="ltr-field" {...register("nameFr")} /></Field>
          {admin && (
            <Field label={t("centers.field.code")} hint={t("centers.field.codeHint")}><Input className="ltr-field" {...register("code")} /></Field>
          )}
          <Field label={t("centers.field.address")} className="sm:col-span-2"><Input {...register("address")} /></Field>
        </div>
      </Card>

      <Card title={t("centers.section.building")}>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label={t("centers.field.building")}>
            <Select {...register("buildingType")}>
              <option value="">{t("centers.notSpecified")}</option>
              {BUILDINGS.map((b) => <option key={b} value={b}>{label("BuildingType", b)}</option>)}
            </Select>
          </Field>
          {buildingType === "OTHER" && (
            <Field label={t("centers.field.floors")} required error={errors.floorsCount?.message}>
              <Input type="number" min={1} max={20} invalid={!!errors.floorsCount} {...register("floorsCount", { validate: (v) => buildingType !== "OTHER" || !!v || t("common.required") })} />
            </Field>
          )}
          {floors > 0 && <Field label={t("centers.field.totalArea")}><Input disabled readOnly value={`${totalArea.toLocaleString("en", { maximumFractionDigits: 2 })} m²`} /></Field>}
        </div>
        {floors > 0 && (
          <div className="mt-4 grid gap-4 sm:grid-cols-3">
            {Array.from({ length: floors }, (_, i) => (
              <Field key={i} label={t("centers.field.floorArea", { n: i + 1 })} required error={errors.floorAreas?.[i]?.message}>
                <Input type="number" step="0.01" min="0.01" invalid={!!errors.floorAreas?.[i]}
                  {...register(`floorAreas.${i}` as const, { required: t("common.required") })} />
              </Field>
            ))}
          </div>
        )}
      </Card>

      <Card title={t("centers.section.spaces")}>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {spaceTypes.map((s, i) => (
            <div key={s.id} className="rounded-lg border border-line p-3">
              <Checkbox label={name(s)} {...register(`spaces.${i}.checked` as const)} />
              {s.requiresSpecification && spaces?.[i]?.checked && (
                <Input className="mt-2" placeholder={t("centers.field.spaceSpec")} aria-label={t("centers.field.spaceSpec")}
                  {...register(`spaces.${i}.specification` as const, { required: t("common.required") })}
                  invalid={!!errors.spaces?.[i]?.specification} />
              )}
            </div>
          ))}
        </div>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card title={t("centers.section.land")}>
          <div className="space-y-4">
            <Field label={t("centers.field.land")}>
              <Select {...register("landStatus")}>
                <option value="">{t("centers.notSpecified")}</option>
                {LANDS.map((l) => <option key={l} value={l}>{label("LandStatus", l)}</option>)}
              </Select>
            </Field>
            {needsOwner && (
              <Field label={t("centers.field.landOwner")} required error={errors.landOwnerText?.message}>
                <Input invalid={!!errors.landOwnerText} {...register("landOwnerText", { validate: (v) => !needsOwner || !!v?.trim() || t("common.required") })} />
              </Field>
            )}
          </div>
        </Card>
        <Card title={t("centers.section.management")}>
          <Field label={t("centers.field.management")}>
            <Select {...register("managementMode")}>
              <option value="">{t("centers.notSpecified")}</option>
              {MODES.map((m) => <option key={m} value={m}>{label("ManagementMode", m)}</option>)}
            </Select>
          </Field>
        </Card>
      </div>

      <Card title={t("centers.section.associations")}
        actions={<div className="flex gap-2">
          <Button variant="ghost" onClick={() => setNewAssociation(true)}>{t("centers.association.new")}</Button>
          <Button variant="secondary" icon={<Plus className="size-4" />}
            onClick={() => assoc.append({ associationId: "", agreementSubject: "", agreementDate: "", agreementReference: "", partnershipEvaluation: "", measureTaken: "", measureOtherText: "", partnerTypeIds: [] })}>
            {t("centers.association.add")}
          </Button>
        </div>}>
        {assoc.fields.length === 0 && <p className="text-sm text-muted">{t("centers.association.none")}</p>}
        <div className="space-y-5">
          {assoc.fields.map((f, i) => (
            <fieldset key={f.id} className="rounded-lg border border-line p-4">
              <legend className="px-2 text-sm font-semibold text-brand-700">{t("centers.association.title", { n: i + 1 })}</legend>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label={t("centers.association.name")} required error={errors.associations?.[i]?.associationId?.message} className="sm:col-span-2">
                  <Select invalid={!!errors.associations?.[i]?.associationId} {...register(`associations.${i}.associationId` as const, required)}>
                    <option value="">{t("common.select")}</option>
                    {directory.data?.map((a: Association) => <option key={a.id} value={a.id}>{name(a)}</option>)}
                  </Select>
                </Field>
                <Field label={t("centers.association.subject")} className="sm:col-span-2"><Input {...register(`associations.${i}.agreementSubject` as const)} /></Field>
                <Field label={t("centers.association.date")}><Input type="date" className="ltr-field" {...register(`associations.${i}.agreementDate` as const)} /></Field>
                <Field label={t("centers.association.reference")}><Input className="ltr-field" {...register(`associations.${i}.agreementReference` as const)} /></Field>
                <Field label={t("centers.association.evaluation")}>
                  <Select {...register(`associations.${i}.partnershipEvaluation` as const)}>
                    <option value="">{t("centers.notSpecified")}</option>
                    {EVALS.map((e) => <option key={e} value={e}>{label("PartnershipEvaluation", e)}</option>)}
                  </Select>
                </Field>
                <Field label={t("centers.association.measure")}>
                  <Select {...register(`associations.${i}.measureTaken` as const)}>
                    <option value="">{t("centers.notSpecified")}</option>
                    {MEASURES.map((m) => <option key={m} value={m}>{label("PartnershipMeasure", m)}</option>)}
                  </Select>
                </Field>
                {associations?.[i]?.measureTaken === "OTHER" && (
                  <Field label={t("centers.association.measureOther")} required error={errors.associations?.[i]?.measureOtherText?.message} className="sm:col-span-2">
                    <Input invalid={!!errors.associations?.[i]?.measureOtherText} {...register(`associations.${i}.measureOtherText` as const, required)} />
                  </Field>
                )}
                <div className="sm:col-span-2">
                  <span className="mb-1.5 block text-sm font-medium">{t("centers.association.partners")}</span>
                  <Controller control={control} name={`associations.${i}.partnerTypeIds` as const} render={({ field }) => (
                    <div className="flex flex-wrap gap-2">
                      {partnerTypes.data?.map((p) => {
                        const on = field.value.includes(p.id);
                        return (
                          <button key={p.id} type="button" aria-pressed={on}
                            onClick={() => field.onChange(on ? field.value.filter((x: number) => x !== p.id) : [...field.value, p.id])}
                            className={clsx("rounded-full border px-3 py-1 text-sm", on ? "border-brand-600 bg-brand-50 text-brand-700" : "border-line text-muted hover:bg-surface")}>
                            {name(p)}
                          </button>
                        );
                      })}
                    </div>
                  )} />
                </div>
              </div>
              <div className="mt-3 flex justify-end">
                <Button variant="ghost" className="text-danger" icon={<Trash2 className="size-4" />} onClick={() => assoc.remove(i)}>{t("centers.association.remove")}</Button>
              </div>
            </fieldset>
          ))}
        </div>
      </Card>

      {center && <Checkbox label={t("centers.field.active")} {...register("active")} />}

      <div className="sticky bottom-0 -mx-4 flex justify-end gap-2 border-t border-line bg-surface/95 px-4 py-3 backdrop-blur lg:-mx-8 lg:px-8">
        <Button variant="secondary" onClick={() => router.back()}>{t("common.cancel")}</Button>
        <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
      </div>

      <Modal open={newAssociation} onClose={() => setNewAssociation(false)} title={t("centers.association.new")}>
        {newAssociation && <NewAssociation onClose={() => setNewAssociation(false)} />}
      </Modal>
    </form>
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
