import { beforeEach, describe, expect, it, vi } from "vitest";

import { api, authTokenStorage } from "@/lib/api";

const jsonResponse = (data: unknown) => ({
  ok: true,
  status: 200,
  headers: new Headers({ "content-type": "application/json" }),
  text: async () => JSON.stringify(data),
}) as Response;

describe("client API", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("ne joint pas un ancien JWT aux listes publiques du catalogue", async () => {
    authTokenStorage.setTokens("ancien-token");
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse([]));

    await api.get("/api/categories");
    await api.get("/api/cities");

    for (const [, options] of fetchMock.mock.calls) {
      expect(new Headers(options?.headers).has("Authorization")).toBe(false);
    }
  });

  it("conserve le JWT pour une route protegee", async () => {
    authTokenStorage.setTokens("access-token");
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse([]));

    await api.get("/api/gigs/mine");

    const [, options] = fetchMock.mock.calls[0];
    expect(new Headers(options?.headers).get("Authorization")).toBe("Bearer access-token");
  });
});
