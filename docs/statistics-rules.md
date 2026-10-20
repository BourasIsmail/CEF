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

## Implémentation (étape « statistiques »)

Une seule requête d'agrégation, sans boucle en mémoire :

```sql
SELECT r.id, p.id, f.id, b.stat_category,
       SUM(b.gender = 'M')                              AS registered_m,
       SUM(b.gender = 'F')                              AS registered_f,
       SUM(e.status = 'DEPARTED' AND b.gender = 'M')    AS departed_m,
       -- … idem F, GRADUATED, FAILED, ABSENT
FROM enrollment e
JOIN beneficiary b ON b.id = e.beneficiary_id
JOIN center c ON c.id = e.center_id
JOIN provincial_directorate p ON p.id = c.provincial_directorate_id
JOIN regional_directorate r ON r.id = p.regional_directorate_id
JOIN filiere f ON f.id = e.filiere_id
WHERE e.season_id = :season          -- + filtres région / province / centre / filière + périmètre de l'utilisateur
GROUP BY r.id, p.id, f.id, b.stat_category WITH ROLLUP;
```

Index utilisé : `idx_enrollment_stats (season_id, filiere_id, center_id, status)`.
Les lignes de catégorie sans bénéficiaire (compteurs à 0) sont complétées par un `CROSS JOIN` avec la liste des 4 catégories.
