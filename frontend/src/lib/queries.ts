"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "./api";
import type { Province, Region } from "./types";

/** Référentiels peu changeants, mis en cache longtemps. */
const STATIC = { staleTime: 30 * 60_000 } as const;

export const useRegions = () =>
  useQuery({ queryKey: ["regions"], queryFn: () => api<Region[]>("/regions"), ...STATIC });

export const useProvinces = (regionId?: number | null) =>
  useQuery({
    queryKey: ["provinces", regionId ?? "all"],
    queryFn: () => api<Province[]>("/provinces", { query: { regionId } }),
    ...STATIC,
  });
