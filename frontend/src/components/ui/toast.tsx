"use client";

import clsx from "clsx";
import { CheckCircle2, XCircle } from "lucide-react";
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";

interface Toast { id: number; message: string; tone: "ok" | "error" }
interface ToastApi { success: (m: string) => void; error: (m: string) => void }

const Ctx = createContext<ToastApi | null>(null);
let seq = 0;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const push = useCallback((message: string, tone: Toast["tone"]) => {
    const id = ++seq;
    setToasts((l) => [...l, { id, message, tone }]);
    setTimeout(() => setToasts((l) => l.filter((x) => x.id !== id)), 5000);
  }, []);

  const api = useMemo<ToastApi>(() => ({ success: (m) => push(m, "ok"), error: (m) => push(m, "error") }), [push]);

  return (
    <Ctx.Provider value={api}>
      {children}
      <div aria-live="polite" className="fixed bottom-4 end-4 z-[60] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2">
        {toasts.map((x) => (
          <div key={x.id} role="status"
            className={clsx("flex items-start gap-2 rounded-lg border bg-white px-4 py-3 text-sm shadow-lg",
              x.tone === "ok" ? "border-ok/40" : "border-danger/40")}>
            {x.tone === "ok" ? <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-ok" /> : <XCircle className="mt-0.5 size-4 shrink-0 text-danger" />}
            <span>{x.message}</span>
          </div>
        ))}
      </div>
    </Ctx.Provider>
  );
}

export function useToast(): ToastApi {
  const v = useContext(Ctx);
  if (!v) throw new Error("useToast hors ToastProvider");
  return v;
}
