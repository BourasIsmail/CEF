"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/modal";
import { Spinner } from "@/components/ui/states";
import { useI18n } from "@/lib/i18n";
import { useCentersOptions } from "@/lib/queries";
import type { CenterSummary } from "@/lib/types";

/** « إسناد إلى مراكز » : cases à cocher des centres, regroupés par direction provinciale. */
export function AssignCentersModal({ open, onClose, initial, loading, onSave }: {
  open: boolean; onClose: () => void; initial: number[]; loading?: boolean; onSave: (centerIds: number[]) => void;
}) {
  const { t } = useI18n();
  return (
    <Modal open={open} onClose={onClose} title={t("filieres.assign")} wide>
      {open && <Body onClose={onClose} initial={initial} loading={loading} onSave={onSave} />}
    </Modal>
  );
}

function Body({ onClose, initial, loading, onSave }: { onClose: () => void; initial: number[]; loading?: boolean; onSave: (ids: number[]) => void }) {
  const { t, name } = useI18n();
  const centers = useCentersOptions();
  const [selected, setSelected] = useState(() => new Set(initial));

  const groups = useMemo(() => {
    const map = new Map<number, { label: string; items: CenterSummary[] }>();
    for (const c of centers.data ?? []) {
      const g = map.get(c.provincialDirectorateId) ?? { label: c.provincialDirectorateNameAr, items: [] };
      g.items.push(c);
      map.set(c.provincialDirectorateId, g);
    }
    return [...map.values()].sort((a, b) => a.label.localeCompare(b.label, "ar"));
  }, [centers.data]);

  const toggle = (id: number) => setSelected((s) => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n; });

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted">{t("filieres.assign.hint")}</p>
      {centers.isPending ? <Spinner /> : groups.map((g) => (
        <fieldset key={g.label} className="space-y-2">
          <legend className="mb-1 text-sm font-semibold text-[#3c4d55]">{g.label}</legend>
          <div className="flex flex-wrap gap-2">
            {g.items.map((c) => (
              <label key={c.id} className={`flex min-h-11 cursor-pointer items-center gap-2 rounded-full border px-4 text-sm ${selected.has(c.id) ? "border-brand-600 bg-[#E3F0EE] text-[#0B4745]" : "border-[#b9c6c3] bg-white"}`}>
                <input type="checkbox" className="size-4 accent-brand-600" checked={selected.has(c.id)} onChange={() => toggle(c.id)} />
                {name(c)}
              </label>
            ))}
          </div>
        </fieldset>
      ))}
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose}>{t("common.cancel")}</Button>
        <Button loading={loading} onClick={() => onSave([...selected])}>{t("common.save")}</Button>
      </div>
    </div>
  );
}
