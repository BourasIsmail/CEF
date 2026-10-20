/** Pays d'origine proposés pour les migrants (valeur stockée : code ISO ; une valeur libre existante reste affichée telle quelle). */
export const COUNTRIES: { code: string; ar: string; fr: string }[] = [
  { code: "ML", ar: "مالي", fr: "Mali" },
  { code: "SN", ar: "السنغال", fr: "Sénégal" },
  { code: "GN", ar: "غينيا", fr: "Guinée" },
  { code: "CI", ar: "كوت ديفوار", fr: "Côte d'Ivoire" },
  { code: "CM", ar: "الكاميرون", fr: "Cameroun" },
  { code: "NG", ar: "نيجيريا", fr: "Nigéria" },
  { code: "CG", ar: "الكونغو", fr: "Congo" },
  { code: "CD", ar: "الكونغو الديمقراطية", fr: "RD Congo" },
  { code: "GA", ar: "الغابون", fr: "Gabon" },
  { code: "NE", ar: "النيجر", fr: "Niger" },
  { code: "BF", ar: "بوركينا فاسو", fr: "Burkina Faso" },
  { code: "TD", ar: "تشاد", fr: "Tchad" },
  { code: "SD", ar: "السودان", fr: "Soudan" },
  { code: "SS", ar: "جنوب السودان", fr: "Soudan du Sud" },
  { code: "GM", ar: "غامبيا", fr: "Gambie" },
  { code: "SL", ar: "سيراليون", fr: "Sierra Leone" },
  { code: "LR", ar: "ليبيريا", fr: "Libéria" },
  { code: "GH", ar: "غانا", fr: "Ghana" },
  { code: "TG", ar: "توغو", fr: "Togo" },
  { code: "BJ", ar: "بنين", fr: "Bénin" },
  { code: "CF", ar: "إفريقيا الوسطى", fr: "Centrafrique" },
  { code: "BI", ar: "بوروندي", fr: "Burundi" },
  { code: "RW", ar: "رواندا", fr: "Rwanda" },
  { code: "ET", ar: "إثيوبيا", fr: "Éthiopie" },
  { code: "ER", ar: "إريتريا", fr: "Érythrée" },
  { code: "SO", ar: "الصومال", fr: "Somalie" },
  { code: "MR", ar: "موريتانيا", fr: "Mauritanie" },
  { code: "DZ", ar: "الجزائر", fr: "Algérie" },
  { code: "TN", ar: "تونس", fr: "Tunisie" },
  { code: "LY", ar: "ليبيا", fr: "Libye" },
  { code: "EG", ar: "مصر", fr: "Égypte" },
  { code: "SY", ar: "سوريا", fr: "Syrie" },
  { code: "YE", ar: "اليمن", fr: "Yémen" },
  { code: "IQ", ar: "العراق", fr: "Irak" },
  { code: "PS", ar: "فلسطين", fr: "Palestine" },
  { code: "LB", ar: "لبنان", fr: "Liban" },
  { code: "PK", ar: "باكستان", fr: "Pakistan" },
  { code: "BD", ar: "بنغلاديش", fr: "Bangladesh" },
  { code: "PH", ar: "الفلبين", fr: "Philippines" },
  { code: "UA", ar: "أوكرانيا", fr: "Ukraine" },
  { code: "FR", ar: "فرنسا", fr: "France" },
  { code: "ES", ar: "إسبانيا", fr: "Espagne" },
];

/** Nom localisé d'un pays à partir du code stocké ; une valeur libre ancienne est renvoyée telle quelle. */
export function countryName(value: string | undefined | null, locale: "ar" | "fr"): string {
  if (!value) return "";
  const c = COUNTRIES.find((x) => x.code === value);
  return c ? c[locale] : value;
}
