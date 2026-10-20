/** Types des réponses du backend (voir docs/api.md). */

export type Role = "ADMIN" | "REGIONAL" | "PROVINCIAL" | "CENTER_MANAGER" | "HR_OFFICER" | "READ_ONLY";
export type Locale = "ar" | "fr";

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Me {
  id: number;
  username: string;
  fullName: string;
  email?: string;
  role: Role;
  regionId?: number;
  regionNameAr?: string;
  provinceId?: number;
  provinceNameAr?: string;
  centerId?: number;
  enabled: boolean;
  locked: boolean;
  preferredLocale: Locale;
  lastLoginAt?: string;
  createdAt: string;
}

export type UserView = Me;

export interface Region {
  id: number;
  code: string;
  nameAr: string;
  nameFr: string;
}

export interface Province {
  id: number;
  regionId: number;
  code: string;
  nameAr: string;
  nameFr: string;
}

export type StatCategory = "MIGRANT" | "DISABLED" | "OTHER" | "REMAINING";

export interface Cell { male: number; female: number; total: number }

export interface Metrics {
  registered: Cell; departed: Cell; graduates: Cell; failed: Cell; absent: Cell;
  passRatePercent?: number;
}

export interface Dashboard {
  season?: { id: number; label: string };
  totals: Metrics;
  inProgress: number;
  byFiliere: { id: number; code: string; nameAr: string; nameFr: string; registered: number; graduates: number }[];
  byCategory: { category: StatCategory; registered: number }[];
  todo: { graduatesWithoutDiploma: number; examsWithoutExamNumber: number; departedWithoutReason: number };
}
