import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { ApiError } from "./api";

/** Reporte les erreurs de validation du serveur sur les champs du formulaire. Renvoie true s'il y en avait. */
export function applyServerErrors<T extends FieldValues>(e: unknown, setError: UseFormSetError<T>): boolean {
  if (e instanceof ApiError && e.errors) {
    for (const [field, message] of Object.entries(e.errors)) {
      setError(field as Path<T>, { type: "server", message });
    }
    return Object.keys(e.errors).length > 0;
  }
  return false;
}

/** Robustesse indicative d'un mot de passe (0 à 4) ; la règle qui fait foi est celle du serveur. */
export function passwordStrength(p: string): number {
  let score = 0;
  if (p.length >= 12) score++;
  if (/[A-Za-z]/.test(p) && /\d/.test(p)) score++;
  if (/[^A-Za-z0-9]/.test(p)) score++;
  if (p.length >= 16) score++;
  return p.length === 0 ? 0 : score;
}
