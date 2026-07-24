import type { PropsWithChildren } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  user: { id: "student-1", role: "student" },
}));

vi.mock("@/lib/api", () => ({
  api: {
    get: mocks.get,
    post: mocks.post,
    put: vi.fn(),
    patch: mocks.patch,
    delete: vi.fn(),
    // En production, cette base est vide (site et API sur la meme origine).
    defaults: { baseURL: "" },
  },
  authTokenStorage: { getAccessToken: () => null },
  uploadApiFile: vi.fn(),
}));

vi.mock("@/contexts/AuthContext", () => ({
  useAuth: () => ({ user: mocks.user }),
}));

import { getSignedFileUrl, useAbuseReports, useCreateAbuseReport, useDecideAbuseReport, useGigs, useProfile } from "@/hooks/useBackendData";

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return function Wrapper({ children }: PropsWithChildren) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe("useBackendData", () => {
  beforeEach(() => {
    mocks.get.mockReset();
    mocks.post.mockReset();
    mocks.patch.mockReset();
  });

  it("conserve le statut actif renvoye par Catalog", async () => {
    const tier = {
      name: "Basique",
      description: "Test",
      price: 10_000,
      deliveryDays: 3,
      features: [],
    };
    mocks.get.mockResolvedValue({
      status: 200,
      data: [{
        id: "gig-1",
        studentId: "student-1",
        studentName: "Luc",
        title: "Creation de site",
        description: "Site vitrine",
        category: "Numerique",
        location: "Bafoussam",
        active: true,
        published: true,
        tierBasique: tier,
        tierStandard: tier,
        tierPremium: tier,
      }],
    });

    const { result } = renderHook(() => useGigs(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0]).toMatchObject({ active: true, published: true });
  });

  it("affiche le profil meme si les statistiques sont indisponibles", async () => {
    const profile = {
      id: "profile-1",
      user_id: "student-1",
      first_name: "Luc",
      last_name: "TC",
      email: "luc@kametud.com",
      phone: null,
      avatar_url: null,
      bio: null,
      city: null,
      university: "Universite de Dschang",
      faculty: "Informatique",
      level: "Licence 3",
      skills: [],
      rating: null,
      role: "student",
      verified: true,
      banned: false,
      created_at: "2026-07-06T00:00:00Z",
      updated_at: "2026-07-06T00:00:00Z",
    };
    mocks.get
      .mockResolvedValueOnce({ status: 200, data: profile })
      .mockRejectedValueOnce(new Error("Student stats unavailable"));

    const { result } = renderHook(() => useProfile("student-1"), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(profile);
    expect(mocks.get).toHaveBeenNthCalledWith(1, "/api/profiles/me");
    expect(mocks.get).toHaveBeenNthCalledWith(2, "/api/student-stats/student-1");
  });

  it("envoie le signalement d'abus avec les donnees obligatoires", async () => {
    const request = {
      disputeId: "dispute-1",
      targetUserId: "student-1",
      reason: "false_evidence" as const,
      note: "La preuve semble avoir ete falsifiee.",
    };
    mocks.post.mockResolvedValue({ status: 201, data: { id: "report-1", ...request, status: "open" } });

    const { result } = renderHook(() => useCreateAbuseReport(), { wrapper: createWrapper() });
    await act(async () => { await result.current.mutateAsync(request); });

    expect(mocks.post).toHaveBeenCalledWith("/api/abuse-reports", request);
  });

  it("charge la file d'abus reservee a l'administrateur", async () => {
    mocks.get.mockResolvedValue({ status: 200, data: [{ id: "report-1", status: "open" }] });

    const { result } = renderHook(() => useAbuseReports(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mocks.get).toHaveBeenCalledWith("/api/admin/abuse-reports");
  });

  it("enregistre une decision administrative sans commande de paiement", async () => {
    mocks.patch.mockResolvedValue({ status: 200, data: { id: "report-1", status: "warned" } });

    const { result } = renderHook(() => useDecideAbuseReport(), { wrapper: createWrapper() });
    await act(async () => {
      await result.current.mutateAsync({
        reportId: "report-1",
        action: "WARN",
        adminNote: "Avertissement apres verification des preuves.",
      });
    });

    expect(mocks.patch).toHaveBeenCalledWith("/api/admin/abuse-reports/report-1", {
      action: "WARN",
      adminNote: "Avertissement apres verification des preuves.",
    });
  });

  // Regression : en production api.defaults.baseURL est vide (meme origine).
  // Le code faisait « new URL(chemin, "") », qui leve « Failed to construct
  // 'URL': Invalid base URL », d'ou l'echec du telechargement des preuves et
  // livrables depuis la gestion des litiges. On concatene desormais un chemin
  // relatif, resolu par fetch contre l'origine courante.
  it("construit l'URL du fichier prive sans base d'API configuree", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue({
      ok: true,
      status: 200,
      blob: async () => new Blob(["x"]),
    } as unknown as Response);
    const createObjectURL = vi.fn(() => "blob:local");
    vi.stubGlobal("URL", Object.assign(URL, { createObjectURL }));

    await expect(
      getSignedFileUrl("disputes", "/api/storage/private/files/preuve.png"),
    ).resolves.toBe("blob:local");

    const [calledUrl] = fetchMock.mock.calls[0];
    expect(calledUrl).toBe("/api/storage/private/files/preuve.png");

    vi.unstubAllGlobals();
  });
});
