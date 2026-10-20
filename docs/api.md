# API — organisation, filières, modules

Base : `/api`. JSON (UTF-8). Documentation interactive (profil `dev`) : `/swagger-ui/index.html`.
Erreurs au format *problem details* avec un `code` stable (`center.commune_not_in_province`, …) que le frontend
traduit en arabe / français ; les erreurs de validation ajoutent `errors: { champ: message }`.

| Statut | Signification |
|---|---|
| 400 | requête mal formée ou champs invalides (`validation_failed`, `invalid_sort`) |
| 401 | non authentifié |
| 403 | profil ou périmètre insuffisant, ou jeton CSRF manquant |
| 404 | introuvable **ou hors périmètre** (on ne révèle pas l'existence) |
| 409 | doublon ou ressource encore utilisée (`center.in_use`, `filiere.in_use`, `module.in_use`…) |
| 422 | règle métier non respectée |

**CSRF** : le cookie `XSRF-TOKEN` est lisible par le JavaScript ; chaque écriture (POST/PUT/PATCH/DELETE) renvoie
sa valeur dans l'en-tête `X-XSRF-TOKEN`.

## Droits

| | Provincial | Régional | Admin |
|---|---|---|---|
| Lire régions / provinces / communes / centres | son périmètre | sa région | tout |
| Créer / modifier / désactiver / supprimer un centre | **sa province uniquement** | ✗ (lecture) | tout |
| Annuaire des associations | lire + créer / modifier | lire | tout |
| Provinces et communes | lire | lire | créer / modifier |
| Catalogue des filières et modules | lire | lire | créer / modifier / supprimer |
| Saisons de formation | lire | lire | créer / modifier / saison courante |
| Assigner une filière à un centre | lire | lire | assigner / retirer |

Un compte provincial ne choisit pas sa province : elle est déduite de son compte (la requête ne peut pas viser une
autre province : 403).

## Organisation

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/regions` | régions visibles |
| GET | `/provinces?regionId=` | provinces visibles |
| GET | `/communes?provinceId=` | communes d'une province visible |
| POST/PUT | `/provinces`, `/provinces/{id}` | ADMIN |
| POST/PUT | `/communes`, `/communes/{id}` | ADMIN |
| GET | `/space-types`, `/partner-types` | référentiels (المرافق, الشركاء) |
| GET/POST/PUT | `/associations`, `/associations/{id}` | annuaire (`?q=`) |

## Centres

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/centers` | liste paginée : `search` (code, nom, commune, association), `regionId`, `provinceId`, `communeId`, `areaType`, `managementMode`, `landStatus`, `filiereId`, `active`, `page`, `size` (≤ 100), `sort` |
| GET | `/centers/{id}` | fiche complète : étages et surface totale, espaces, associations et partenaires, compteurs (filières, bénéficiaires, personnel) |
| POST | `/centers` | création ; `code` facultatif (généré `{province}-{nn}`) |
| PUT | `/centers/{id}` | remplace étages, espaces et associations (mise à jour différentielle) |
| PATCH | `/centers/{id}/active?value=` | (dés)activation |
| DELETE | `/centers/{id}` | refusé (409) si le centre a des bénéficiaires, employés, filières ou demandes |

Règles : commune de la même province ; « autre » bâtiment ⇒ nombre d'étages **et** surface de chaque étage ;
surface totale calculée ; situation foncière « mis à disposition » / « autre secteur » ⇒ propriétaire à préciser ;
espace « autre » ⇒ précision ; mesure « autres » ⇒ texte ; partenaires choisis dans `partner-types`.

## Filières, modules, saisons

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/filieres` | liste (`q`, `active`, `programId`) avec nb de modules, Σ coefficients, Σ heures, centres du périmètre |
| GET | `/filieres/{id}` | détail : modules ordonnés + centres du périmètre |
| POST/PUT/DELETE | `/filieres`, `/filieres/{id}` | ADMIN ; suppression refusée si utilisée |
| PATCH | `/filieres/{id}/active?value=` | ADMIN |
| GET/POST | `/filieres/{id}/modules` | ajout en dernière position |
| PUT/DELETE | `/filieres/{id}/modules/{moduleId}` | modification ; suppression refusée si des notes existent |
| PUT | `/filieres/{id}/modules/order` | `{moduleIds: [...]}` nouvel ordre complet |
| GET | `/programs` | programmes (CEF par défaut) |
| GET | `/seasons`, `/seasons/current` | saisons (الموسم التكويني) |
| POST/PUT | `/seasons`, `/seasons/{id}` | ADMIN ; libellé `2025-2026` (années consécutives) |
| PUT | `/seasons/{id}/current` | ADMIN ; une seule saison courante |
| GET | `/centers/{id}/filieres` | filières du centre |
| PUT | `/centers/{id}/filieres` | ADMIN : remplace la liste |
| PUT/DELETE | `/centers/{id}/filieres/{filiereId}` | ADMIN : assigner / retirer (refusé s'il y a des inscriptions) |

## Périmètre dans le code

- `UserScope` (security) : ce que voit un utilisateur (région, province, centre).
- Listes JPA : `CenterSpecifications` applique le périmètre **avant** les filtres du client.
- Requêtes SQL d'agrégation : `ScopeSql.centerPredicate(...)` à ajouter à toute requête native qui lit des données
  de centre (utilisé par les statistiques à l'étape suivante).
- Les principaux sont fabriqués par l'authentification JWT (étape sécurité) ; les tests utilisent `AppUserPrincipal`.
