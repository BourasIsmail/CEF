import { describe, expect, it } from "vitest";
import { passwordStrength } from "./form";

describe("passwordStrength", () => {
  it("croît avec la longueur et la variété", () => {
    expect(passwordStrength("")).toBe(0);
    expect(passwordStrength("abc")).toBe(0);
    expect(passwordStrength("abcdefghijkl1")).toBe(2);
    expect(passwordStrength("abcdefghijkl1!")).toBe(3);
    expect(passwordStrength("abcdefghijklmnop1!")).toBe(4);
  });
});
