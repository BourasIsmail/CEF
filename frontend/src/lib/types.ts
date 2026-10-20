/** Types des réponses du backend (voir docs/api.md). */

export type Role = "ADMIN" | "REGIONAL" | "PROVINCIAL" | "CENTER_MANAGER" | "HR_OFFICER" | "READ_ONLY";
export type Locale = "ar" | "fr";

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Me {
  id: number;
  username: string;
  fullName: string;
  email?: string;
  role: Role;
  regionId?: number;
  regionNameAr?: string;
  provinceId?: number;
  provinceNameAr?: string;
  centerId?: number;
  enabled: boolean;
  locked: boolean;
  preferredLocale: Locale;
  lastLoginAt?: string;
  createdAt: string;
}

export type UserView = Me;

export interface Region {
  id: number;
  code: string;
  nameAr: string;
  nameFr: string;
}

export interface Province {
  id: number;
  regionId: number;
  code: string;
  nameAr: string;
  nameFr: string;
}

export type StatCategory = "MIGRANT" | "DISABLED" | "OTHER" | "REMAINING";

export interface Cell { male: number; female: number; total: number }

export interface Metrics {
  registered: Cell; departed: Cell; graduates: Cell; failed: Cell; absent: Cell;
  passRatePercent?: number;
}

export interface Dashboard {
  season?: { id: number; label: string };
  totals: Metrics;
  inProgress: number;
  byFiliere: { id: number; code: string; nameAr: string; nameFr: string; registered: number; graduates: number }[];
  byCategory: { category: StatCategory; registered: number }[];
  todo: { graduatesWithoutDiploma: number; examsWithoutExamNumber: number; departedWithoutReason: number };
}

// ---------------------------------------------------------------- référentiels et catalogue

export interface Commune { id: number; provinceId: number; nameAr: string; nameFr: string }
export interface SpaceType { id: number; code: string; nameAr: string; nameFr: string; requiresSpecification: boolean }
export interface PartnerType { id: number; code: string; nameAr: string; nameFr: string }
export interface Association { id: number; nameAr: string; nameFr?: string }
export interface Season { id: number; label: string; startDate: string; endDate: string; current: boolean }
export interface Program { id: number; code: string; nameAr: string; nameFr: string }

export interface ModuleView { id: number; nameAr: string; nameFr?: string; coefficient: number; durationHours: number; position: number }

export interface FiliereSummary {
  id: number; programId: number; programCode: string; code: string; nameAr: string; nameFr: string;
  durationMonths: number; active: boolean; modulesCount: number; totalCoefficient: number; totalHours: number; centersCount: number;
}

export interface CenterRef { id: number; code: string; nameAr: string; provinceId: number }

export interface FiliereDetail {
  summary: FiliereSummary; modules: ModuleView[]; centers: CenterRef[];
  createdAt: string; updatedAt: string; createdBy?: string; updatedBy?: string;
}

export interface CenterFiliere {
  filiereId: number; code: string; nameAr: string; nameFr: string; durationMonths: number; active: boolean; assignedAt: string;
}

// ---------------------------------------------------------------- centres

export type AreaType = "RURAL" | "URBAN";
export type BuildingType = "ONE_FLOOR" | "TWO_FLOORS" | "THREE_FLOORS" | "OTHER";
export type LandStatus = "OWNED_BY_EN" | "RENTED_BY_EN" | "PROVIDED_TO_EN" | "ASSOCIATION" | "OTHER_SECTOR";
export type ManagementMode = "DIRECT_EN" | "ASSOCIATION_WITH_EN_DIRECTOR" | "ASSOCIATION_FULL";
export type PartnershipEvaluation = "GOOD" | "FAIR" | "AVERAGE" | "WEAK";
export type PartnershipMeasure = "EXPAND" | "CONTINUE" | "TERMINATE" | "OTHER";

export interface CenterSummary {
  id: number; code: string; nameAr: string; nameFr?: string;
  regionalDirectorateId: number; provincialDirectorateId: number; provincialDirectorateNameAr: string;
  communeId: number; communeNameAr: string;
  areaType: AreaType; landStatus?: LandStatus; managementMode?: ManagementMode; active: boolean;
  filieresCount: number; beneficiariesCount: number; staffCount: number;
}

export interface FloorArea { floorNumber: number; areaM2: number }
export interface SpaceView { spaceTypeId: number; code: string; nameAr: string; nameFr: string; specification?: string }
export interface PartnerRef { id: number; code: string; nameAr: string; nameFr: string }
export interface CenterAssociationView {
  id: number; associationId: number; associationNameAr: string; associationNameFr?: string;
  agreementSubject?: string; agreementDate?: string; agreementReference?: string;
  partnershipEvaluation?: PartnershipEvaluation; measureTaken?: PartnershipMeasure; measureOtherText?: string;
  partners: PartnerRef[];
}

