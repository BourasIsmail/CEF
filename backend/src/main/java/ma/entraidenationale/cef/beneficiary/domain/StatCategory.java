package ma.entraidenationale.cef.beneficiary.domain;

/**
 * الفئة المستفيدة du rapport statistique. Un bénéficiaire peut cumuler plusieurs situations sociales mais n'est
 * compté que dans UNE catégorie, selon la priorité : migrant > handicap > autre > reste.
 * Cette règle est aussi appliquée par la colonne générée {@code beneficiary.stat_category} (V3) ; un test
 * vérifie que les deux implémentations concordent.
 */
public enum StatCategory {
    MIGRANT, DISABLED, OTHER, REMAINING;

    public static StatCategory of(MigrationStatus migration, DisabilityType disability, String otherSocialSituation) {
        if (migration != null) {
            return MIGRANT;
        }
        if (disability != null) {
            return DISABLED;
        }
        if (otherSocialSituation != null) {
            return OTHER;
        }
        return REMAINING;
    }
}
