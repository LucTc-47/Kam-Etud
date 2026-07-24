import type { PropsWithChildren } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ navigate: vi.fn() }));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mocks.navigate };
});

vi.mock("@/components/layout/Layout", () => ({
  default: ({ children }: PropsWithChildren) => <>{children}</>,
}));

vi.mock("@/contexts/AuthContext", () => ({
  useAuth: () => ({ user: { id: "student-1", role: "student", verified: true } }),
}));

vi.mock("@/contexts/LanguageContext", () => ({
  useLanguage: () => ({ t: new Proxy({}, { get: (_target, key) => String(key) }) }),
}));

vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));

vi.mock("@/hooks/useBackendData", () => ({
  useMyGigs: () => ({
    isLoading: false,
    data: [{
      id: "gig-1",
      title: "Création de site",
      category: "Développement",
      active: true,
      published: true,
      rating: 5,
      orderCount: 2,
      tiers: {
        basique: { price: 10_000 },
        standard: { price: 20_000 },
        premium: { price: 30_000 },
      },
    }],
  }),
  useToggleGig: () => ({ mutate: vi.fn() }),
  useDeleteGig: () => ({ mutate: vi.fn() }),
  usePublishGig: () => ({ mutate: vi.fn() }),
  useMyVerifications: () => ({ data: [] }),
  useCreateVerification: () => ({ mutateAsync: vi.fn() }),
}));

import MyGigs from "@/pages/MyGigs";

describe("MyGigs", () => {
  beforeEach(() => mocks.navigate.mockReset());

  it("propose la modification des gigs existants", () => {
    render(<MyGigs />);

    fireEvent.click(screen.getByRole("button", { name: /mg_edit/i }));

    expect(mocks.navigate).toHaveBeenCalledWith("/mes-gigs/gig-1/modifier");
  });
});
