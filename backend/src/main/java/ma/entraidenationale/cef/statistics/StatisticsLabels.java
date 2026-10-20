package ma.entraidenationale.cef.statistics;

import ma.entraidenationale.cef.beneficiary.domain.StatCategory;

/** Libellés du tableau officiel (exports Excel / PDF) en arabe (mise en page officielle, défaut) ou en français. */
public enum StatisticsLabels {

    AR(true,
            "حصيلة المستفيدين من برنامج مراكز التربية والتكوين", "الموسم التكويني",
            "الجهة", "الإقليم", "الشعبة", "الفئة المستفيدة",
            "المسجلين", "المغادرين", "الخريجين", "الراسبين", "الغائبين",
            "ذكور", "إناث", "المجموع", "النسبة المئوية للنجاح",
            "مجموع الإقليم", "مجموع الجهة", "المجموع العام",
            "المهاجرين", "الأشخاص في وضعية إعاقة", "فئة أخرى", "باقي المستفيدين",
            "الإحصائيات",
            "يُحتسب كل مستفيد في فئة واحدة حسب الأولوية: المهاجرين ثم الأشخاص في وضعية إعاقة ثم فئة أخرى. "
                    + "باقي المستفيدين = إجمالي الشعبة − الفئات الثلاث الأولى. الغائبون لا يُعتبرون راسبين. "
                    + "النسبة المئوية للنجاح = الخريجون ÷ (الخريجون + الراسبون)."),

    FR(false,
            "Bilan des bénéficiaires du programme des Centres d'Éducation et de Formation", "Saison de formation",
            "Région", "Province", "Filière", "Catégorie bénéficiaire",
            "Inscrits", "Partis", "Diplômés", "Recalés", "Absents",
            "Garçons", "Filles", "Total", "Taux de réussite",
            "Total province", "Total région", "Total général",
            "Migrants", "Personnes en situation de handicap", "Autre catégorie", "Autres bénéficiaires",
            "Statistiques",
            "Chaque bénéficiaire n'est compté que dans une catégorie, selon la priorité : migrants, puis handicap, puis autre. "
                    + "Autres bénéficiaires = total de la filière − les trois premières catégories. Les absents ne sont pas "
                    + "comptés parmi les recalés. Taux de réussite = diplômés ÷ (diplômés + recalés).");

    public final boolean rtl;
    public final String title, season, region, province, filiere, category;
    public final String registered, departed, graduates, failed, absent;
    public final String male, female, total, passRate;
    public final String provinceTotal, regionTotal, grandTotal;
    private final String migrant, disabled, other, remaining;
    public final String sheetName, note;

    StatisticsLabels(boolean rtl, String title, String season, String region, String province, String filiere, String category,
                     String registered, String departed, String graduates, String failed, String absent,
                     String male, String female, String total, String passRate,
                     String provinceTotal, String regionTotal, String grandTotal,
                     String migrant, String disabled, String other, String remaining, String sheetName, String note) {
        this.rtl = rtl;
        this.title = title;
        this.season = season;
        this.region = region;
        this.province = province;
        this.filiere = filiere;
        this.category = category;
        this.registered = registered;
        this.departed = departed;
        this.graduates = graduates;
        this.failed = failed;
        this.absent = absent;
        this.male = male;
        this.female = female;
        this.total = total;
        this.passRate = passRate;
        this.provinceTotal = provinceTotal;
        this.regionTotal = regionTotal;
        this.grandTotal = grandTotal;
        this.migrant = migrant;
        this.disabled = disabled;
        this.other = other;
        this.remaining = remaining;
        this.sheetName = sheetName;
        this.note = note;
    }

    public String category(StatCategory c, String specification) {
        return switch (c) {
            case MIGRANT -> migrant;
            case DISABLED -> disabled;
            case OTHER -> specification == null || specification.isBlank() ? other : other + " (" + specification + ")";
            case REMAINING -> remaining;
        };
    }

    public static StatisticsLabels of(String lang) {
        return "fr".equalsIgnoreCase(lang) ? FR : AR;
    }
}
