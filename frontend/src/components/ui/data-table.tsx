"use client";

import { flexRender, getCoreRowModel, useReactTable, type ColumnDef, type SortingState } from "@tanstack/react-table";
import clsx from "clsx";
import { ChevronDown, ChevronUp, ChevronsUpDown } from "lucide-react";
import { useT } from "@/lib/i18n";
import { Button } from "./button";
import { Empty, Spinner } from "./states";

interface Props<T> {
  columns: ColumnDef<T, any>[];
  data: T[];
  loading?: boolean;
  /** Tri côté serveur : l'identifiant de colonne est le critère de tri de l'API. */
  sorting?: SortingState;
  onSortingChange?: (s: SortingState) => void;
  page: number;
  pageSize: number;
  total: number;
  onPageChange: (page: number) => void;
  onRowClick?: (row: T) => void;
}

/** Tableau paginé et triable côté serveur (TanStack Table en mode manuel). */
export function DataTable<T>({ columns, data, loading, sorting = [], onSortingChange, page, pageSize, total, onPageChange, onRowClick }: Props<T>) {
  "use no memo"; // TanStack Table renvoie des fonctions non mémoïsables
  const t = useT();
  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data, columns,
    state: { sorting },
    manualSorting: true,
    manualPagination: true,
    enableSortingRemoval: false,
    onSortingChange: (u) => onSortingChange?.(typeof u === "function" ? u(sorting) : u),
    getCoreRowModel: getCoreRowModel(),
  });
  const pages = Math.max(1, Math.ceil(total / pageSize));
  const from = total === 0 ? 0 : page * pageSize + 1;
  const to = Math.min(total, (page + 1) * pageSize);

  return (
    <div className="overflow-hidden rounded-xl border border-line bg-white">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-surface text-muted">
            {table.getHeaderGroups().map((hg) => (
              <tr key={hg.id}>
                {hg.headers.map((h) => {
                  const sorted = h.column.getIsSorted();
                  return (
                    <th key={h.id} scope="col" aria-sort={sorted === "asc" ? "ascending" : sorted === "desc" ? "descending" : undefined}
                      className="whitespace-nowrap px-4 py-3 text-start font-semibold">
                      {h.isPlaceholder ? null : h.column.getCanSort() && onSortingChange ? (
                        <button type="button" onClick={h.column.getToggleSortingHandler()} className="inline-flex items-center gap-1 hover:text-ink">
                          {flexRender(h.column.columnDef.header, h.getContext())}
                          {sorted === "asc" ? <ChevronUp className="size-3.5" /> : sorted === "desc" ? <ChevronDown className="size-3.5" /> : <ChevronsUpDown className="size-3.5 opacity-40" />}
                        </button>
                      ) : flexRender(h.column.columnDef.header, h.getContext())}
                    </th>
                  );
                })}
              </tr>
            ))}
          </thead>
          <tbody>
            {table.getRowModel().rows.map((row) => (
              <tr key={row.id} onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                className={clsx("border-t border-line", onRowClick && "cursor-pointer hover:bg-brand-50/40")}>
                {row.getVisibleCells().map((c) => (
                  <td key={c.id} className="px-4 py-3 align-middle">{flexRender(c.column.columnDef.cell, c.getContext())}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {loading && data.length === 0 && <Spinner />}
      {!loading && data.length === 0 && <Empty />}
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-4 py-3 text-sm text-muted">
        <span>{t("common.rows", { from, to, total })}</span>
        <div className="flex items-center gap-2">
          <Button variant="secondary" className="px-3 py-1" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>{t("common.previous")}</Button>
          <span>{t("common.page", { page: page + 1, pages })}</span>
          <Button variant="secondary" className="px-3 py-1" disabled={page + 1 >= pages} onClick={() => onPageChange(page + 1)}>{t("common.next")}</Button>
        </div>
      </div>
    </div>
  );
}
