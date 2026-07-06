import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AuthProvider } from "@/contexts/AuthContext";
import { ThemeProvider } from "@/contexts/ThemeContext";
import { LanguageProvider } from "@/contexts/LanguageContext";
import { NotificationProvider } from "@/contexts/NotificationContext";
import { lazy, Suspense } from "react";
import { Loader2 } from "lucide-react";

// Lazy loading components
const Index = lazy(() => import("./pages/Index"));
const Services = lazy(() => import("./pages/Services"));
const HowItWorks = lazy(() => import("./pages/HowItWorks"));
const NotFound = lazy(() => import("./pages/NotFound"));
const Login = lazy(() => import("./pages/auth/Login"));
const Register = lazy(() => import("./pages/auth/Register"));
const RegisterStudent = lazy(() => import("./pages/auth/RegisterStudent"));
const RegisterClient = lazy(() => import("./pages/auth/RegisterClient"));
const StudentProfile = lazy(() => import("./pages/StudentProfile"));
const AdminDashboard = lazy(() => import("./pages/AdminDashboard"));
const ModeratorDashboard = lazy(() => import("./pages/ModeratorDashboard"));
const OrderPage = lazy(() => import("./pages/OrderPage"));
const MyOrders = lazy(() => import("./pages/MyOrders"));
const MyMissions = lazy(() => import("./pages/MyMissions"));
const MyGigs = lazy(() => import("./pages/MyGigs"));
const CreateGig = lazy(() => import("./pages/CreateGig"));
const Requests = lazy(() => import("./pages/Requests"));
const RequestDetail = lazy(() => import("./pages/RequestDetail"));
const MyRequests = lazy(() => import("./pages/MyRequests"));
const MyProposals = lazy(() => import("./pages/MyProposals"));
const Privacy = lazy(() => import("./pages/Privacy"));
const Terms = lazy(() => import("./pages/Terms"));

const queryClient = new QueryClient();

const PageLoader = () => (
  <div className="min-h-screen flex items-center justify-center">
    <Loader2 className="w-8 h-8 animate-spin text-primary" />
  </div>
);

const App = () => (
  <QueryClientProvider client={queryClient}>
    <ThemeProvider>
      <LanguageProvider>
        <AuthProvider>
          <NotificationProvider>
            <TooltipProvider>
              <Toaster />
              <Sonner />
              <BrowserRouter>
                <Suspense fallback={<PageLoader />}>
                  <Routes>
                    <Route path="/" element={<Index />} />
                    <Route path="/services" element={<Services />} />
                    <Route path="/comment-ca-marche" element={<HowItWorks />} />
                    <Route path="/connexion" element={<Login />} />
                    <Route path="/inscription" element={<Register />} />
                    <Route path="/inscription/etudiant" element={<RegisterStudent />} />
                    <Route path="/inscription/client" element={<RegisterClient />} />
                    <Route path="/profil/:id" element={<StudentProfile />} />
                    <Route path="/commander/:gigId" element={<OrderPage />} />
                    <Route path="/mes-commandes" element={<MyOrders />} />
                    <Route path="/mes-missions" element={<MyMissions />} />
                    <Route path="/mes-gigs" element={<MyGigs />} />
                    <Route path="/mes-gigs/creer" element={<CreateGig />} />
                    <Route path="/mes-gigs/:gigId/modifier" element={<CreateGig />} />
                    <Route path="/demandes" element={<Requests />} />
                    <Route path="/demandes/:id" element={<RequestDetail />} />
                    <Route path="/mes-demandes" element={<MyRequests />} />
                    <Route path="/mes-propositions" element={<MyProposals />} />
                    <Route path="/admin" element={<AdminDashboard />} />
                    <Route path="/moderateur" element={<ModeratorDashboard />} />
                    <Route path="/moderateur/signalements" element={<ModeratorDashboard />} />
                    <Route path="/confidentialite" element={<Privacy />} />
                    <Route path="/cgu" element={<Terms />} />
                    <Route path="*" element={<NotFound />} />
                  </Routes>
                </Suspense>
              </BrowserRouter>
            </TooltipProvider>
          </NotificationProvider>
        </AuthProvider>
      </LanguageProvider>
    </ThemeProvider>
  </QueryClientProvider>
);

export default App;
