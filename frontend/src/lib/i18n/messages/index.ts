import { core } from "./core";
import { beneficiaries } from "./beneficiaries";
import { catalog } from "./catalog";
import { centers } from "./centers";
import { enums } from "./enums";
import { errors } from "./errors";
import { hr } from "./hr";
import { requests } from "./requests";
import { stats } from "./stats";

/**
 * Ensemble des textes. Chaque module déclare l'arabe (référence) et le français avec exactement les mêmes clés
 * (vérifié par le compilateur) ; les modules sont fusionnés ici.
 */
const ar = { ...core.ar, ...enums.ar, ...errors.ar, ...catalog.ar, ...centers.ar, ...beneficiaries.ar, ...hr.ar, ...requests.ar, ...stats.ar };
const fr = { ...core.fr, ...enums.fr, ...errors.fr, ...catalog.fr, ...centers.fr, ...beneficiaries.fr, ...hr.fr, ...requests.fr, ...stats.fr };

export type MessageKey = keyof typeof ar;
export const messages: Record<"ar" | "fr", Record<MessageKey, string>> = { ar, fr };
