// Adaptateurs React Query vers les contrats exposes par l'API Gateway.
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, authTokenStorage, uploadApiFile } from '@/lib/api';
import { useAuth } from '@/contexts/AuthContext';
import type { Gig, GigTier, Order, OrderStatus, ChatMessage, Review, Dispute, AbuseReport } from '@/types';
import type { BackendProfile, BackendVerificationRequest } from '@/types/backend';

type ApiGig = {
  id: string;
  studentId: string;
  studentName?: string;
  title: string;
  description?: string;
  category: string;
  location: string;
  rating?: number;
  reviewCount?: number;
  orderCount?: number;
  badge?: string;
  images?: string[];
  active: boolean;
  published: boolean;
  gpsLat?: number | null;
  gpsLng?: number | null;
  tierBasique: GigTier;
  tierStandard: GigTier;
  tierPremium: GigTier;
};

type CatalogOption = { id: string; name: string; active: boolean; icon?: string };

type GigMutationInput = {
  title: string;
  description: string;
  category: string;
  location: string;
  tier_basique: GigTier;
  tier_standard: GigTier;
  tier_premium: GigTier;
  images?: string[];
  published?: boolean;
};

function apiGigToGig(gig: ApiGig): Gig {
  return {
    id: gig.id,
    studentId: gig.studentId,
    studentName: gig.studentName || 'Étudiant',
    studentRating: Number(gig.rating) || 0,
    title: gig.title,
    description: gig.description || '',
    category: gig.category,
    location: gig.location,
    rating: Number(gig.rating) || 0,
    reviewCount: gig.reviewCount || 0,
    orderCount: gig.orderCount || 0,
    badge: gig.badge || 'Nouveau',
    images: gig.images || [],
    active: gig.active,
    published: gig.published,
    gpsLocation: gig.gpsLat != null && gig.gpsLng != null
      ? { lat: gig.gpsLat, lng: gig.gpsLng }
      : undefined,
    tiers: {
      basique: gig.tierBasique,
      standard: gig.tierStandard,
      premium: gig.tierPremium,
    },
  };
}

// ─── GIGS ───
export function useGigs(query?: string) {
  return useQuery({
    queryKey: ['gigs', query],
    refetchInterval: 5_000,
    refetchIntervalInBackground: true,
    queryFn: async () => {
      const params = query?.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
      const { data } = await api.get<ApiGig[]>(`/api/gigs${params}`);
      return data.map(apiGigToGig);
    },
  });
}

export function useGigById(id: string | undefined) {
  // Skip query for non-UUID mock IDs
  const isUUID = !!id && /^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(id);
  return useQuery({
    queryKey: ['gig', id],
    enabled: isUUID,
    queryFn: async () => {
      const { data } = await api.get<ApiGig>(`/api/gigs/${id}`);
      return apiGigToGig(data);
    },
  });
}

export function useMyGigs() {
  const { user } = useAuth();
  return useQuery({
    queryKey: ['my-gigs', user?.id],
    enabled: !!user,
    queryFn: async () => {
      const { data } = await api.get<ApiGig[]>('/api/gigs/mine');
      return data.map(apiGigToGig);
    },
  });
}

export function useCreateGig() {
  const qc = useQueryClient();
  // Ancien besoin : const { user } = useAuth(); studentId vient maintenant du JWT.
  return useMutation({
    mutationFn: async (gig: GigMutationInput) => {
      // Le backend déduit le propriétaire depuis le JWT.
      await api.post('/api/gigs', {
        title: gig.title,
        description: gig.description,
        category: gig.category,
        location: gig.location,
        tierBasique: gig.tier_basique,
        tierStandard: gig.tier_standard,
        tierPremium: gig.tier_premium,
        images: gig.images || [],
        published: gig.published ?? false,
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['my-gigs'] });
      qc.invalidateQueries({ queryKey: ['gigs'] });
    },
  });
}

