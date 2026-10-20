"use client";

import { Modal } from "./modal";
import { Button } from "./button";
import { useT } from "@/lib/i18n";

export function ConfirmDialog({ open, title, message, danger, loading, onConfirm, onClose }: {
  open: boolean; title: string; message: string; danger?: boolean; loading?: boolean; onConfirm: () => void; onClose: () => void;
}) {
  const t = useT();
  return (
    <Modal open={open} onClose={onClose} title={title}>
      <p className="mb-5 text-sm text-muted">{message}</p>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button variant={danger ? "danger" : "primary"} loading={loading} onClick={onConfirm}>{t("common.confirm")}</Button>
      </div>
    </Modal>
  );
}
