package ma.entraidenationale.cef.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** الشركاء : liste prédéfinie. */
@Entity
@Table(name = "partner_type")
@Getter
@Setter
@NoArgsConstructor
public class PartnerType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "name_ar", nullable = false, length = 200)
    private String nameAr;

    @Column(name = "name_fr", nullable = false, length = 200)
    private String nameFr;

    @Column(nullable = false)
    private short position;

    /** « مع تحديده » : une précision est demandée sur la ligne d'association. */
    @Column(name = "requires_specification", nullable = false)
    private boolean requiresSpecification;

    @Column(nullable = false)
    private boolean active;
}