export interface CenterDetail {
  id: number; code: string; nameAr: string; nameFr?: string;
  regionalDirectorateId: number; regionalDirectorateNameAr: string;
  provincialDirectorateId: number; provincialDirectorateNameAr: string;
  communeId: number; communeNameAr: string;
  areaType: AreaType; address?: string;
  buildingType?: BuildingType; floorsCount?: number; floorAreas: FloorArea[]; totalAreaM2?: number;
  landStatus?: LandStatus; landOwnerText?: string; managementMode?: ManagementMode;
  spaces: SpaceView[]; spacesCount: number; associations: CenterAssociationView[]; associationsCount: number;
  filieresCount: number; beneficiariesCount: number; staffCount: number;
  active: boolean; createdAt: string; updatedAt: string; createdBy?: string; updatedBy?: string;
}

// ---------------------------------------------------------------- bénéficiaires

export type Gender = "M" | "F";
export type EnrollmentStatus = "ENROLLED" | "DEPARTED" | "GRADUATED" | "FAILED" | "ABSENT";
export type ExamStatus = "TAKEN" | "ABSENT" | "NOT_TAKEN";
export type ExamResult = "PASSED" | "FAILED" | "ABSENT" | "INCOMPLETE";
export type SessionType = "NORMAL" | "MAKEUP";

export interface BeneficiarySummary {
  id: number; centerId: number; centerNameAr: string; rankNumber: number; registrationNumber: string; registrationDate: string;
  gender: Gender; fullNameAr: string; fullNameFr?: string; age: number; category: StatCategory; vulnerable: boolean;
  enrollmentId?: number; filiereId?: number; filiereNameAr?: string; seasonId?: number; status?: EnrollmentStatus;
}

export interface SessionSummary { type: SessionType; result: ExamResult; overallAverage?: number; mention?: string; passed?: boolean }
export interface DiplomaView { id: number; serialNumber: string; receivedDate?: string; additionalInfo?: string }

export interface EnrollmentView {
  id: number; beneficiaryId: number; seasonId: number; seasonLabel: string; centerId: number; centerNameAr: string;
  filiereId: number; filiereCode: string; filiereNameAr: string; filiereNameFr: string;
  startDate: string; examDate?: string; status: EnrollmentStatus; departureDate?: string; departureReason?: string;
  sessions: SessionSummary[]; diploma?: DiplomaView; createdAt: string; updatedAt: string;
}

export interface BeneficiaryDetail {
  id: number; centerId: number; centerNameAr: string; provincialDirectorateId: number; regionalDirectorateId: number;
  rankNumber: number; registrationNumber: string; registrationDate: string; gender: Gender;
  fullNameAr: string; fullNameFr?: string; birthDate: string; age: number; birthPlace?: string;
  idDocumentType?: string; idDocumentNumber?: string; address?: string; phone?: string;
  educationLevel?: string; familyStatus?: string; familyStatusOtherText?: string; additionalInfo?: string;
  vulnerable: boolean; disabilityType?: string; disabilityOtherText?: string;
  migrationStatus?: string; migrationOtherText?: string; originCountry?: string;
  otherSocialSituation?: string; category: StatCategory;
  enrollments: EnrollmentView[]; createdAt: string; updatedAt: string; createdBy?: string; updatedBy?: string;
}

export interface GradeView { moduleId: number; nameAr: string; coefficient: number; status: ExamStatus; grade?: number }
export interface SessionView {
  type: SessionType; examNumber?: string; grades: GradeView[]; finalExamStatus: ExamStatus; finalExamGrade?: number;
  modulesAverage?: number; overallAverage?: number; passThreshold: number; exceptionalPass: boolean;
  exceptionalPassReason?: string; exceptionalPassBy?: string; result: ExamResult; passed?: boolean; mention?: string;
  updatedAt: string; updatedBy?: string;
}
export interface Bulletin {
  enrollmentId: number; beneficiaryId: number; beneficiaryNameAr: string; beneficiaryNameFr?: string;
  registrationNumber: string; seasonLabel: string; centerNameAr: string; centerNameFr?: string; filiereNameAr: string; filiereNameFr?: string;
  status: EnrollmentStatus; examDate?: string;
  modules: ModuleView[]; sessions: SessionView[]; makeupAllowed: boolean; diploma?: DiplomaView;
}

export interface AuditEntry {
  id: number; entityType: string; entityId: number; rootType: string; rootId: number;
  action: "CREATE" | "UPDATE" | "DELETE"; username: string; occurredAt: string;
  changes: Record<string, { old?: unknown; new?: unknown }>;
}

// ---------------------------------------------------------------- ressources humaines

export type EmployeeType = "PEDAGOGICAL" | "CENTER_DIRECTOR" | "ADMINISTRATIVE" | "GUEST_TRAINER" | "PEDAGOGICAL_COORDINATOR";
export type AdministrativeStatus = "ENTRAIDE_NATIONALE" | "ASSOCIATION" | "OTHER_SECTOR" | "PROMOTION_NATIONALE" | "FOREIGN_VOLUNTEER";
export type ActivityStatus = "ACTIVE" | "ON_LEAVE" | "SECONDED" | "SUSPENDED" | "RETIRED";

