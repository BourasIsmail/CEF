import { core } from "./core";
import { catalog } from "./catalog";
import { centers } from "./centers";
import { enums } from "./enums";
import { errors } from "./errors";

/**
 * Ensemble des textes. Chaque module déclare l'arabe (référence) et le français avec exactement les mêmes clés
 * (vérifié par le compilateur) ; les modules sont fusionnés ici.
 */
const ar = { ...core.ar, ...enums.ar, ...errors.ar, ...catalog.ar, ...centers.ar };
const fr = { ...core.fr, ...enums.fr, ...errors.fr, ...catalog.fr, ...centers.fr };

export type MessageKey = keyof typeof ar;
export const messages: Record<"ar" | "fr", Record<MessageKey, string>> = { ar, fr };
