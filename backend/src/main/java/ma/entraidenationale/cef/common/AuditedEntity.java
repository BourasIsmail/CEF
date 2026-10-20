package ma.entraidenationale.cef.common;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/** Ajoute l'auteur de la création / dernière modification (colonnes {@code created_by}, {@code updated_by}). */
@MappedSuperclass
@Getter
public abstract class AuditedEntity extends TimestampedEntity {

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
