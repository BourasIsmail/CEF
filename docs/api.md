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

---

# API — bénéficiaires, inscriptions, examens, diplôme, audit

## Droits et périmètre

| | Provincial | Régional | Admin |
|---|---|---|---|
| Lire fiches, inscriptions, bulletins, historique | centres de sa province | centres de sa région | tout |
| Créer / modifier / supprimer fiches, inscriptions, notes, diplômes | **sa province** | ✗ (lecture seule) | tout |
| Journal d'audit global (`/api/audit`) | ✗ | ✗ | ✔ |

Une fiche dont le centre est hors périmètre répond **404**. La liste applique le périmètre **avant** les filtres.

## Bénéficiaires

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/beneficiaries` | liste paginée (SQL natif, une requête + COUNT). Filtres : `search` (noms AR/FR, n° d'inscription, pièce d'identité, téléphone), `regionId`, `provinceId`, `centerId`, `filiereId`, `seasonId` (défaut : saison courante), `status`, `category`, `gender`, `vulnerable`, `withoutEnrollment`. Tris : `rank`, `registrationNumber`, `registrationDate`, `nameAr`, `nameFr`, `birthDate`, `center`, `filiere`, `status`, `category` (tout autre critère : 400) |
| GET | `/beneficiaries/{id}` | fiche complète : âge calculé, catégorie statistique, inscriptions (avec résumé des sessions et diplôme) |
| GET | `/beneficiaries/{id}/history` | historique d'audit de la fiche (fiche, inscriptions, notes, diplôme) |
| POST | `/beneficiaries` | création ; `enrollment` facultatif pour inscrire aussitôt |
| PUT | `/beneficiaries/{id}` | modification (le centre est immuable) |
| DELETE | `/beneficiaries/{id}` | refusé (409) s'il existe des résultats d'examen ou un diplôme |

- **Numérotation** : `rankNumber` (الرقم الترتيبي, unique par centre) = max + 1 si absent ; `registrationNumber` généré
  `{année}-{nnnn}` si absent, unique par centre.
- **Situation sociale à choix multiple** : `vulnerable`, `disabilityType`, `migrationStatus`, `otherSocialSituation`.
  Les valeurs « autre » exigent une précision ; les précisions sans objet sont effacées.
- **Catégorie statistique** (une seule par personne) : migrant > handicap > autre > reste. La précarité seule = reste.

## Inscriptions et cycle de vie

| Méthode | Chemin | Description |
|---|---|---|
| POST | `/beneficiaries/{id}/enrollments` | inscription dans le centre du bénéficiaire ; saison courante par défaut ; filière assignée au centre ; une seule par saison |
| GET/PUT/DELETE | `/enrollments/{id}` | lecture / dates et filière (si aucun résultat) / suppression (si aucun résultat) |
| POST | `/enrollments/{id}/depart` | **parti** avant l'examen final : `departureDate` + `reason` obligatoires |
| POST | `/enrollments/{id}/reinstate` | annule un départ |
| POST | `/enrollments/{id}/finalize` | clôture sans rattrapage : session normale échouée → `FAILED`, absente → `ABSENT` |

Statuts : `ENROLLED` → `DEPARTED` | `GRADUATED` | `FAILED` | `ABSENT`. Un bénéficiaire **reste inscrit** tant que le
rattrapage est possible ; `GRADUATED`, `FAILED`, `ABSENT` sont **déduits des sessions** par le serveur (la session de
rattrapage fait foi si elle existe).

## Examens, bulletin, diplôme

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/enrollments/{id}/bulletin` | بيان النقط : modules de la filière + sessions (notes par module, moyennes, issue, mention) |
| PUT | `/enrollments/{id}/sessions/{NORMAL\|MAKEUP}` | saisie d'une session (remplace la précédente) |
| DELETE | `/enrollments/{id}/sessions/{type}` | suppression (le rattrapage d'abord) |
| PUT/DELETE | `/enrollments/{id}/diploma` | diplôme (diplômés uniquement), numéro de série unique |

Règles de calcul (le client envoie les notes, le serveur calcule) :

- moyenne des modules = Σ(note × coefficient) / Σ coefficients, sur **tous** les modules de la filière ;
- **moyenne générale = (moyenne des modules + note de l'examen final) / 2**, sans arrondi intermédiaire ;
- réussite si la moyenne générale (arrondie à 2 décimales) atteint le **seuil** — 10 par défaut, modifiable à la saisie
  (`passThreshold`) ;
- **réussite exceptionnelle** (`exceptionalPass`) : seulement pour une session complète sous le seuil, avec justification ;
  tracée (auteur) ; pas de mention ;
- **absent** à un module ou à l'examen final ⇒ issue `ABSENT` (jamais « recalé ») ; saisie incomplète ⇒ `INCOMPLETE`,
  aucune moyenne ;
- **rattrapage** : possible après une session normale échouée ou absente ; il reprend **tous** les examens ; sa note
  **remplace** la précédente (aucun plafond) ;
- mentions : échelle provisoire (≥ 10 مقبول, ≥ 12 مستحسن, ≥ 14 جيد, ≥ 16 جيد جدا) — classe `MentionScale`.

Un diplôme émis verrouille le résultat (409 `exam.diploma_issued`) jusqu'à sa suppression.

## Audit

Chaque création / modification / suppression de fiche, inscription, session d'examen et diplôme est tracée (qui, quand,
IP, différences champ par champ `{champ: {old, new}}`), dans la transaction de l'opération. Une modification sans
changement ne laisse pas de trace. L'historique est rattaché à la fiche du bénéficiaire.

---

# API — statistiques

Tous les profils (chacun dans son périmètre ; un compte régional / provincial ne voit que sa région / province).

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/statistics/report` | tableau hiérarchique région → province → filière → catégorie, totaux à chaque niveau |
| GET | `/statistics/report.xlsx` | export Excel (mise en page officielle, RTL) |
| GET | `/statistics/report.pdf` | export PDF (A4 paysage, police arabe embarquée) |
| GET | `/statistics/dashboard` | indicateurs du tableau de bord : totaux, en cours, par filière, par catégorie, dossiers à compléter |

Paramètres communs : `seasonId` (défaut : saison courante), `regionId`, `provinceId`, `centerId`, `filiereId` ;
exports : `lang=ar` (défaut) ou `fr`.

Chaque ligne contient `registered`, `departed`, `graduates`, `failed`, `absent` (chacun `{male, female, total}`) et
`passRatePercent` (diplômés ÷ (diplômés + recalés), 1 décimale, `null` si aucun). Les catégories sont toujours
`MIGRANT`, `DISABLED`, `OTHER` (avec `specification` : les précisions saisies), `REMAINING`. Règles complètes :
[statistics-rules.md](statistics-rules.md).

---

# API — ressources humaines

Lecture dans le périmètre (un agent hors périmètre ⇒ 404) ; écriture `ADMIN` et `PROVINCIAL` de la province de l'agent
(`REGIONAL` : lecture seule). Un cadre sans centre (coordonnateur pédagogique, administratif provincial) reste visible
de sa province et de sa région.

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/employees` | liste paginée : `search` (nom, n° d'ordre, pièce d'identité, téléphone), `regionId`, `provinceId`, `centerId`, `type`, `administrativeStatus`, `activityStatus`, `gender`, `withoutCenter` ; tris `nameAr`, `nameFr`, `type`, `administrativeStatus`, `center`, `rankNumber`, `hireDate`, `activityStatus` |
| GET | `/employees/summary` | effectifs calculés (hors retraités) : total, sexe, par صفة, par وضعية إدارية ; filtres région / province / centre |
| GET/POST | `/employees`, `/employees/{id}` (GET, PUT, DELETE) | fiche complète avec affectations, enseignement et prestations |
| GET | `/employees/{id}/history` | journal d'audit de la fiche et de ses éléments |
| POST | `/employees/{id}/transfers` | mutation : clôt l'affectation en cours la veille de `fromDate`, en ouvre une nouvelle |
| POST/DELETE | `/employees/{id}/teachings[/{tid}]` | هيئة التكوين : filière entière ou un module, par saison |
| POST/PUT/DELETE | `/employees/{id}/guest-contracts[/{cid}]` | prestation d'un formateur invité (tarif horaire, mois, heures…) |

Règles :
- **صفة** : le centre est obligatoire pour cadre pédagogique, directeur et formateur invité ; interdit pour le
  coordonnateur pédagogique ; facultatif pour l'administratif (alors la province est celle du compte provincial, ou
  `provincialDirectorateId` pour l'administrateur). Direction provinciale non modifiable.
- **الوضعية إدارية** : `OTHER_SECTOR` ⇒ `otherSectorText` obligatoire ; `FOREIGN_VOLUNTEER` ⇒ `delegatingBody` obligatoire ;
  `ENTRAIDE_NATIONALE` ⇒ numéro d'ordre (الرقم الترتيبي) obligatoire et unique, avec cadre et échelle ; ces trois champs
  sont ignorés pour les autres statuts.
- **Centre** : se change uniquement par mutation (`422 employee.use_transfer` sur un PUT), dans la même province, avec
  historique contigu des affectations (la première affectation est ouverte à la création, à la date d'embauche).
- **Enseignement** : cadres pédagogiques et formateurs invités seulement ; la filière doit être proposée par le centre ;
  un module doit appartenir à la filière ; on ne cumule pas « toute la filière » et des modules de la même filière.
- **Prestations** : réservées au formateur invité, une par saison et filière ; `estimatedAnnualCostMad` = tarif horaire
  × heures par mois × mois par an.
- Suppression d'un agent : ses affectations, enseignements et prestations partent avec lui ; tout est tracé dans l'audit.
