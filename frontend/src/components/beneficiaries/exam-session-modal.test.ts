import { describe, expect, it } from "vitest";
import { previewAverages } from "./exam-session-modal";

const modules = [{ id: 1, coefficient: 4 }, { id: 2, coefficient: 3 }, { id: 3, coefficient: 1 }];

describe("previewAverages", () => {
  it("calcule la moyenne pondérée puis la moyenne générale (modules + final) / 2", () => {
    const grades = [
      { moduleId: 1, status: "TAKEN" as const, grade: "12" },
      { moduleId: 2, status: "TAKEN" as const, grade: "14" },
      { moduleId: 3, status: "TAKEN" as const, grade: "8" },
    ];
    const r = previewAverages(modules, grades, "16", "TAKEN");
    expect(r.modules).toBeCloseTo((12 * 4 + 14 * 3 + 8) / 8, 10);
    expect(r.overall).toBeCloseTo(((12 * 4 + 14 * 3 + 8) / 8 + 16) / 2, 10);
    expect(r.absent).toBe(false);
  });

  it("reste vide tant qu'une note manque et signale l'absence", () => {
    const grades = [
      { moduleId: 1, status: "TAKEN" as const, grade: "12" },
      { moduleId: 2, status: "ABSENT" as const, grade: "" },
      { moduleId: 3, status: "TAKEN" as const, grade: "8" },
    ];
    const r = previewAverages(modules, grades, "16", "TAKEN");
    expect(r.modules).toBeUndefined();
    expect(r.absent).toBe(true);
  });

  it("sans note finale, seule la moyenne des modules est affichée", () => {
    const grades = modules.map((m) => ({ moduleId: m.id, status: "TAKEN" as const, grade: "10" }));
    const r = previewAverages(modules, grades, "", "TAKEN");
    expect(r.modules).toBe(10);
    expect(r.overall).toBeUndefined();
  });
});
