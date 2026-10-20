package ma.entraidenationale.cef.enrollment.domain;

import java.time.LocalDate;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.entraidenationale.cef.beneficiary.domain.Beneficiary;
import ma.entraidenationale.cef.common.AuditedEntity;
import ma.entraidenationale.cef.organization.domain.Center;
import ma.entraidenationale.cef.training.domain.Filiere;
import ma.entraidenationale.cef.training.domain.TrainingSeason;

/** Inscription d'un bénéficiaire à une filière pour une saison (une seule par saison). */
@Entity
@Table(name = "enrollment")
@Getter
@Setter
@NoArgsConstructor
public class Enrollment extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_id", nullable = false, updatable = false)
    private Beneficiary beneficiary;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id", nullable = false, updatable = false)
    private Center center;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filiere_id", nullable = false)
    private Filiere filiere;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false, updatable = false)
    private TrainingSeason season;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** تاريخ الامتحان. */
    @Column(name = "exam_date")
    private LocalDate examDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private EnrollmentStatus status = EnrollmentStatus.ENROLLED;

    @Column(name = "departure_date")
    private LocalDate departureDate;

    @Column(name = "departure_reason", length = 500)
    private String departureReason;

    @OneToMany(mappedBy = "enrollment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sessionType")
    private List<ExamSession> sessions = new ArrayList<>();

    @OneToOne(mappedBy = "enrollment", cascade = CascadeType.ALL, orphanRemoval = true)
    private Diploma diploma;

    public ExamSession session(SessionType type) {
        return sessions.stream().filter(s -> s.getSessionType() == type).findFirst().orElse(null);
    }
}
