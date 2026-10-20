# Règles du rapport statistique

Regroupement : **Région → Province → Filière → Catégorie**, filtré par saison, région, province, centre, filière.
Pour chaque ligne : **Inscrits / Partis / Diplômés / Recalés / Absents** × (Garçons, Filles, Total), puis le pourcentage de réussite.

## Catégories (الفئة المستفيدة)

La situation sociale est à choix multiple : un bénéficiaire peut cumuler plusieurs situations.
Pour que chaque personne soit comptée **une seule fois**, on applique une priorité (colonne générée `beneficiary.stat_category`) :

| Priorité | Catégorie | Condition |
|---|---|---|
| 1 | المهاجرين (Migrants) | `migration_status` renseigné |
| 2 | الأشخاص في وضعية إعاقة (Handicap) | `disability_type` renseigné |
| 3 | فئة أخرى (Autre) | `other_social_situation_text` renseigné |
| 4 | باقي المستفيدين (Reste) | tous les autres, y compris la précarité seule |

Conséquence : **Reste = total de la filière − (Migrants + Handicap + Autre)** exactement, par construction
(test `remainingEqualsFiliereTotalMinusOtherCategories`).

> **À confirmer** : cet ordre (migrant > handicap > autre) suit celui du tableau officiel. Changer la règle
> = une migration qui recrée la colonne générée ; aucune donnée à retraiter.

## Colonnes

| Colonne | Définition |
|---|---|
| Inscrits | toutes les inscriptions de la saison, quel que soit leur statut final |
| Partis | `status = 'DEPARTED'` (avant l'examen final) |
| Diplômés | `status = 'GRADUATED'` |
| Recalés | `status = 'FAILED'` |
| Absents | `status = 'ABSENT'` : **jamais** comptés parmi les recalés |
| % de réussite | Diplômés ÷ (Diplômés + Recalés) — absents et partis exclus *(à confirmer)* |

Un bénéficiaire en rattrapage reste `ENROLLED` : il n'est ni diplômé, ni recalé, ni absent tant que la session
de rattrapage n'a pas eu lieu.

## Implémentation

`StatisticsService` (package `statistics`) :

1. une requête de **squelette** liste les filières proposées dans chaque province (pour afficher aussi les lignes à zéro) ;
2. **une seule requête d'agrégation** (`SUM(CASE …)` groupée par province, filière et catégorie, jointure
   inscription ⋈ bénéficiaire ⋈ centre ⋈ province) fournit tous les compteurs garçons / filles ;
3. le code Java assemble les lignes déjà agrégées et additionne les sous-totaux (filière, province, région, général).

Le périmètre de l'utilisateur (`ScopeSql`) est appliqué **avant** les filtres : un provincial ne peut pas élargir sa vue
en passant un autre `provinceId` / `centerId`. Index utile : `idx_enrollment_stats (season_id, filiere_id, center_id, status)`.

Les **absents** forment un groupe de colonnes à part (« الغائبين ») ; le pourcentage de réussite les ignore.

## Exports

- **Excel** (`/api/statistics/report.xlsx`) : feuille de droite à gauche en arabe (`lang=fr` : gauche à droite),
  en-têtes fusionnés sur 2 lignes, cellules région / province / filière fusionnées, totaux par province et par région
  (et général si plusieurs régions), pourcentage stocké en nombre (format `0.0%`), note de calcul en pied de tableau,
  mise en page A4 paysage.
- **PDF** (`/api/statistics/report.pdf`) : même tableau, A4 paysage, police **Noto Naskh Arabic** embarquée
  (licence SIL OFL, `resources/fonts`) ; l'en-tête est répété à chaque page.
  Note technique : les tableaux RTL d'OpenPDF échouent avec des cellules fusionnées ; le tableau est donc construit de
  gauche à droite avec les colonnes émises en ordre inverse, le texte restant en sens RTL.
