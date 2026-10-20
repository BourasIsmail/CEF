package ma.entraidenationale.cef.enrollment.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.entraidenationale.cef.common.AuditedEntity;

/**
 * Session d'examen d'une inscription. Les moyennes, le résultat et la mention sont CALCULÉS par le serveur
 * (voir {@code ExamCalculator}) et stockés pour les statistiques.
 */
@Entity
@Table(name = "exam_session")
@Getter
@Setter
@NoArgsConstructor
public class ExamSession extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false, updatable = false)
    private Enrollment enrollment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "session_type", nullable = false, length = 10, updatable = false)
    private SessionType sessionType;

    /** رقم الامتحان. */
    @Column(name = "exam_number", length = 30)
    private String examNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "final_exam_status", nullable = false, length = 10)
    private ExamStatus finalExamStatus = ExamStatus.NOT_TAKEN;

    @Column(name = "final_exam_grade", precision = 5, scale = 2)
    private BigDecimal finalExamGrade;

    @Column(name = "modules_average", precision = 5, scale = 2)
    private BigDecimal modulesAverage;

    @Column(name = "overall_average", precision = 5, scale = 2)
    private BigDecimal overallAverage;

    @Column(name = "pass_threshold", nullable = false, precision = 4, scale = 2)
    private BigDecimal passThreshold = new BigDecimal("10.00");

    @Column(name = "exceptional_pass", nullable = false)
    private boolean exceptionalPass;

    @Column(name = "exceptional_pass_reason", length = 500)
    private String exceptionalPassReason;

    @Column(name = "exceptional_pass_by", length = 100)
    private String exceptionalPassBy;

    @Column
    private Boolean passed;

    @Column(length = 30)
    private String mention;

    @OneToMany(mappedBy = "examSession", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ModuleGrade> grades = new ArrayList<>();
}
