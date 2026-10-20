package ma.entraidenationale.cef.filiererequest.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.entraidenationale.cef.common.TimestampedEntity;
import ma.entraidenationale.cef.organization.domain.Center;

/** طلب إحداث / تعديل شعبة. */
@Entity
@Table(name = "filiere_request")
@Getter
@Setter
@NoArgsConstructor
public class FiliereChangeRequest extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", nullable = false, length = 30, updatable = false)
    private String requestNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "request_type", nullable = false, length = 10, updatable = false)
    private RequestType requestType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id", nullable = false, updatable = false)
    private Center center;

    @Column(name = "association_id")
    private Long associationId;

    @Column(name = "target_filiere_id", updatable = false)
    private Long targetFiliereId;

    @Column(name = "proposed_name_ar", nullable = false, length = 200)
    private String proposedNameAr;

    @Column(name = "proposed_name_fr", length = 200)
    private String proposedNameFr;

    @Column(name = "duration_months")
    private Short durationMonths;

    @Column(name = "expected_beneficiaries")
    private Integer expectedBeneficiaries;

    @Column(name = "modification_summary", columnDefinition = "TEXT")
    private String modificationSummary;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private Long createdByUserId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "regional_decision_by")
    private Long regionalDecisionBy;

    @Column(name = "regional_decision_at")
    private Instant regionalDecisionAt;

    @Column(name = "regional_comment", columnDefinition = "TEXT")
    private String regionalComment;

    @Column(name = "national_decision_by")
    private Long nationalDecisionBy;

    @Column(name = "national_decision_at")
    private Instant nationalDecisionAt;

    @Column(name = "national_comment", columnDefinition = "TEXT")
    private String nationalComment;

    @Column(name = "resulting_filiere_id")
    private Long resultingFiliereId;
}
