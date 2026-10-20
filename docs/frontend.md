# Frontend (Next.js)

Next.js 16 (App Router), TypeScript, Tailwind 4, TanStack Query / Table, react-hook-form. Arabe (RTL) par défaut,
français en un clic ; police IBM Plex Sans Arabic embarquée (aucune requête vers un CDN).

## Architecture

| Dossier | Rôle |
|---|---|
| `src/lib/api.ts` | client HTTP : cookies httpOnly, en-tête `X-XSRF-TOKEN`, un rafraîchissement de session sur 401 puis rejeu |
| `src/lib/auth.tsx` | session (`/auth/me`), connexion, déconnexion ; retour à `/login` si la session est perdue |
| `src/lib/i18n/` | `messages/*.ts` (arabe = référence ; le français doit avoir **exactement** les mêmes clés, vérifié par le compilateur et par un test), `useI18n()` : `t`, `label` (énumérations), `name`, `date`, `num` |
| `src/lib/errors.ts` | code d'erreur du backend → message traduit (`err.<code>`), sinon message du serveur |
| `src/components/ui/` | boutons, champs, tableau paginé / triable côté serveur, modales, notifications |
| `src/app/(app)/` | pages protégées ; `login/` est publique |

Le navigateur ne parle qu'à Next.js : `/api/*` est relayé vers le backend (`BACKEND_URL`, lu au build). Les cookies
(`cef_access`, `cef_refresh`, `XSRF-TOKEN`) appartiennent donc à une seule origine. Le backend doit lister l'origine
publique du frontend dans `CORS_ALLOWED_ORIGINS` (il vérifie l'en-tête `Origin` des écritures).

## Écrans

| Route | Profils | Contenu |
|---|---|---|
| `/login` | public | connexion (logo, intro, bascule de langue) |
| `/` | tous | tableau de bord : inscrits, diplômés, partis, taux de réussite, par filière et catégorie, dossiers à compléter |
| `/beneficiaries`, `/new`, `/[id]`, `/[id]/edit`, `/[id]/bulletin/[enrollmentId]` | tous / écriture : admin, provincial | liste filtrable, fiche (situation sociale à choix multiple, situation familiale en dernier), inscriptions, saisie des examens (normale et rattrapage, aperçu des moyennes), départ, clôture, diplôme, bulletin imprimable, historique d'audit |
| `/centers`, `/new`, `/[id]`, `/[id]/edit` | tous / écriture : admin, provincial | liste, fiche (étages, 13 espaces, situation foncière, mode de gestion, associations partenaires), filières du centre (admin) |
| `/employees`, `/new`, `/[id]`, `/[id]/edit` | tous / écriture : admin, provincial | effectifs, fiche selon la صفة et la وضعية إدارية, mutations, enseignement, prestations des formateurs invités |
| `/filieres`, `/[id]` | tous / écriture : admin | catalogue, modules (coefficients, ordre) |
| `/requests`, `/new`, `/[id]`, `/[id]/edit` | tous / selon l'étape | demandes de filière : 8 PDF (≤ 20 Mo), dépôt, validation régionale, approbation, renvoi, rejet |
| `/statistics` | tous | tableau région → province → filière → catégorie, export Excel et PDF |
| `/seasons` | admin | saisons de formation |
| `/users` | admin | comptes : création, modification, activation, réinitialisation du mot de passe, déverrouillage |
| `/account` | tous | mon compte : **changer mon mot de passe**, langue |

Les boutons affichés suivent les droits (`availableActions` pour les demandes) ; le backend reste l'autorité.

## Ajouter un texte

1. Ajouter la clé en arabe dans le bon fichier de `messages/`, puis en français (le typage l'impose).
2. `t("ma.cle", { variable })` ; énumération : `label("EnrollmentStatus", valeur)` avec la clé `enum.EnrollmentStatus.VALEUR`.
3. Nouveau code d'erreur du backend : clé `err.<code>` dans `messages/errors.ts`.

## Vérifier

```bash
npm run typecheck && npm run lint && npm test && npm run build
```
