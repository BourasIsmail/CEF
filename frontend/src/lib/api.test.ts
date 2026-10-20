import { describe, expect, it } from "vitest";
import { buildQuery, readCookie } from "./api";

describe("api", () => {
  it("construit la query en ignorant les valeurs vides", () => {
    expect(buildQuery({ a: 1, b: "", c: undefined, d: null, e: false, f: "x y" })).toBe("?a=1&e=false&f=x+y");
    expect(buildQuery({})).toBe("");
    expect(buildQuery()).toBe("");
  });

  it("lit un cookie par son nom exact", () => {
    const jar = "a=1; XSRF-TOKEN=abc%3D; other=2";
    expect(readCookie("XSRF-TOKEN", jar)).toBe("abc=");
    expect(readCookie("XSRF", jar)).toBeUndefined();
  });
});
