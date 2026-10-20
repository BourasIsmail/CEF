import { describe, expect, it } from "vitest";
import { interpolate } from "./format";
import { messages } from "./messages";

describe("i18n", () => {
  it("remplace les variables", () => {
    expect(interpolate("Bonjour {name} ({n})", { name: "Ali", n: 3 })).toBe("Bonjour Ali (3)");
    expect(interpolate("Reste {x}", {})).toBe("Reste {x}");
  });

  it("l'arabe et le français ont exactement les mêmes clés, sans texte vide", () => {
    expect(Object.keys(messages.fr).sort()).toEqual(Object.keys(messages.ar).sort());
    for (const lang of ["ar", "fr"] as const) {
      for (const [k, v] of Object.entries(messages[lang])) expect(v, `${lang}:${k}`).not.toBe("");
    }
  });

  it("les mêmes variables {x} apparaissent dans les deux langues", () => {
    const vars = (s: string) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
    for (const k of Object.keys(messages.ar) as (keyof typeof messages.ar)[]) {
      expect(vars(messages.fr[k]), k).toEqual(vars(messages.ar[k]));
    }
  });
});
