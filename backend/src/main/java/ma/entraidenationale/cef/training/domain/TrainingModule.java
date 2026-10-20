package ma.entraidenationale.cef.training.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Unité (module) d'une filière : coefficient et volume horaire propres. */
@Entity
@Table(name = "module")
@Getter
@Setter
@NoArgsConstructor
public class TrainingModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filiere_id", nullable = false)
    private Filiere filiere;

    @Column(name = "name_ar", nullable = false, length = 200)
    private String nameAr;

    @Column(name = "name_fr", length = 200)
    private String nameFr;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal coefficient;

    @Column(name = "duration_hours", nullable = false)
    private short durationHours;

    @Column(nullable = false)
    private short position;
}
