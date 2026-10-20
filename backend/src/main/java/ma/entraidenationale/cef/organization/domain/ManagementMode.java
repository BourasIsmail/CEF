package ma.entraidenationale.cef.organization.domain;

/** وضعية تسيير المركز. */
public enum ManagementMode {
    DIRECT_EN,                      // مسير مباشرة من التعاون الوطني
    ASSOCIATION_WITH_EN_DIRECTOR,   // مسير من طرف جمعية + مدير تابع للتعاون الوطني
    ASSOCIATION_FULL                // مسير من طرف جمعية
}
