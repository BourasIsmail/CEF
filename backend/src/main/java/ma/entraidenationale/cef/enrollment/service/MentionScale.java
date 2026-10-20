package ma.entraidenationale.cef.enrollment.service;

import java.math.BigDecimal;

/**
 * Échelle des mentions (الميزة). HYPOTHÈSE À CONFIRMER : échelle marocaine usuelle
 * (passable ≥ 10, assez bien ≥ 12, bien ≥ 14, très bien ≥ 16). Changer l'échelle ne touche que cette classe.
 */
public final class MentionScale {

    private MentionScale() {
    }

    public static String of(BigDecimal overallAverage) {
        double v = overallAverage.doubleValue();
        if (v >= 16) {
            return "جيد جدا";
        }
        if (v >= 14) {
            return "جيد";
        }
        if (v >= 12) {
            return "مستحسن";
        }
        if (v >= 10) {
            return "مقبول";
        }
        return null;
    }
}
