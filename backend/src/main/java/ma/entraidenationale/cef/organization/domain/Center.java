package ma.entraidenationale.cef.organization.domain;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.entraidenationale.cef.common.AuditedEntity;

/** المركز. */
@Entity
@Table(name = "center")
@Getter
@Setter
@NoArgsConstructor
public class Center extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provincial_directorate_id", nullable = false, updatable = false)
    private ProvincialDirectorate provincialDirectorate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commune_id", nullable = false)
    private Commune commune;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(name = "name_ar", nullable = false, length = 200)
    private String nameAr;

    @Column(name = "name_fr", length = 200)
    private String nameFr;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "area_type", nullable = false, length = 10)
    private AreaType areaType;

    @Column(length = 300)
    private String address;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "building_type", length = 20)
    private BuildingType buildingType;

    /** Obligatoire quand buildingType = OTHER. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "floors_count")
    private Short floorsCount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "land_status", length = 30)
    private LandStatus landStatus;

    @Column(name = "land_owner_text", length = 255)
    private String landOwnerText;

    /** موضوع رهن الإشارة : organisme propriétaire quand la situation foncière est « mis à disposition ». */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "land_provider_id")
    private LandProvider landProvider;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "management_mode", length = 40)
    private ManagementMode managementMode;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "center", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("floorNumber")
    private List<CenterFloorArea> floorAreas = new ArrayList<>();

    @OneToMany(mappedBy = "center", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CenterSpace> spaces = new ArrayList<>();

    @OneToMany(mappedBy = "center", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CenterAssociation> associations = new ArrayList<>();
}