export interface EmployeeSummary {
  id: number; provincialDirectorateId: number; centerId?: number; centerNameAr?: string;
  employeeType: EmployeeType; administrativeStatus: AdministrativeStatus;
  fullNameAr: string; fullNameFr?: string; gender: Gender; phone?: string; rankNumber?: string; hireDate?: string; activityStatus: ActivityStatus;
  enGrade?: string; enScale?: string; teachingFilieresAr?: string; teachingFilieresFr?: string;
}

export interface AssignmentView {
  id: number; fromDate: string; toDate?: string; provincialDirectorateId: number; centerId?: number; centerNameAr?: string;
  positionTitle?: string; decisionReference?: string;
}
export interface TeachingView { id: number; seasonId: number; seasonLabel: string; filiereId: number; filiereNameAr: string; moduleId?: number; moduleNameAr?: string }
export interface GuestContractView {
  id: number; seasonId: number; seasonLabel: string; filiereId: number; filiereNameAr: string; diplomaYear?: number; workStartDate?: string;
  hourlyRateMad?: number; monthsPerYear?: number; hoursPerMonth?: number; beneficiariesCount?: number; estimatedAnnualCostMad?: number;
}

export interface EmployeeDetail {
  id: number; provincialDirectorateId: number; provincialDirectorateNameAr: string; regionalDirectorateId: number;
  centerId?: number; centerNameAr?: string; employeeType: EmployeeType; administrativeStatus: AdministrativeStatus;
  otherSectorText?: string; delegatingBody?: string; fullNameAr: string; fullNameFr?: string; gender: Gender;
  birthDate?: string; birthPlace?: string; idDocumentType?: string; idDocumentNumber?: string; address?: string; phone?: string; email?: string;
  educationLevel?: string; diplomaTitle?: string; specialty?: string; diplomaDate?: string; experienceDescription?: string; otherQualifications?: string;
  enGrade?: string; enScale?: string; rankNumber?: string; hireDate?: string; activityStatus: ActivityStatus;
  assignments: AssignmentView[]; teachings: TeachingView[]; guestContracts: GuestContractView[];
  createdAt: string; updatedAt: string; createdBy?: string; updatedBy?: string;
}

export interface StaffSummary {
  total: number; male: number; female: number;
  byType: Record<EmployeeType, number>; byAdministrativeStatus: Record<AdministrativeStatus, number>;
  matrix: Record<EmployeeType, Record<AdministrativeStatus, number>>;
}

// ---------------------------------------------------------------- demandes de filière

export type RequestType = "CREATE" | "MODIFY";
export type RequestStatus = "DRAFT" | "SUBMITTED" | "RETURNED" | "REGIONAL_VALIDATED" | "APPROVED" | "REJECTED";

export interface RequestSummary {
  id: number; requestNumber: string; requestType: RequestType; status: RequestStatus; centerId: number; centerNameAr: string;
  provinceId: number; provinceNameAr: string; regionId: number; proposedNameAr: string; targetFiliereId?: number;
  submittedAt?: string; createdAt: string; updatedAt: string;
}
export interface RequestDocument { id: number; originalFilename: string; sizeBytes: number; sha256: string; uploadedAt: string; uploadedBy: string }
export interface RequestDocumentSlot { typeCode: string; typeNameAr: string; position: number; mandatory: boolean; document?: RequestDocument }
export interface RequestHistoryEntry { fromStatus?: RequestStatus; toStatus: RequestStatus; actor: string; comment?: string; occurredAt: string }
export interface RequestDetail {
  id: number; requestNumber: string; requestType: RequestType; status: RequestStatus; centerId: number; centerNameAr: string;
  provinceId: number; provinceNameAr: string; regionId: number; associationId?: number; associationNameAr?: string;
  targetFiliereId?: number; targetFiliereNameAr?: string; proposedNameAr: string; proposedNameFr?: string; durationMonths?: number;
  expectedBeneficiaries?: number; modificationSummary?: string; submittedAt?: string;
  regionalDecisionAt?: string; regionalComment?: string; nationalDecisionAt?: string; nationalComment?: string; resultingFiliereId?: number;
  documents: RequestDocumentSlot[]; history: RequestHistoryEntry[]; availableActions: string[]; createdAt: string; updatedAt: string;
}
export interface RequestCounts { byStatus: Record<RequestStatus, number>; total: number }

// ---------------------------------------------------------------- rapport statistique

export interface CategoryRow { category: StatCategory; specification?: string; metrics: Metrics }
export interface FiliereBlock { id: number; code: string; nameAr: string; nameFr: string; categories: CategoryRow[]; total: Metrics }
export interface ProvinceBlock { id: number; code: string; nameAr: string; nameFr: string; filieres: FiliereBlock[]; total: Metrics }
export interface RegionBlock { id: number; code: string; nameAr: string; nameFr: string; provinces: ProvinceBlock[]; total: Metrics }
export interface StatisticsReport {
  season?: { id: number; label: string }; regions: RegionBlock[]; grandTotal: Metrics; generatedAt: string;
}
