"use client";

import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Card, PageHeader } from "@/components/ui/card";
import { Field } from "@/components/ui/form";
import { PasswordInput } from "@/components/ui/password-input";
import { useToast } from "@/components/ui/toast";
import { LanguageSwitch } from "@/components/layout/language-switch";
import { api } from "@/lib/api";
import { useAuth, useMe } from "@/lib/auth";
import { errorKey } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useI18n } from "@/lib/i18n";
import type { Me } from "@/lib/types";

interface PasswordForm { currentPassword: string; newPassword: string; confirm: string }

/** Chaque utilisateur change ici son propre mot de passe et sa langue. */
export default function AccountPage() {
  const user = useMe();
  const { setUser } = useAuth();
  const { t, dateTime } = useI18n();
  const toast = useToast();
  const [formError, setFormError] = useState<string>();
  const { register, handleSubmit, control, setError, reset, formState: { errors, isSubmitting } } =
    useForm<PasswordForm>({ defaultValues: { currentPassword: "", newPassword: "", confirm: "" } });
  const newPassword = useWatch({ control, name: "newPassword" });

  async function onSubmit(v: PasswordForm) {
    setFormError(undefined);
    try {
      const updated = await api<Me>("/auth/password", { method: "PUT", body: { currentPassword: v.currentPassword, newPassword: v.newPassword } });
      setUser(updated);
      reset();
      toast.success(t("account.password.done"));
    } catch (e) {
      if (!applyServerErrors(e, setError)) setFormError(t(errorKey(e)));
    }
  }

  const scope = user.provinceNameAr ?? user.regionNameAr ?? t("users.scopeAll");
  const rows: [string, string][] = [
    [t("users.field.fullName"), user.fullName],
    [t("users.field.email"), user.email],
    [t("account.role"), t(`role.${user.role}`)],
    [t("account.scope"), scope],
    [t("account.lastLogin"), dateTime(user.lastLoginAt)],
  ];

  return (
    <>
      <PageHeader title={t("account.title")} />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title={t("account.profile")}>
          <dl className="space-y-3 text-sm">
            {rows.map(([k, v]) => (
              <div key={k} className="flex justify-between gap-4 border-b border-line pb-2 last:border-0">
                <dt className="text-muted">{k}</dt>
                <dd className="font-medium">{v}</dd>
              </div>
            ))}
            <div className="flex items-center justify-between gap-4 pt-1">
              <dt className="text-muted">{t("account.locale")}</dt>
              <dd><LanguageSwitch /></dd>
            </div>
          </dl>
        </Card>

        <Card title={t("account.password.title")}>
          <p className="mb-4 text-sm text-muted">{t("account.password.hint")}</p>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <Field label={t("account.password.current")} required error={errors.currentPassword?.message}>
              <PasswordInput autoComplete="current-password" invalid={!!errors.currentPassword}
                {...register("currentPassword", { required: t("common.required") })} />
            </Field>
            <Field label={t("account.password.new")} required error={errors.newPassword?.message}>
              <PasswordInput autoComplete="new-password" strength={newPassword} invalid={!!errors.newPassword}
                {...register("newPassword", { required: t("common.required"), minLength: { value: 12, message: t("err.password.weak") } })} />
            </Field>
            <Field label={t("account.password.confirm")} required error={errors.confirm?.message}>
              <PasswordInput autoComplete="new-password" invalid={!!errors.confirm}
                {...register("confirm", { required: t("common.required"), validate: (v) => v === newPassword || t("account.password.mismatch") })} />
            </Field>
            {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
            <Button type="submit" loading={isSubmitting}>{t("account.password.submit")}</Button>
          </form>
        </Card>
      </div>
    </>
  );
}
