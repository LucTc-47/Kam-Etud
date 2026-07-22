import { createContext, useContext, useEffect, useState, ReactNode } from "react";
import { api, authTokenStorage } from "@/lib/api";
import { getErrorMessage } from "@/lib/utils";

export type UserRole = "client" | "student" | "admin" | "moderator";

export interface AppUser {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  role: UserRole;
  city: string;
  avatar?: string;
  verified: boolean;
  banned: boolean;
  createdAt: string;
}

interface AppSession {
  accessToken: string;
  refreshToken?: string;
}

interface ApiProfile {
  user_id: string;
  first_name?: string | null;
  last_name?: string | null;
  email: string;
  phone?: string | null;
  role?: string | null;
  city?: string | null;
  avatar_url?: string | null;
  verified?: boolean | null;
  banned?: boolean | null;
  created_at?: string | null;
}

interface AuthApiResponse {
  token: string;
  refresh_token?: string;
  profile: ApiProfile;
}

interface RegisterData {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone?: string;
  role?: UserRole;
  city?: string;
  university?: string;
  faculty?: string;
  level?: string;
  bio?: string;
  skills?: string[];
}

interface AuthActionResult {
  success: boolean;
  error?: string;
  // Permet a la page de connexion de rediriger selon le role sans attendre
  // la mise a jour asynchrone de l'etat `user`.
  role?: UserRole;
}

interface AuthContextType {
  user: AppUser | null;
  session: AppSession | null;
  login: (email: string, password: string) => Promise<AuthActionResult>;
  loginWithPhone: (phone: string, otp: string) => Promise<boolean>;
  sendPhoneOtp: (phone: string) => Promise<boolean>;
  register: (userData: RegisterData) => Promise<AuthActionResult & { userId?: string }>;
  logout: () => void;
  isAuthenticated: boolean;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

function toAppUser(profile: ApiProfile): AppUser {
  return {
    id: profile.user_id,
    firstName: profile.first_name ?? "",
    lastName: profile.last_name ?? "",
    email: profile.email,
    phone: profile.phone ?? "",
    role: (profile.role?.toLowerCase() as UserRole) ?? "client",
    city: profile.city ?? "",
    avatar: profile.avatar_url ?? undefined,
    verified: Boolean(profile.verified),
    banned: Boolean(profile.banned),
    createdAt: profile.created_at ?? new Date().toISOString(),
  };
}

async function fetchProfile(): Promise<AppUser> {
  const { data } = await api.get<ApiProfile>("/api/profiles/me");
  return toAppUser(data);
}

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<AppUser | null>(null);
  const [session, setSession] = useState<AppSession | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Restaure la session a partir des JWT locaux.
    const accessToken = authTokenStorage.getAccessToken();
    const refreshToken = authTokenStorage.getRefreshToken() ?? undefined;
    if (!accessToken) {
      setLoading(false);
      return;
    }

    setSession({ accessToken, refreshToken });
    const restoreSession = async () => {
      try {
        setUser(await fetchProfile());
      } catch {
        try {
          if (!refreshToken) throw new Error("Refresh token absent");
          const { data } = await api.post<AuthApiResponse>("/api/auth/refresh", {
            refreshToken,
          });
          authTokenStorage.setTokens(data.token, data.refresh_token);
          setSession({ accessToken: data.token, refreshToken: data.refresh_token });
          setUser(toAppUser(data.profile));
        } catch {
          authTokenStorage.clear();
          setSession(null);
          setUser(null);
        }
      } finally {
        setLoading(false);
      }
    };
    void restoreSession();
  }, []);

  const applyAuthentication = (response: AuthApiResponse) => {
    authTokenStorage.setTokens(response.token, response.refresh_token);
    setSession({ accessToken: response.token, refreshToken: response.refresh_token });
    setUser(toAppUser(response.profile));
  };

  const login = async (email: string, password: string): Promise<AuthActionResult> => {
    try {
      const { data } = await api.post<AuthApiResponse>("/api/auth/login", {
        email: email.trim().toLowerCase(),
        password,
      });
      applyAuthentication(data);
      return { success: true, role: toAppUser(data.profile).role };
    } catch (error: unknown) {
      return { success: false, error: getErrorMessage(error) };
    }
  };

  const sendPhoneOtp = async (_phone: string): Promise<boolean> => {
    // Ce flux sera connecte a un fournisseur SMS backend.
    return false;
  };

  const loginWithPhone = async (_phone: string, _otp: string): Promise<boolean> => {
    // La verification reste desactivee tant que l'endpoint SMS n'existe pas.
    return false;
  };

  const register = async (userData: RegisterData): Promise<AuthActionResult & { userId?: string }> => {
    try {
      const { data } = await api.post<AuthApiResponse>("/api/auth/register", {
        ...userData,
        email: userData.email.trim().toLowerCase(),
        role: (userData.role ?? "client").toUpperCase(),
      });
      applyAuthentication(data);
      return { success: true, userId: data.profile.user_id };
    } catch (error: unknown) {
      return { success: false, error: getErrorMessage(error) };
    }
  };

  const logout = () => {
    authTokenStorage.clear();
    setUser(null);
    setSession(null);
  };

  return (
    <AuthContext.Provider value={{
      user, session, login, loginWithPhone, sendPhoneOtp, register, logout,
      isAuthenticated: Boolean(user), loading,
    }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
};
