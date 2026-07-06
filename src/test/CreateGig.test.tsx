import type { PropsWithChildren } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  update: vi.fn(),
  gig: {
    id: "gig-1",
    title: "Création de site",
    description: "Site vitrine",
    category: "Développement",
    location: "Douala",
    published: true,
    images: [],
    tiers: {
      basique: { name: "Basique", price: 10_000, description: "Simple", deliveryDays: 7, features: ["Page"] },
      standard: { name: "Standard", price: 20_000, description: "Complet", deliveryDays: 5, features: ["Pages"] },
      premium: { name: "Premium", price: 30_000, description: "Avancé", deliveryDays: 3, features: ["Support"] },
    },
  },
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mocks.navigate,
    useParams: () => ({ gigId: "gig-1" }),
  };
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
  useCreateGig: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateGig: () => ({ mutateAsync: mocks.update, isPending: false }),
  useMyGigs: () => ({
    isLoading: false,
    data: [mocks.gig],
  }),
  useCategories: () => ({ data: [{ id: "cat-1", name: "Développement", active: true }] }),
  useCities: () => ({ data: [{ id: "city-1", name: "Douala", active: true }] }),
}));

import CreateGig from "@/pages/CreateGig";

describe("CreateGig en mode édition", () => {
  beforeEach(() => {
    mocks.navigate.mockReset();
    mocks.update.mockReset();
    mocks.update.mockResolvedValue({ id: "gig-1" });
  });

  it("préremplit et enregistre un gig existant", async () => {
    render(<CreateGig />);

    expect(await screen.findByDisplayValue("Création de site")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "cg_update_btn" }));

    await waitFor(() => expect(mocks.update).toHaveBeenCalledWith(expect.objectContaining({
      id: "gig-1",
      gig: expect.objectContaining({ title: "Création de site", published: true }),
    }))); 
    expect(mocks.navigate).toHaveBeenCalledWith("/mes-gigs");
  });

  it("affiche les categories et les villes disponibles", async () => {
    render(<CreateGig />);

    const selects = await screen.findAllByRole("combobox");
    fireEvent.click(selects[0]);
    const categoryOption = await screen.findByRole("option");
    expect(categoryOption).toHaveTextContent(/veloppement$/);
    fireEvent.click(categoryOption);

    fireEvent.click(selects[1]);
    expect(await screen.findByRole("option", { name: "Douala" })).toBeInTheDocument();
  });
});
