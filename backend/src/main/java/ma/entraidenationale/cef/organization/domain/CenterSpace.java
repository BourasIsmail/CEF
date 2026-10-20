package ma.entraidenationale.cef.organization.domain;

import java.io.Serializable;
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

/** Un espace (فضاء) présent dans le centre. */
@Entity
@Table(name = "center_space")
@Getter
@Setter
@NoArgsConstructor
public class CenterSpace {

    @EmbeddedId
    private Key id = new Key();

    @MapsId("centerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id")
    private Center center;

    @MapsId("spaceTypeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_type_id")
    private SpaceType spaceType;

    /** Précision, uniquement pour « فضاء آخر ». */
    @Column(length = 200)
    private String specification;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {

        @Column(name = "center_id")
        private Long centerId;

        @Column(name = "space_type_id")
        private Long spaceTypeId;

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(centerId, k.centerId) && Objects.equals(spaceTypeId, k.spaceTypeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(centerId, spaceTypeId);
        }
    }
}