export function useUpdateGig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, gig }: { id: string; gig: GigMutationInput }) => {
      const { data } = await api.put<ApiGig>(`/api/gigs/${id}`, {
        title: gig.title,
        description: gig.description,
        category: gig.category,
        location: gig.location,
        tierBasique: gig.tier_basique,
        tierStandard: gig.tier_standard,
        tierPremium: gig.tier_premium,
        images: gig.images || [],
        published: gig.published ?? false,
      });
      return apiGigToGig(data);
    },
    onSuccess: (gig) => {
      qc.invalidateQueries({ queryKey: ['my-gigs'] });
      qc.invalidateQueries({ queryKey: ['gigs'] });
      qc.setQueryData(['gig', gig.id], gig);
    },
  });
}

export function usePublishGig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, published }: { id: string; published: boolean }) => {
      await api.patch(`/api/gigs/${id}/publish`, { published });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['my-gigs'] });
      qc.invalidateQueries({ queryKey: ['gigs'] });
    },
  });
}

export function useToggleGig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => {
      await api.patch(`/api/gigs/${id}/active`, { active });
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['my-gigs'] }),
  });
}

export function useDeleteGig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/api/gigs/${id}`);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['my-gigs'] }),
  });
}

// ─── ORDERS ───
type ApiOrder = {
  id: string;
  gigId?: string | null;
  gigTitle: string;
  clientId: string;
  clientName: string;
  studentId: string;
  studentName: string;
  tier: string;
  description?: string | null;
  budget: number;
  status: OrderStatus;
  revisionsLeft?: number | null;
  deliveryDate?: string | null;
  createdAt: string;
  escrowAmount?: number | null;
  paymentMethod?: string | null;
  deliverableUrl?: string | null;
  deliverableNote?: string | null;
  deliveredAt?: string | null;
};

function apiOrderToOrder(r: ApiOrder): Order {
  return {
    id: r.id,
    gigId: r.gigId || '',
    gigTitle: r.gigTitle,
    clientId: r.clientId,
    clientName: r.clientName,
    studentId: r.studentId,
    studentName: r.studentName,
    tier: r.tier as 'basique' | 'standard' | 'premium',
    description: r.description || '',
    budget: Number(r.budget),
    status: r.status as OrderStatus,
    revisionsLeft: r.revisionsLeft ?? 2,
    deliveryDate: r.deliveryDate || '',
    createdAt: r.createdAt,
    escrowAmount: Number(r.escrowAmount ?? r.budget),
    paymentMethod: r.paymentMethod || '',
    deliverableUrl: r.deliverableUrl || undefined,
    deliverableNote: r.deliverableNote || undefined,
    delivered_at: r.deliveredAt || undefined,
  };
}

export function useMyOrders() {
  const { user } = useAuth();
  return useQuery({
    queryKey: ['my-orders', user?.id],
    enabled: !!user,
    // Le client doit voir une livraison effectuee depuis la session etudiante
    // sans devoir recharger manuellement la page.
    refetchInterval: 5_000,
    refetchIntervalInBackground: true,
    queryFn: async () => {
      const { data } = await api.get<ApiOrder[]>('/api/orders/mine');
      return data.map(apiOrderToOrder);
    },
  });
}

export function useMyMissions() {
  const { user } = useAuth();
  return useQuery({
    queryKey: ['my-missions', user?.id],
    enabled: !!user,
    // Le paiement et les decisions du client sont realises dans une autre
    // session : les missions sont donc resynchronisees regulierement.
    refetchInterval: 5_000,
    refetchIntervalInBackground: true,
    queryFn: async () => {
      const { data } = await api.get<ApiOrder[]>('/api/orders/missions');
      return data.map(apiOrderToOrder);
    },
  });
}

export function useCreateOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (order: {
      gig_id: string; gig_title: string; student_id: string; student_name: string;
      tier: string; description: string; budget: number;
      payment_method: string; delivery_date?: string;
    }) => {
      /* Business Service relit le gig et l'identite du JWT. */
      const { data } = await api.post<ApiOrder>('/api/orders', {
        gigId: order.gig_id,
        tier: order.tier,
        description: order.description,
        paymentMethod: order.payment_method,
      });
      return apiOrderToOrder(data);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['my-orders'] }),
  });
}

export function useUpdateOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, status, deliverable_url, deliverable_note, revisions_left }: {
      id: string; status?: string; deliverable_url?: string; deliverable_note?: string; revisions_left?: number;
    }) => {
      if (!status) throw new Error('Statut de commande manquant');
      // revisions_left est conserve dans la signature pour compatibilite, mais calcule par le backend.
      const { data } = await api.patch<ApiOrder>(`/api/orders/${id}/status`, {
        status,
        deliverableUrl: deliverable_url,
        deliverableNote: deliverable_note,
      });
      return apiOrderToOrder(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['my-orders'] });
      qc.invalidateQueries({ queryKey: ['my-missions'] });
    },
  });
}

export type PaymentApiResponse = {
  transactionId: string;
  orderId: string;
  status: string;
  externalReference?: string;
  message?: string;
  commission: number;
  netAmount: number;
  payoutReference?: string;
  ussdCode?: string;
};

export async function initiateOrderPayment(orderId: string, phone: string) {
  const { data } = await api.post<PaymentApiResponse>('/api/payments/initiate', { orderId, phone });
  return data;
}

export async function releaseOrderPayment(orderId: string) {
  const { data } = await api.post<PaymentApiResponse>(`/api/payments/order/${orderId}/release`);
  return data;
}

// ─── CHAT ───
export function useChatMessages(orderId: string | null) {
  return useQuery({
    queryKey: ['chat', orderId],
    enabled: !!orderId,
    queryFn: async () => {
      const { data } = await api.get<ChatMessage[]>(`/api/chat/orders/${orderId}/messages`);
      return data;
    },
  });
}

export function useSendMessage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ orderId, content }: { orderId: string; content: string }) => {
      const { data } = await api.post<ChatMessage>(`/api/chat/orders/${orderId}/messages`, { orderId, content });
      return data;
    },
    onSuccess: (_, vars) => qc.invalidateQueries({ queryKey: ['chat', vars.orderId] }),
  });
}

// ─── REVIEWS ───
type ApiReview = {
  id: string; orderId: string; gigId?: string | null; reviewerId: string;
  reviewerName: string; studentId: string; rating: number; text?: string | null;
  createdAt: string; reported: boolean;
};

function apiReviewToReview(r: ApiReview): Review {
  return {
    id: r.id, orderId: r.orderId, gigId: r.gigId || '', reviewerId: r.reviewerId,
    reviewerName: r.reviewerName, studentId: r.studentId, rating: r.rating,
    text: r.text || '', date: r.createdAt, reported: r.reported,
  };
}

export function useReviewsByStudent(studentId: string | undefined) {
  return useQuery({
    queryKey: ['reviews', studentId],
    enabled: !!studentId,
    queryFn: async () => {
      const { data } = await api.get<ApiReview[]>(`/api/reviews/student/${studentId}`);
      return data.map(apiReviewToReview);
    },
  });
}

export function useCreateReview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (review: { orderId: string; gigId: string; studentId: string; rating: number; text: string }) => {
      // gigId, studentId et auteur sont verifies ou derives de la commande cote backend.
      const { data } = await api.post<ApiReview>('/api/reviews', {
        orderId: review.orderId,
        rating: review.rating,
        text: review.text,
      });
      return apiReviewToReview(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reviews'] });
      qc.invalidateQueries({ queryKey: ['gigs'] });
      qc.invalidateQueries({ queryKey: ['profile'] });
    },
  });
}

// ─── DISPUTES ───
type ApiDispute = {
  id: string; orderId: string; gigTitle: string; clientId: string; clientName: string;
  clientStatement: string; studentId: string; studentName: string; studentStatement?: string | null;
  status: Dispute['status']; moderatorId?: string | null; moderatorNote?: string | null;
  createdAt: string; resolvedAt?: string | null; clientEvidenceUrl?: string | null;
  studentEvidenceUrl?: string | null;
};

function apiDisputeToDispute(d: ApiDispute): Dispute {
  return {
    id: d.id, orderId: d.orderId, gigTitle: d.gigTitle, clientId: d.clientId,
    clientName: d.clientName, clientStatement: d.clientStatement, studentId: d.studentId,
    studentName: d.studentName, studentStatement: d.studentStatement || '', status: d.status,
    moderatorId: d.moderatorId || undefined, moderatorNote: d.moderatorNote || undefined,
    createdAt: d.createdAt, resolvedAt: d.resolvedAt || undefined,
    clientEvidenceUrl: d.clientEvidenceUrl || undefined,
    studentEvidenceUrl: d.studentEvidenceUrl || undefined,
  };
}

export function useReportReview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ reviewId, reason }: { reviewId: string; reason: string }) => {
      const { data } = await api.patch<ApiReview>(`/api/reviews/${reviewId}/report`, { reason });
      return apiReviewToReview(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reviews'] });
      qc.invalidateQueries({ queryKey: ['reported-content'] });
    },
  });
}

export function useDisputes() {
  return useQuery({
    queryKey: ['disputes'],
    queryFn: async () => {
      const { data } = await api.get<ApiDispute[]>('/api/disputes');
      return data.map(apiDisputeToDispute);
    },
  });
}

export function useUpdateDispute() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, status, moderator_id, moderator_note, resolved_at, student_evidence_url, client_evidence_url }: {
      id: string; status?: string; moderator_id?: string; moderator_note?: string; resolved_at?: string; student_evidence_url?: string; client_evidence_url?: string;
    }) => {
      if (!status) throw new Error('Resolution manquante');
      const { data } = await api.patch<ApiDispute>(`/api/disputes/${id}`, {
        status,
        moderatorNote: moderator_note,
      });
      return apiDisputeToDispute(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['disputes'] });
      qc.invalidateQueries({ queryKey: ['my-orders'] });
      qc.invalidateQueries({ queryKey: ['my-missions'] });
    },
  });
}

export function useCreateDispute() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (d: { orderId: string; gigTitle: string; studentId: string; studentName: string; clientStatement: string; clientEvidenceUrl?: string }) => {
      const { data } = await api.post<ApiDispute>('/api/disputes', {
        orderId: d.orderId,
        clientStatement: d.clientStatement,
        clientEvidenceUrl: d.clientEvidenceUrl,
      });
      return apiDisputeToDispute(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['my-orders'] });
      qc.invalidateQueries({ queryKey: ['disputes'] });
    },
  });
}

// --- ABUSE REPORTS ---
export function useCreateAbuseReport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (request: {
      disputeId: string;
      targetUserId: string;
      reason: AbuseReport['reason'];
      note: string;
    }) => {
      const { data } = await api.post<AbuseReport>('/api/abuse-reports', request);
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['abuse-reports'] }),
  });
}

export function useAbuseReports() {
  return useQuery({
    queryKey: ['abuse-reports'],
    queryFn: async () => {
      const { data } = await api.get<AbuseReport[]>('/api/admin/abuse-reports');
      return data;
    },
  });
}

export function useDecideAbuseReport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ reportId, action, adminNote }: {
      reportId: string;
      action: 'DISMISS' | 'WARN' | 'BAN';
      adminNote: string;
    }) => {
      const { data } = await api.patch<AbuseReport>(`/api/admin/abuse-reports/${reportId}`, {
        action,
        adminNote,
      });
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['abuse-reports'] }),
  });
}

// ─── VERIFICATION REQUESTS ───
export function useVerificationRequests() {
  return useQuery({
    queryKey: ['verifications'],
    queryFn: async () => {
      const { data } = await api.get<BackendVerificationRequest[]>('/api/admin/verifications');
      return data;
    },
  });
}

export function useUpdateVerification() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, status }: { id: string; status: string }) => {
      // L'ancien code mettait a jour verification_requests puis profiles dans le navigateur.
      // Le backend realise maintenant les deux operations dans une transaction.
      await api.patch(`/api/admin/verifications/${id}`, { status });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['verifications'] });
      qc.invalidateQueries({ queryKey: ['profiles'] });
      qc.invalidateQueries({ queryKey: ['all-profiles'] });
    },
  });
}

// Dossiers de verification de l'etudiant connecte (pour savoir s'il en a
// deja un en attente avant de proposer d'en soumettre un nouveau).
export function useMyVerifications() {
  return useQuery({
    queryKey: ['my-verifications'],
    queryFn: async () => {
      const { data } = await api.get<BackendVerificationRequest[]>('/api/verifications/me');
      return data;
    },
  });
}

export function useCreateVerification() {
  const qc = useQueryClient();
  // Ancien besoin : const { user } = useAuth(); l'identite est maintenant lue dans le JWT.
  return useMutation({
    mutationFn: async (req: {
      university: string; id_type: string;
      id_file_url: string; student_card_url: string; selfie_url: string;
    }) => {
      // L'identite vient des claims JWT cote serveur.
      await api.post('/api/verifications', req);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['verifications'] });
      qc.invalidateQueries({ queryKey: ['my-verifications'] });
    },
  });
}

// ─── CATEGORIES & CITIES ───
export function useCategories() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: async () => {
      const { data } = await api.get<CatalogOption[]>('/api/categories');
      return data;
    },
    retry: 5,
    refetchOnMount: 'always',
  });
}

export function useCities() {
  return useQuery({
    queryKey: ['cities'],
    queryFn: async () => {
      const { data } = await api.get<CatalogOption[]>('/api/cities');
      return data;
    },
    retry: 5,
    refetchOnMount: 'always',
  });
}

// ─── PROFILES ───
export function useProfile(userId: string | undefined) {
  const { user: currentUser } = useAuth();
  return useQuery({
    queryKey: ['profile', userId],
    enabled: !!userId,
    queryFn: async () => {
      const endpoint = currentUser?.id === userId
        ? '/api/profiles/me'
        : `/api/profiles/${userId}`;
      const { data: profile } = await api.get<BackendProfile>(endpoint);
      if (profile.role?.toLowerCase() !== 'student') return profile;

      try {
        const { data: statistics } = await api.get<Pick<BackendProfile,
          'completed_jobs' | 'review_count' | 'rating' | 'response_time' |
          'level_badge' | 'xp' | 'next_level_xp'>>(`/api/student-stats/${userId}`);
        return { ...profile, ...statistics };
      } catch {
        // Le profil Identity reste affichable si Business est indisponible ou
        // si une ancienne Gateway ne route pas encore les statistiques.
        return profile;
      }
    },
  });
}

export function useAllProfiles() {
  return useQuery({
    queryKey: ['all-profiles'],
    queryFn: async () => {
      const { data } = await api.get<BackendProfile[]>('/api/admin/profiles');
      return data;
    },
  });
}

export function useAllOrders() {
  return useQuery({
    queryKey: ['all-orders'],
    queryFn: async () => {
      const { data } = await api.get<ApiOrder[]>('/api/orders/all');
      return data.map(apiOrderToOrder);
    },
  });
}

export function useStudentIncome(studentId: string | undefined) {
  const { user } = useAuth();
  return useQuery({
    queryKey: ['student-income', studentId],
    // Les revenus sont prives : seul l'etudiant connecte consulte ses missions.
    enabled: !!studentId && user?.id === studentId && user?.role === 'student',
    queryFn: async () => {
      const { data } = await api.get<ApiOrder[]>('/api/orders/missions');

      const completed = data
        .filter(o => o.status === 'completed')
        .reduce((sum, o) => sum + Number(o.budget), 0);

      const pending = data
        .filter(o => ['accepted', 'in_progress', 'delivered', 'revision_requested', 'disputed'].includes(o.status))
        .reduce((sum, o) => sum + Number(o.budget), 0);

      return { completed, pending, count: data.filter(o => o.status === 'completed').length };
    },
  });
}

// ─── REPORTED CONTENT ───
export function useReportedContent() {
  return useQuery({
    queryKey: ['reported-content'],
    queryFn: async () => {
      const { data } = await api.get<ApiReview[]>('/api/reviews/reported');
      // Le tableau admin historique attend encore le nom snake_case.
      return data.map(review => ({ ...review, reviewer_name: review.reviewerName }));
    },
  });
}

// ─── ADMIN: CATEGORIES MUTATIONS ───
export function useCreateCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (name: string) => {
      await api.post('/api/categories', { name });
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categories'] }),
  });
}

export function useToggleCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => {
      await api.patch(`/api/categories/${id}`, { active });
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categories'] }),
  });
}

export function useDeleteCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/api/categories/${id}`);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categories'] }),
  });
}

