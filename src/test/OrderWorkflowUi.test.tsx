import type { PropsWithChildren } from "react";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  invalidateQueries: vi.fn(),
  updateOrder: vi.fn(),
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ invalidateQueries: mocks.invalidateQueries }),
}));

vi.mock("@/components/layout/Layout", () => ({
  default: ({ children }: PropsWithChildren) => <>{children}</>,
}));

vi.mock("@/contexts/AuthContext", () => ({
  useAuth: () => ({ user: { id: "user-1", role: "student", phone: "237670000000" } }),
}));

vi.mock("@/contexts/LanguageContext", () => ({
  useLanguage: () => ({
    locale: "fr-FR",
    t: new Proxy({}, { get: (_target, key) => String(key) }),
  }),
}));

vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));

const order = (status: string, title: string, deliverable = false) => ({
  id: `${status}-1`,
  gigId: "gig-1",
  gigTitle: title,
  clientId: "client-1",
  clientName: "Client Test",
  studentId: "student-1",
  studentName: "Etudiant Test",
  tier: "standard",
  description: "Mission test",
  budget: 20_000,
  status,
  revisionsLeft: 2,
  deliveryDate: "2026-08-01",
  createdAt: "2026-07-06T00:00:00Z",
  escrowAmount: 20_000,
  paymentMethod: "mobile_money",
  deliverableUrl: deliverable ? "http://localhost:8080/api/storage/private/files/test.png" : undefined,
  deliverableNote: deliverable ? "Voici le projet livre" : undefined,
});

vi.mock("@/hooks/useBackendData", () => ({
  useMyMissions: () => ({
    isLoading: false,
    data: [order("accepted", "Mission payee"), order("in_progress", "Mission commencee")],
  }),
  useMyOrders: () => ({ isLoading: false, data: [order("delivered", "Mission livree", true)] }),
  useUpdateOrder: () => ({ mutate: mocks.updateOrder, mutateAsync: mocks.updateOrder }),
  useRespondToDispute: () => ({ mutate: vi.fn(), isPending: false }),
  useCreateDispute: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useCreateReview: () => ({ mutate: vi.fn(), isPending: false }),
  useChatMessages: () => ({ data: [] }),
  useSendMessage: () => ({ mutate: vi.fn() }),
  subscribeToChatMessages: () => () => undefined,
  uploadPrivateFile: vi.fn(),
  getSignedFileUrl: vi.fn(),
  initiateOrderPayment: vi.fn(),
  releaseOrderPayment: vi.fn(),
}));

import MyMissions from "@/pages/MyMissions";
import MyOrders from "@/pages/MyOrders";

describe("parcours des commandes", () => {
  it("conserve une mission payee dans Demandes jusqu'a l'acceptation etudiante", () => {
    render(<MemoryRouter><MyMissions /></MemoryRouter>);

    expect(screen.getByRole("tab", { name: "mm_demands (1)" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "mm_active (1)" })).toBeInTheDocument();
    expect(screen.getByText("Mission payee")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "mm_accept" })).toBeInTheDocument();

  });

  it("montre au client le livrable et l'option de contestation", () => {
    render(<MemoryRouter><MyOrders /></MemoryRouter>);

    expect(screen.getByText("Voici le projet livre")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "ad_download" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "mo_dispute" })).toBeInTheDocument();
  });
});
