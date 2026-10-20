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
/** Pages affichées : la page courante et ses voisines (au plus 5), indices à partir de 0. */
function windowPages(page: number, pages: number): number[] {
  const start = Math.max(0, Math.min(page - 2, pages - 5));
  return Array.from({ length: Math.min(5, pages) }, (_, i) => start + i);
}

/** Le tri est fait côté serveur : toute colonne nommée et non désactivée est triable sans accesseur de valeur. */
function sortable<T>(columns: ColumnDef<T>[]): ColumnDef<T>[] {
  return columns.map((c) => ("accessorFn" in c || "accessorKey" in c || !c.id || c.enableSorting === false ? c : { ...c, accessorFn: () => null } as ColumnDef<T>));
}

export function DataTable<T>({ columns, data, loading, sorting = [], onSortingChange, page, pageSize, total, onPageChange, onRowClick }: Props<T>) {
  "use no memo"; // TanStack Table renvoie des fonctions non mémoïsables
  const t = useT();
  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data, columns: sortable(columns),
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
        <nav className="flex items-center gap-2" aria-label={t("common.page", { page: page + 1, pages })}>
          {windowPages(page, pages).map((n) => (
            <button key={n} type="button" onClick={() => onPageChange(n)} aria-current={n === page ? "page" : undefined}
              className={`h-9 min-w-9 rounded-lg border px-3 text-sm ${n === page ? "border-brand-700 bg-brand-700 text-white" : "border-line bg-white text-ink hover:bg-brand-50"}`}>
              {n + 1}
            </button>
          ))}
          <button type="button" disabled={page + 1 >= pages} onClick={() => onPageChange(page + 1)}
            className="h-9 rounded-lg border border-line bg-white px-3 text-sm text-ink hover:bg-brand-50 disabled:opacity-50">{t("common.next")} ›</button>
        </nav>
      </div>
    </div>
  );
}
