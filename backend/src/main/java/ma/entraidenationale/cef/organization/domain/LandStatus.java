package ma.entraidenationale.cef.organization.domain;

/** الوضعية العقارية. */
public enum LandStatus {
    OWNED_BY_EN,        // في ملكية التعاون الوطني
    RENTED_BY_EN,       // مكترى من طرف التعاون الوطني
    PROVIDED_TO_EN,     // موضوع رهن إشارة التعاون الوطني (propriétaire à préciser)
    ASSOCIATION,        // تابع للجمعية
    OTHER_SECTOR        // تابع لقطاع آخر (à préciser)
}
