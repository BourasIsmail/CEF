package ma.entraidenationale.cef.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import ma.entraidenationale.cef.enrollment.domain.ExamResult;
import ma.entraidenationale.cef.enrollment.domain.ExamStatus;
import ma.entraidenationale.cef.enrollment.service.ExamCalculator;
import ma.entraidenationale.cef.enrollment.service.ExamCalculator.ModuleInput;
import ma.entraidenationale.cef.enrollment.service.ExamCalculator.Outcome;

/** Règles de calcul : moyenne pondérée des modules, moyenne générale, seuil, absences, mentions. */
class ExamCalculatorTest {

    private static final BigDecimal TEN = new BigDecimal("10");
    /** Coefficients de la filière « خياطة » : 3, 3, 4, 1, 2, 1. */
    private static final int[] COEFFICIENTS = {3, 3, 4, 1, 2, 1};

    private static List<ModuleInput> modules(double... grades) {
        return java.util.stream.IntStream.range(0, COEFFICIENTS.length)
                .mapToObj(i -> new ModuleInput((long) i, BigDecimal.valueOf(COEFFICIENTS[i]),
                        i < grades.length ? ExamStatus.TAKEN : ExamStatus.NOT_TAKEN,
                        i < grades.length ? BigDecimal.valueOf(grades[i]) : null))
                .toList();
    }

    private static Outcome compute(List<ModuleInput> m, ExamStatus finalStatus, Double finalGrade, BigDecimal threshold, boolean exceptional) {
        return ExamCalculator.compute(m, finalStatus, finalGrade == null ? null : BigDecimal.valueOf(finalGrade), threshold, exceptional);
    }

    @Test
    void weightedModulesAverageAndOverallAverage() {
        // (12×3 + 9×3 + 8×4 + 14×1 + 11×2 + 13×1) / 14 = 10,2857…  puis (10,2857 + 8,5) / 2 = 9,3929…
        Outcome o = compute(modules(12, 9, 8, 14, 11, 13), ExamStatus.TAKEN, 8.5, TEN, false);
        assertThat(o.modulesAverage()).isEqualByComparingTo("10.29");
        assertThat(o.overallAverage()).isEqualByComparingTo("9.39");
        assertThat(o.result()).isEqualTo(ExamResult.FAILED);
        assertThat(o.passed()).isFalse();
        assertThat(o.mention()).isNull();
    }

    @Test
    void makeupExampleReachesTheThreshold() {
        Outcome o = compute(modules(13, 11, 10, 15, 12, 14), ExamStatus.TAKEN, 11.0, TEN, false);
        assertThat(o.modulesAverage()).isEqualByComparingTo("11.79");
        assertThat(o.overallAverage()).isEqualByComparingTo("11.39");
        assertThat(o.result()).isEqualTo(ExamResult.PASSED);
        assertThat(o.mention()).isEqualTo("مقبول");
    }

    @Test
    void intermediateValuesAreNotRoundedBeforeTheOverallAverage() {
        // Avec la moyenne des modules arrondie (10,29), on obtiendrait (10,29 + 8,5)/2 = 9,395 → 9,40 ; la bonne valeur est 9,39.
        Outcome o = compute(modules(12, 9, 8, 14, 11, 13), ExamStatus.TAKEN, 8.5, TEN, false);
        assertThat(o.overallAverage()).isEqualByComparingTo("9.39");
    }

    @Test
    void thresholdComparisonUsesTheDisplayedAverage() {
        // (10 + 9,99) / 2 = 9,995 → affiché 10,00 → réussite
        List<ModuleInput> one = List.of(new ModuleInput(1L, BigDecimal.ONE, ExamStatus.TAKEN, TEN));
        Outcome o = compute(one, ExamStatus.TAKEN, 9.99, TEN, false);
        assertThat(o.overallAverage()).isEqualByComparingTo("10.00");
        assertThat(o.result()).isEqualTo(ExamResult.PASSED);
    }

    @Test
    void thresholdCanBeLoweredByTheOperator() {
        Outcome o = compute(modules(12, 9, 8, 14, 11, 13), ExamStatus.TAKEN, 8.5, new BigDecimal("9"), false);
        assertThat(o.result()).isEqualTo(ExamResult.PASSED);
        assertThat(o.mention()).isNull(); // 9,39 < 10 : pas de mention
    }

    @Test
    void exceptionalPassIsAPassWithoutMention() {
        Outcome o = compute(modules(12, 9, 8, 14, 11, 13), ExamStatus.TAKEN, 8.5, TEN, true);
        assertThat(o.result()).isEqualTo(ExamResult.PASSED);
        assertThat(o.overallAverage()).isEqualByComparingTo("9.39");
        assertThat(o.mention()).isNull();
    }

    @Test
    void anAbsentModuleOrAbsentFinalIsAbsentNotFailed() {
        List<ModuleInput> withAbsent = modules(12, 9, 8, 14, 11, 13).stream()
                .map(m -> m.moduleId() == 2L ? new ModuleInput(2L, m.coefficient(), ExamStatus.ABSENT, null) : m).toList();
        assertThat(compute(withAbsent, ExamStatus.TAKEN, 15.0, TEN, false).result()).isEqualTo(ExamResult.ABSENT);
        Outcome finalAbsent = compute(modules(12, 9, 8, 14, 11, 13), ExamStatus.ABSENT, null, TEN, false);
        assertThat(finalAbsent.result()).isEqualTo(ExamResult.ABSENT);
        assertThat(finalAbsent.overallAverage()).isNull();
        assertThat(finalAbsent.passed()).isNull();
    }

    @Test
    void missingGradesMeanIncomplete() {
        Outcome partial = compute(modules(12, 9, 8), ExamStatus.TAKEN, 12.0, TEN, false);
        assertThat(partial.result()).isEqualTo(ExamResult.INCOMPLETE);
        assertThat(partial.modulesAverage()).isNull();
        assertThat(compute(modules(12, 9, 8, 14, 11, 13), ExamStatus.NOT_TAKEN, null, TEN, false).result())
                .isEqualTo(ExamResult.INCOMPLETE);
        assertThat(compute(List.of(), ExamStatus.TAKEN, 12.0, TEN, false).result()).isEqualTo(ExamResult.INCOMPLETE);
    }

    @Test
    void mentionScale() {
        assertThat(mentionOf(10.0)).isEqualTo("مقبول");
        assertThat(mentionOf(12.0)).isEqualTo("مستحسن");
        assertThat(mentionOf(14.0)).isEqualTo("جيد");
        assertThat(mentionOf(16.0)).isEqualTo("جيد جدا");
        assertThat(mentionOf(19.5)).isEqualTo("جيد جدا");
    }

    private static String mentionOf(double overall) {
        List<ModuleInput> one = List.of(new ModuleInput(1L, BigDecimal.ONE, ExamStatus.TAKEN, BigDecimal.valueOf(overall)));
        return compute(one, ExamStatus.TAKEN, overall, TEN, false).mention();
    }
}