// ─── ADMIN: CITIES MUTATIONS ───
export function useCreateCity() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (name: string) => {
      await api.post('/api/cities', { name });
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cities'] }),
  });
}

export function useDeleteCity() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/api/cities/${id}`);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cities'] }),
  });
}

// ─── ADMIN: USER MANAGEMENT ───
export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ userId, updates }: { userId: string; updates: { banned?: boolean; verified?: boolean } }) => {
      await api.patch(`/api/admin/profiles/${userId}`, updates);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['all-profiles'] }),
  });
}

// ─── ADMIN: ALL CITIES (including inactive) ───
export function useAllCities() {
  return useQuery({
    queryKey: ['all-cities'],
    queryFn: async () => {
      const { data } = await api.get<CatalogOption[]>('/api/cities?includeInactive=true');
      return data;
    },
  });
}

// ─── FILE UPLOAD ───
export async function uploadFile(bucket: string, path: string, file: File) {
  return uploadApiFile(file, 'public', bucket);
}

export function useModerateReview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ reviewId, action, note }: {
      reviewId: string; action: 'HIDE' | 'DISMISS'; note?: string;
    }) => {
      const { data } = await api.patch<ApiReview>(`/api/reviews/${reviewId}/moderate`, { action, note });
      return data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reported-content'] });
      qc.invalidateQueries({ queryKey: ['reviews'] });
      qc.invalidateQueries({ queryKey: ['gigs'] });
      qc.invalidateQueries({ queryKey: ['profile'] });
    },
  });
}

export async function uploadPrivateFile(bucket: string, path: string, file: File) {
  const parts = path.split('/');
  const resourceId = parts.find((value, index) => index > 0 && /^[0-9a-f-]{36}$/i.test(value));
  return uploadApiFile(file, 'private', bucket, resourceId);
}

export function getStoragePathFromUrl(bucket: string, pathOrUrl: string) {
  if (!pathOrUrl) return "";

  try {
    const url = new URL(pathOrUrl);
    const decodedPath = decodeURIComponent(url.pathname);
    const markers = [
      `/storage/v1/object/public/${bucket}/`,
      `/storage/v1/object/sign/${bucket}/`,
      `/storage/v1/object/authenticated/${bucket}/`,
    ];
    const marker = markers.find((m) => decodedPath.includes(m));
    if (marker) return decodedPath.slice(decodedPath.indexOf(marker) + marker.length);
  } catch {
    // Raw storage paths are expected for private files.
  }

  const cleanPath = pathOrUrl.split("?")[0].replace(/^\/+/, "");
  return cleanPath.startsWith(`${bucket}/`) ? cleanPath.slice(bucket.length + 1) : cleanPath;
}

export async function getSignedFileUrl(bucket: string, pathOrUrl: string, expiresIn = 3600) {
  // Support Service controle l'acces a chaque requete privee.
  const filename = pathOrUrl.split('?')[0].split('/').filter(Boolean).pop();
  if (!filename) throw new Error("Chemin de fichier invalide");
  const url = /^https?:\/\//i.test(pathOrUrl)
    ? pathOrUrl
    : new URL(`/api/storage/private/files/${filename}`, api.defaults.baseURL).toString();
  const token = authTokenStorage.getAccessToken();
  const response = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : undefined });
  if (!response.ok) throw new Error(`Acces au fichier refuse (${response.status})`);
  return URL.createObjectURL(await response.blob());
}

export function useRespondToDispute() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ orderId, statement, evidenceUrl }: { orderId: string; statement: string; evidenceUrl?: string }) => {
      const { data } = await api.patch<ApiDispute>(`/api/disputes/order/${orderId}/response`, {
        statement,
        evidenceUrl,
      });
      return apiDisputeToDispute(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['disputes'] });
      qc.invalidateQueries({ queryKey: ['my-missions'] });
    },
  });
}

// ─── REALTIME SUBSCRIPTIONS ───
export function subscribeToDisputes(callback: () => void) {
  const interval = window.setInterval(callback, 10_000);
  return () => window.clearInterval(interval);
}

export function subscribeToChatMessages(orderId: string, callback: (msg: unknown) => void) {
  // Rafraichissement REST temporaire; le backend WebSocket STOMP reste disponible.
  const interval = window.setInterval(() => callback({ orderId }), 3_000);
  return () => window.clearInterval(interval);
}

// ─── GIG REQUESTS (Client open requests) ───
export interface GigRequest {
  id: string;
  client_id: string;
  client_name: string;
  title: string;
  description: string;
  category: string | null;
  location: string | null;
  budget: number;
  deadline: string | null;
  status: string;
  accepted_proposal_id: string | null;
  created_at: string;
}

type ApiGigRequest = {
  id: string;
  clientId: string;
  clientName: string;
  title: string;
  description?: string | null;
  category?: string | null;
  location?: string | null;
  budget: number;
  deadline?: string | null;
  status: string;
  acceptedProposalId?: string | null;
  createdAt: string;
};

function apiRequestToGigRequest(request: ApiGigRequest): GigRequest {
  return {
    id: request.id,
    client_id: request.clientId,
    client_name: request.clientName,
    title: request.title,
    description: request.description || '',
    category: request.category || null,
    location: request.location || null,
    budget: Number(request.budget),
    deadline: request.deadline || null,
    status: request.status,
    accepted_proposal_id: request.acceptedProposalId || null,
    created_at: request.createdAt,
  };
}

export function useGigRequests() {
  return useQuery({
    queryKey: ['gig-requests'],
    queryFn: async () => {
      const { data } = await api.get<ApiGigRequest[]>('/api/v1/requests');
      return data.map(apiRequestToGigRequest);
    },
  });
}

export function useGigRequest(requestId: string | undefined) {
  return useQuery({
    queryKey: ['gig-request', requestId],
    enabled: !!requestId,
    queryFn: async () => {
      const { data } = await api.get<ApiGigRequest>(`/api/v1/requests/${requestId}`);
      return apiRequestToGigRequest(data);
    },
  });
}

export function useMyRequests() {
  const { user } = useAuth();
  return useQuery({
    queryKey: ['my-requests', user?.id],
    enabled: !!user,
    queryFn: async () => {
      // L'identifiant du client est maintenant extrait du JWT par la Gateway.
      const { data } = await api.get<ApiGigRequest[]>('/api/v1/requests/mine');
      return data.map(apiRequestToGigRequest);
    },
  });
}

export function useCreateGigRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (r: { title: string; description: string; category: string; location: string; budget: number; deadline?: string }) => {
      /* Le backend utilise le JWT et le profil Identity. */
      const { data } = await api.post<ApiGigRequest>('/api/v1/requests', {
        title: r.title,
        description: r.description,
        category: r.category,
        location: r.location,
        budget: r.budget,
        deadline: r.deadline || null,
      });
      return apiRequestToGigRequest(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['gig-requests'] });
      qc.invalidateQueries({ queryKey: ['my-requests'] });
    },
  });
}

export function useCancelGigRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const { data } = await api.patch<ApiGigRequest>(`/api/v1/requests/${id}/cancel`);
      return apiRequestToGigRequest(data);
    },
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: ['gig-requests'] });
      qc.invalidateQueries({ queryKey: ['my-requests'] });
      qc.invalidateQueries({ queryKey: ['gig-request', id] });
      qc.invalidateQueries({ queryKey: ['proposals', id] });
    },
  });
}

// ─── PROPOSALS ───
export interface RequestProposal {
  id: string;
  request_id: string;
  student_id: string;
  student_name: string;
  price: number;
  delivery_days: number;
  message: string;
  status: string;
  created_at: string;
  gig_requests?: { title: string; budget: number; status: string };
}

type ApiRequestProposal = {
  id: string;
  requestId: string;
  studentId: string;
  studentName: string;
  price: number;
  deliveryDays: number;
  message?: string | null;
  status: string;
  createdAt: string;
  requestTitle?: string | null;
  requestBudget?: number | null;
  requestStatus?: string | null;
};

function apiProposalToRequestProposal(proposal: ApiRequestProposal): RequestProposal {
  return {
    id: proposal.id,
    request_id: proposal.requestId,
    student_id: proposal.studentId,
    student_name: proposal.studentName,
    price: Number(proposal.price),
    delivery_days: proposal.deliveryDays,
    message: proposal.message || '',
    status: proposal.status,
    created_at: proposal.createdAt,
    gig_requests: proposal.requestTitle
      ? {
          title: proposal.requestTitle,
          budget: Number(proposal.requestBudget) || 0,
          status: proposal.requestStatus || 'open',
        }
      : undefined,
  };
}

export function useProposals(requestId: string | undefined) {
  const { user } = useAuth();
  return useQuery({
    queryKey: ['proposals', requestId],
    // Les propositions sont privees : client proprietaire ou etudiant concerne.
    enabled: !!requestId && !!user,
    queryFn: async () => {
      const { data } = await api.get<ApiRequestProposal[]>(`/api/v1/proposals/request/${requestId}`);
      return data.map(apiProposalToRequestProposal);
    },
  });
}

export function useMyProposals() {
  const { user } = useAuth();
  return useQuery({
    queryKey: ['my-proposals', user?.id],
    enabled: !!user,
    queryFn: async () => {
      const { data } = await api.get<ApiRequestProposal[]>('/api/v1/proposals/mine');
      return data.map(apiProposalToRequestProposal);
    },
  });
}

export function useCreateProposal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (p: { request_id: string; price: number; delivery_days: number; message: string }) => {
      // studentId/studentName ne quittent plus le navigateur : le JWT fait foi.
      const { data } = await api.post<ApiRequestProposal>('/api/v1/proposals', {
        requestId: p.request_id,
        price: p.price,
        deliveryDays: p.delivery_days,
        message: p.message,
      });
      return apiProposalToRequestProposal(data);
    },
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['proposals', vars.request_id] });
      qc.invalidateQueries({ queryKey: ['my-proposals'] });
    },
  });
}

export function useAcceptProposal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ proposalId, requestId }: { proposalId: string; requestId: string }) => {
      /* Request Service accepte atomiquement puis cree une commande idempotente
         dans Business Service avec un jeton inter-service. */
      const { data } = await api.put<ApiRequestProposal>(`/api/v1/proposals/${proposalId}/accept`);
      return apiProposalToRequestProposal(data);
    },
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['proposals', vars.requestId] });
      qc.invalidateQueries({ queryKey: ['gig-request', vars.requestId] });
      qc.invalidateQueries({ queryKey: ['my-requests'] });
      qc.invalidateQueries({ queryKey: ['gig-requests'] });
      qc.invalidateQueries({ queryKey: ['my-proposals'] });
      qc.invalidateQueries({ queryKey: ['my-orders'] });
      qc.invalidateQueries({ queryKey: ['my-missions'] });
    },
  });
}
