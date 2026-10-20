package ma.entraidenationale.cef.training.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.entraidenationale.cef.organization.domain.Center;

/** Filière assignée à un centre. */
@Entity
@Table(name = "center_filiere")
@Getter
@Setter
@NoArgsConstructor
public class CenterFiliere {

    @EmbeddedId
    private Key id = new Key();

    @MapsId("centerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id")
    private Center center;

    @MapsId("filiereId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filiere_id")
    private Filiere filiere;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt = Instant.now();

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {

        @Column(name = "center_id")
        private Long centerId;

        @Column(name = "filiere_id")
        private Long filiereId;

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(centerId, k.centerId) && Objects.equals(filiereId, k.filiereId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(centerId, filiereId);
        }
    }
}
