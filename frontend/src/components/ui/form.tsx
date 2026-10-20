import clsx from "clsx";
import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";

const control =
  "w-full rounded-lg border border-line bg-white px-3 py-2 text-sm text-ink placeholder:text-muted/60 " +
  "focus:border-brand-500 focus:outline-none disabled:bg-surface disabled:text-muted aria-[invalid=true]:border-danger";

export function Field({
  label, hint, error, required, className, children,
}: { label: string; hint?: string; error?: string; required?: boolean; className?: string; children: ReactNode }) {
  return (
    <label className={clsx("flex flex-col gap-1.5", className)}>
      <span className="text-sm font-medium text-ink">
        {label}
        {required && <span className="ms-1 text-danger" aria-hidden>*</span>}
      </span>
      {children}
      {hint && !error && <span className="text-xs text-muted">{hint}</span>}
      {error && <span role="alert" className="text-xs text-danger">{error}</span>}
    </label>
  );
}

export function Input({ className, invalid, ...rest }: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return <input aria-invalid={invalid || undefined} className={clsx(control, className)} {...rest} />;
}

export function Select({ className, invalid, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }) {
  return (
    <select aria-invalid={invalid || undefined} className={clsx(control, "pe-8", className)} {...rest}>
      {children}
    </select>
  );
}

export function Textarea({ className, invalid, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }) {
  return <textarea aria-invalid={invalid || undefined} rows={3} className={clsx(control, className)} {...rest} />;
}

export function Checkbox({ label, className, ...rest }: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return (
    <label className={clsx("inline-flex items-center gap-2 text-sm", className)}>
      <input type="checkbox" className="size-4 accent-brand-600" {...rest} />
      {label}
    </label>
  );
}
