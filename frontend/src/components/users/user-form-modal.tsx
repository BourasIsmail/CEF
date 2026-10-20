"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Field, Input, Select } from "@/components/ui/form";
import { Modal } from "@/components/ui/modal";
import { PasswordInput } from "@/components/ui/password-input";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { errorKey } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n } from "@/lib/i18n";
import { useProvinces, useRegions } from "@/lib/queries";
import type { Locale, Role, UserView } from "@/lib/types";

interface FormValues {
  username: string;
  password: string;
  fullName: string;
  email: string;
  role: Role;
  regionId: string;
  provinceId: string;
  preferredLocale: Locale;
}

const OPEN_ROLES: Role[] = ["ADMIN", "REGIONAL", "PROVINCIAL"];

const blank: FormValues = { username: "", password: "", fullName: "", email: "", role: "PROVINCIAL", regionId: "", provinceId: "", preferredLocale: "ar" };

function toForm(u: UserView): FormValues {
  return {
    username: u.username, password: "", fullName: u.fullName, email: u.email ?? "", role: u.role,
    regionId: u.regionId ? String(u.regionId) : "", provinceId: u.provinceId ? String(u.provinceId) : "", preferredLocale: u.preferredLocale,
  };
}

/** Création ou modification d'un compte ; le mot de passe ne se saisit qu'à la création. */
export function UserFormModal({ user, onClose }: { user: UserView | "new" | null; onClose: () => void }) {
  const { t } = useI18n();
  return (
    <Modal open={!!user} onClose={onClose} title={user === "new" ? t("users.new") : t("users.edit")}>
      {user && <UserForm user={user} onClose={onClose} />}
    </Modal>
  );
}

// Le contenu d'un Modal n'existe que lorsqu'il est ouvert : l'état du formulaire repart de zéro à chaque ouverture.
function UserForm({ user, onClose }: { user: UserView | "new"; onClose: () => void }) {
  const { t, name } = useI18n();
  const toast = useToast();
  const qc = useQueryClient();
  const isNew = user === "new";
  const [formError, setFormError] = useState<string>();
  const { register, handleSubmit, control, setValue, setError, formState: { errors } } =
    useForm<FormValues>({ defaultValues: isNew ? blank : toForm(user as UserView) });

  const role = useWatch({ control, name: "role" });
  const regionId = useWatch({ control, name: "regionId" });
  const password = useWatch({ control, name: "password" });
  const regions = useRegions();
  const provinces = useProvinces(regionId ? Number(regionId) : undefined);

  const save = useMutation({
    mutationFn: (v: FormValues) => {
      const scope = {
        regionId: role === "REGIONAL" ? Number(v.regionId) : undefined,
        provinceId: role === "PROVINCIAL" ? Number(v.provinceId) : undefined,
      };
      const common = { fullName: v.fullName, email: v.email || undefined, role: v.role, preferredLocale: v.preferredLocale, ...scope };
      return isNew
        ? api<UserView>("/users", { method: "POST", body: { ...common, username: v.username, password: v.password } })
        : api<UserView>(`/users/${(user as UserView).id}`, { method: "PUT", body: common });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["users"] });
      toast.success(t(isNew ? "users.created" : "users.updated"));
      onClose();
    },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(t(errorKey(e))); },
  });

  return (
    <>
      <form onSubmit={handleSubmit((v) => { setFormError(undefined); save.mutate(v); })} className="space-y-4" noValidate>
        <Field label={t("users.field.username")} hint={t("users.field.username.hint")} required error={errors.username?.message}>
          <Input className="ltr-field" disabled={!isNew} autoComplete="off" invalid={!!errors.username}
            {...register("username", { required: t("common.required"), pattern: { value: /^[A-Za-z0-9._-]{3,100}$/, message: t("users.field.username.hint") } })} />
        </Field>
        <Field label={t("users.field.fullName")} required error={errors.fullName?.message}>
          <Input invalid={!!errors.fullName} {...register("fullName", { required: t("common.required") })} />
        </Field>
        <Field label={t("users.field.email")} error={errors.email?.message}>
          <Input type="email" className="ltr-field" invalid={!!errors.email} {...register("email")} />
        </Field>
        <Field label={t("users.field.role")} hint={t("users.roleNote")} required error={errors.role?.message}>
          <Select {...register("role", { onChange: () => { setValue("regionId", ""); setValue("provinceId", ""); } })}>
            {OPEN_ROLES.map((r) => <option key={r} value={r}>{t(`role.${r}`)}</option>)}
          </Select>
        </Field>

        {role === "REGIONAL" && (
          <Field label={t("users.field.region")} required error={errors.regionId?.message}>
            <Select invalid={!!errors.regionId} {...register("regionId", { required: t("common.required") })}>
              <option value="">{t("common.select")}</option>
              {regions.data?.map((r) => <option key={r.id} value={r.id}>{name(r)}</option>)}
            </Select>
          </Field>
        )}
        {role === "PROVINCIAL" && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("users.field.region")}>
              <Select value={regionId} onChange={(e) => { setValue("regionId", e.target.value); setValue("provinceId", ""); }}>
                <option value="">{t("common.select")}</option>
                {regions.data?.map((r) => <option key={r.id} value={r.id}>{name(r)}</option>)}
              </Select>
            </Field>
            <Field label={t("users.field.province")} required error={errors.provinceId?.message}>
              <Select invalid={!!errors.provinceId} {...register("provinceId", { required: t("common.required") })}>
                <option value="">{t("common.select")}</option>
                {provinces.data?.map((p) => <option key={p.id} value={p.id}>{name(p)}</option>)}
              </Select>
            </Field>
          </div>
        )}

        {isNew && (
          <Field label={t("users.field.password")} hint={t("account.password.hint")} required error={errors.password?.message}>
            <PasswordInput strength={password} invalid={!!errors.password}
              {...register("password", { required: t("common.required"), minLength: { value: 12, message: t("err.password.weak") } })} />
          </Field>
        )}
        <Field label={t("users.field.locale")}>
          <Select {...register("preferredLocale")}>
            <option value="ar">{t("lang.ar")}</option>
            <option value="fr">{t("lang.fr")}</option>
          </Select>
        </Field>

        {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
          <Button type="submit" loading={save.isPending}>{t("common.save")}</Button>
        </div>
      </form>
    </>
  );
}
