package ma.entraidenationale.cef.organization.domain;

/** نوع البناية. */
public enum BuildingType {
    ONE_FLOOR(1), TWO_FLOORS(2), THREE_FLOORS(3), OTHER(0);

    private final int fixedFloors;

    BuildingType(int fixedFloors) {
        this.fixedFloors = fixedFloors;
    }

    /** Nombre d'étages imposé par le type (0 = libre, à préciser). */
    public int fixedFloors() {
        return fixedFloors;
    }
}
