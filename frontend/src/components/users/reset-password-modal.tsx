"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/form";
import { Modal } from "@/components/ui/modal";
import { PasswordInput } from "@/components/ui/password-input";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api";
import { errorKey } from "@/lib/errors";
import { applyServerErrors } from "@/lib/form";
import { useT } from "@/lib/i18n";
import type { UserView } from "@/lib/types";

/** Réinitialisation du mot de passe d'un utilisateur par l'administrateur. */
export function ResetPasswordModal({ user, onClose }: { user: UserView | null; onClose: () => void }) {
  const t = useT();
  return (
    <Modal open={!!user} onClose={onClose} title={`${t("users.resetPassword.title")} — ${user?.fullName ?? ""}`}>
      {user && <ResetForm user={user} onClose={onClose} />}
    </Modal>
  );
}

function ResetForm({ user, onClose }: { user: UserView; onClose: () => void }) {
  const t = useT();
  const toast = useToast();
  const qc = useQueryClient();
  const [formError, setFormError] = useState<string>();
  const { register, handleSubmit, control, setError, formState: { errors } } = useForm<{ newPassword: string; confirm: string }>();
  const value = useWatch({ control, name: "newPassword" }) ?? "";

  const save = useMutation({
    mutationFn: (v: { newPassword: string }) => api(`/users/${user.id}/password`, { method: "PUT", body: { newPassword: v.newPassword } }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["users"] }); toast.success(t("users.resetPassword.done")); onClose(); },
    onError: (e) => { if (!applyServerErrors(e, setError)) setFormError(t(errorKey(e))); },
  });

  return (
    <>
      <p className="mb-4 text-sm text-muted">{t("users.resetPassword.hint")}</p>
      <form onSubmit={handleSubmit((v) => save.mutate(v))} className="space-y-4" noValidate>
        <Field label={t("account.password.new")} required error={errors.newPassword?.message}>
          <PasswordInput strength={value} invalid={!!errors.newPassword}
            {...register("newPassword", { required: t("common.required"), minLength: { value: 12, message: t("err.password.weak") } })} />
        </Field>
        <Field label={t("account.password.confirm")} required error={errors.confirm?.message}>
          <PasswordInput invalid={!!errors.confirm}
            {...register("confirm", { required: t("common.required"), validate: (v) => v === value || t("account.password.mismatch") })} />
        </Field>
        {formError && <p role="alert" className="rounded-lg bg-danger-50 px-3 py-2 text-sm text-danger">{formError}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
          <Button type="submit" loading={save.isPending}>{t("users.resetPassword")}</Button>
        </div>
      </form>
    </>
  );
}
