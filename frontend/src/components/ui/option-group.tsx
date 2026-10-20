"use client";

import clsx from "clsx";
import { Controller, type Control, type FieldValues, type Path } from "react-hook-form";

export interface Option<V extends string = string> { value: V; label: string }

/**
 * Groupe d'options « à puces » des maquettes : choix unique (radio) ou multiple (cases à cocher).
 * Inactif = visible mais estompé (le groupe devient actif quand la case parente est cochée).
 */
export function OptionGroup<V extends string>({ legend, name, options, value, onChange, multiple, disabled, readOnly, className }: {
  legend?: string; name: string; options: Option<V>[]; value: V | V[] | "" | undefined;
  onChange?: (v: V | V[] | "") => void; multiple?: boolean; disabled?: boolean; readOnly?: boolean; className?: string;
}) {
  const selected = multiple ? ((value as V[] | undefined) ?? []) : value ? [value as V] : [];
  const inactive = disabled || readOnly;
  return (
    <fieldset className={clsx("min-w-0 border-0 p-0 transition-opacity", disabled && "opacity-55", className)}>
      {legend && <legend className="mb-2.5 p-0 text-[13px] font-medium">{legend}</legend>}
      <div className="flex flex-wrap gap-2.5">
        {options.map((o) => {
          const on = selected.includes(o.value);
          return (
            <label key={o.value}
              className={clsx("flex min-h-11 items-center gap-2 rounded-lg border px-4 text-sm",
                on ? "border-brand-600 bg-brand-50 font-semibold" : "border-line bg-white",
                inactive ? "cursor-default" : "cursor-pointer hover:border-brand-500")}>
              <input type={multiple ? "checkbox" : "radio"} name={name} value={o.value} checked={on} disabled={inactive}
                className="accent-brand-600"
                onChange={() => {
                  if (!onChange) return;
                  if (multiple) onChange(on ? (selected as V[]).filter((x) => x !== o.value) : [...(selected as V[]), o.value]);
                  else onChange(o.value);
                }} />
              <span>{o.label}</span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}

/** Liaison react-hook-form d'un {@link OptionGroup}. */
export function OptionField<T extends FieldValues, V extends string>({ control, name, ...rest }: {
  control: Control<T>; name: Path<T>; legend?: string; options: Option<V>[]; multiple?: boolean; disabled?: boolean; readOnly?: boolean; className?: string;
}) {
  return (
    <Controller control={control} name={name} render={({ field }) => (
      <OptionGroup {...rest} name={name} value={field.value as V | V[] | ""} onChange={(v) => field.onChange(v)} />
    )} />
  );
}
