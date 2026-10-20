import type { Me } from "./types";

/** Droits d'affichage ; le backend reste l'autorité (404 / 403). */
export const can = {
  manageUsers: (u?: Me) => u?.role === "ADMIN",
  /** Création / modification des fiches (bénéficiaires, RH, centres, demandes) : ADMIN et PROVINCIAL. */
  write: (u?: Me) => u?.role === "ADMIN" || u?.role === "PROVINCIAL",
  admin: (u?: Me) => u?.role === "ADMIN",
};
