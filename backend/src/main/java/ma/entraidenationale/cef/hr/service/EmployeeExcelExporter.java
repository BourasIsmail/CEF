package ma.entraidenationale.cef.hr.service;

import java.util.List;

import org.springframework.stereotype.Component;

import ma.entraidenationale.cef.common.XlsxWriter;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.Summary;

/** Export Excel de la liste des agents (mêmes colonnes que l'écran). */
@Component
public class EmployeeExcelExporter {

    public byte[] export(List<Summary> employees, boolean ar) {
        List<String> headers = ar
                ? List.of("الرقم الترتيبي", "الاسم الكامل", "الاسم بالفرنسية", "الصفة", "الوضعية الإدارية", "الدرجة", "السلم", "التعيين الحالي",
                        "الشعب المدرّسة", "تاريخ التوظيف", "الجنس", "الهاتف", "الحالة")
                : List.of("N° d'ordre", "Nom complet (ar)", "Nom complet", "Fonction", "Statut administratif", "Grade", "Échelle", "Affectation actuelle",
                        "Filières enseignées", "Date de recrutement", "Sexe", "Téléphone", "Situation");
        List<List<Object>> rows = employees.stream().map(e -> {
            List<Object> r = new java.util.ArrayList<>();
            r.add(e.rankNumber());
            r.add(e.fullNameAr());
            r.add(e.fullNameFr());
            r.add(type(e.employeeType(), ar));
            r.add(status(e.administrativeStatus(), ar));
            r.add(e.enGrade());
            r.add(e.enScale());
            r.add(e.centerNameAr());
            r.add(ar || e.teachingFilieresFr() == null ? e.teachingFilieresAr() : e.teachingFilieresFr());
            r.add(e.hireDate() == null ? null : e.hireDate().toString());
            r.add(e.gender() == null ? null : switch (e.gender()) { case M -> ar ? "ذكر" : "Homme"; case F -> ar ? "أنثى" : "Femme"; });
            r.add(e.phone());
            r.add(activity(e.activityStatus(), ar));
            return r;
        }).toList();
        return XlsxWriter.write(ar ? "الموارد البشرية" : "Ressources humaines", ar, headers, rows);
    }

    private static String type(ma.entraidenationale.cef.hr.domain.EmployeeType v, boolean ar) {
        return switch (v) {
            case PEDAGOGICAL -> ar ? "مؤطر تربوي" : "Cadre pédagogique";
            case CENTER_DIRECTOR -> ar ? "مدير المركز" : "Directeur de centre";
            case ADMINISTRATIVE -> ar ? "إداري" : "Administratif";
            case GUEST_TRAINER -> ar ? "مكون مستضاف" : "Formateur invité";
            case PEDAGOGICAL_COORDINATOR -> ar ? "منسق تربوي" : "Coordonnateur pédagogique";
        };
    }

    private static String status(ma.entraidenationale.cef.hr.domain.AdministrativeStatus v, boolean ar) {
        return switch (v) {
            case ENTRAIDE_NATIONALE -> ar ? "التعاون الوطني" : "Entraide Nationale";
            case ASSOCIATION -> ar ? "جمعية" : "Association";
            case OTHER_SECTOR -> ar ? "قطاع آخر" : "Autre secteur";
            case PROMOTION_NATIONALE -> ar ? "الإنعاش الوطني" : "Promotion nationale";
            case FOREIGN_VOLUNTEER -> ar ? "متطوع أجنبي" : "Volontaire étranger";
        };
    }

    private static String activity(ma.entraidenationale.cef.hr.domain.ActivityStatus v, boolean ar) {
        return switch (v) {
            case ACTIVE -> ar ? "في نشاط" : "En activité";
            case ON_LEAVE -> ar ? "في عطلة" : "En congé";
            case SECONDED -> ar ? "ملحق" : "Détaché";
            case SUSPENDED -> ar ? "موقوف" : "Suspendu";
            case RETIRED -> ar ? "متقاعد" : "Retraité";
        };
    }
}
