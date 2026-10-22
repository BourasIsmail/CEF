package ma.entraidenationale.cef.organization.domain;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.entraidenationale.cef.common.AuditedEntity;

/** معطيات الجمعية الشريكة بالمركز (convention, évaluation, mesures, partenaires). */
@Entity
@Table(name = "center_association")
@Getter
@Setter
@NoArgsConstructor
public class CenterAssociation extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id", nullable = false)
    private Center center;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false)
    private Association association;

    @Column(name = "agreement_subject", length = 300)
    private String agreementSubject;

    @Column(name = "agreement_date")
    private LocalDate agreementDate;

    @Column(name = "agreement_reference", length = 100)
    private String agreementReference;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "partnership_evaluation", length = 10)
    private PartnershipEvaluation partnershipEvaluation;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "measure_taken", length = 20)
    private PartnershipMeasure measureTaken;

    @Column(name = "measure_other_text", length = 255)
    private String measureOtherText;

    /** Précision demandée par les partenaires « مع تحديده ». */
    @Column(name = "partner_specification", length = 255)
    private String partnerSpecification;

    @ManyToMany
    @JoinTable(name = "center_association_partner",
            joinColumns = @JoinColumn(name = "center_association_id"),
            inverseJoinColumns = @JoinColumn(name = "partner_type_id"))
    private Set<PartnerType> partnerTypes = new LinkedHashSet<>();
}
