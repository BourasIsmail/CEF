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

/** موضوع رهن الإشارة : organisme qui met le centre à la disposition de l'Entraide Nationale. */
@Entity
@Table(name = "land_provider")
@Getter
@Setter
@NoArgsConstructor
public class LandProvider {

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

    /** « قطاع آخر مع تحديده » : le texte libre {@code land_owner_text} devient obligatoire. */
    @Column(name = "requires_specification", nullable = false)
    private boolean requiresSpecification;

    @Column(nullable = false)
    private boolean active;
}
