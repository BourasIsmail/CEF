package ma.entraidenationale.cef.organization.service;

import java.util.List;

import org.springframework.stereotype.Component;

import ma.entraidenationale.cef.common.XlsxWriter;
import ma.entraidenationale.cef.organization.api.CenterDtos.Summary;

/** Export Excel de la liste des centres (mêmes colonnes que l'écran). */
@Component
public class CenterExcelExporter {

    public byte[] export(List<Summary> centers, boolean ar) {
        List<String> headers = ar
                ? List.of("اسم المركز", "الرمز", "المديرية الإقليمية", "الجماعة", "المجال", "الوضعية العقارية", "نوع التسيير", "الشعب", "المستفيدون", "الأطر", "نشط")
                : List.of("Centre", "Code", "Direction provinciale", "Commune", "Milieu", "Situation foncière", "Mode de gestion", "Filières", "Bénéficiaires", "Cadres", "Actif");
        List<List<Object>> rows = centers.stream().map(c -> List.<Object>of(
                ar ? c.nameAr() : c.nameFr(), c.code(), c.provincialDirectorateNameAr(), c.communeNameAr(),
                Labels.area(c.areaType(), ar), Labels.land(c.landStatus(), ar), Labels.mode(c.managementMode(), ar),
                c.filieresCount(), c.beneficiariesCount(), c.staffCount(),
                ar ? (c.active() ? "نعم" : "لا") : (c.active() ? "Oui" : "Non"))).toList();
        return XlsxWriter.write(ar ? "المراكز" : "Centres", ar, headers, rows);
    }

    private static final class Labels {
        static String area(ma.entraidenationale.cef.organization.domain.AreaType v, boolean ar) {
            return switch (v) { case URBAN -> ar ? "حضري" : "Urbain"; case RURAL -> ar ? "قروي" : "Rural"; };
        }

        static String land(ma.entraidenationale.cef.organization.domain.LandStatus v, boolean ar) {
            return switch (v) {
                case OWNED_BY_EN -> ar ? "في ملكية التعاون الوطني" : "Propriété de l'Entraide Nationale";
                case RENTED_BY_EN -> ar ? "مكترى من طرف التعاون الوطني" : "Loué par l'Entraide Nationale";
                case PROVIDED_TO_EN -> ar ? "موضوع رهن إشارة التعاون الوطني" : "Mis à disposition de l'Entraide Nationale";
                case ASSOCIATION -> ar ? "تابع للجمعية" : "Propriété de l'association";
                case OTHER_SECTOR -> ar ? "تابع لقطاع آخر" : "Relevant d'un autre secteur";
            };
        }

        static String mode(ma.entraidenationale.cef.organization.domain.ManagementMode v, boolean ar) {
            return switch (v) {
                case DIRECT_EN -> ar ? "مسير مباشرة من التعاون الوطني" : "Géré directement par l'Entraide Nationale";
                case ASSOCIATION_WITH_EN_DIRECTOR -> ar ? "جمعية + مدير تابع للتعاون الوطني" : "Association + directeur de l'Entraide Nationale";
                case ASSOCIATION_FULL -> ar ? "مسير من طرف جمعية" : "Géré par une association";
            };
        }
    }
}
