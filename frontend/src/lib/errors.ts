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

/**
 * Texte d'une erreur d'API : traduction du code métier si elle existe, sinon le message du serveur (français)
 * pour une erreur applicative (4xx), sinon un message générique.
 */
export function errorText(e: unknown, t: (k: MessageKey) => string): string {
  const key = errorKey(e);
  if (key === "err.generic" && e instanceof ApiError && e.status >= 400 && e.status < 500 && e.message) return e.message;
  return t(key);
}
