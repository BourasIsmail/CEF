package ma.entraidenationale.cef.enrollment.service;

import ma.entraidenationale.cef.enrollment.domain.ExamResult;
import ma.entraidenationale.cef.enrollment.domain.ExamSession;
import ma.entraidenationale.cef.enrollment.domain.ExamStatus;
import ma.entraidenationale.cef.enrollment.domain.ModuleGrade;

/** Issue d'une session à partir de ce qui est stocké (colonne {@code passed}, absences). */
public final class ExamSessionResult {

    private ExamSessionResult() {
    }

    public static ExamResult of(ExamSession s) {
        if (s.getPassed() != null) {
            return s.getPassed() ? ExamResult.PASSED : ExamResult.FAILED;
        }
        boolean absent = s.getFinalExamStatus() == ExamStatus.ABSENT
                || s.getGrades().stream().anyMatch((ModuleGrade g) -> g.getExamStatus() == ExamStatus.ABSENT);
        return absent ? ExamResult.ABSENT : ExamResult.INCOMPLETE;
    }
}
