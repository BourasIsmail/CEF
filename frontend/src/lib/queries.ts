"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "./api";
import type { LandProvider, Province, Region } from "./types";

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

import type { Association, CenterSummary, Commune, FiliereSummary, PageResponse, PartnerType, Season, SpaceType } from "./types";

export const useSeasons = () =>
  useQuery({ queryKey: ["seasons"], queryFn: () => api<Season[]>("/seasons"), staleTime: 5 * 60_000 });

export const useCommunes = (provinceId?: number | null) =>
  useQuery({
    queryKey: ["communes", provinceId],
    queryFn: () => api<Commune[]>("/communes", { query: { provinceId } }),
    enabled: !!provinceId,
    ...STATIC,
  });

export const useSpaceTypes = () => useQuery({ queryKey: ["space-types"], queryFn: () => api<SpaceType[]>("/space-types"), ...STATIC });
export const useLandProviders = () => useQuery({ queryKey: ["land-providers"], queryFn: () => api<LandProvider[]>("/land-providers"), ...STATIC });
export const usePartnerTypes = () => useQuery({ queryKey: ["partner-types"], queryFn: () => api<PartnerType[]>("/partner-types"), ...STATIC });

export const useAssociations = (q?: string) =>
  useQuery({
    queryKey: ["associations", q ?? ""],
    queryFn: () => api<PageResponse<Association>>("/associations", { query: { q, size: 100 } }).then((p) => p.content),
    staleTime: 60_000,
  });

/** Toutes les filières actives (listes déroulantes). */
export const useFilieresOptions = () =>
  useQuery({
    queryKey: ["filieres", "options"],
    queryFn: () => api<PageResponse<FiliereSummary>>("/filieres", { query: { size: 100, active: true, sort: "code,asc" } }).then((p) => p.content),
    staleTime: 5 * 60_000,
  });

/** Centres visibles (listes déroulantes), éventuellement d'une province. */
export const useCentersOptions = (provinceId?: number | null) =>
  useQuery({
    queryKey: ["centers", "options", provinceId ?? "all"],
    queryFn: () => api<PageResponse<CenterSummary>>("/centers", { query: { size: 100, provinceId, active: true, sort: "nameAr,asc" } }).then((p) => p.content),
    staleTime: 5 * 60_000,
  });
