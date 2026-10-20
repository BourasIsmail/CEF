package ma.entraidenationale.cef.enrollment.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.entraidenationale.cef.common.AuditedEntity;

/** الدبلوم : date de remise et numéro de série. */
@Entity
@Table(name = "diploma")
@Getter
@Setter
@NoArgsConstructor
public class Diploma extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false, updatable = false)
    private Enrollment enrollment;

    /** تاريخ تسلم الدبلوم (vide = pas encore remis). */
    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(name = "serial_number", nullable = false, length = 40)
    private String serialNumber;

    @Column(name = "additional_info", length = 500)
    private String additionalInfo;
}
