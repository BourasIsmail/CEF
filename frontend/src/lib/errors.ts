import { ApiError } from "./api";
import type { MessageKey } from "./i18n/messages";
import { messages } from "./i18n/messages";

/** Clé de message pour une erreur d'API : par code métier, sinon par statut HTTP. */
export function errorKey(e: unknown): MessageKey {
  if (e instanceof ApiError) {
    const byCode = `err.${e.code}` as MessageKey;
    if (e.code && byCode in messages.ar) return byCode;
    if (e.status === 403) return "err.forbidden";
    if (e.status === 404) return "err.not_found";
    return "err.generic";
  }
  if (e instanceof TypeError) return "err.network";
  return "err.generic";
}
