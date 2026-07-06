/** Profil renvoye par Identity Service. */
export interface BackendProfile {
  id: string;
  user_id: string;
  first_name: string;
  last_name: string;
  email: string;
  phone: string | null;
  avatar_url: string | null;
  bio: string | null;
  city: string | null;
  university: string | null;
  faculty: string | null;
  level: string | null;
  skills: string[] | null;
  rating: number | null;
  role: string;
  verified: boolean | null;
  banned: boolean | null;
  created_at: string;
  updated_at: string;

  // Statistiques attendues par le profil et destinees a l'agregateur metier.
  completed_jobs?: number | null;
  review_count?: number | null;
  response_time?: string | null;
  level_badge?: string | null;
  xp?: number | null;
  next_level_xp?: number | null;
  availability?: string[] | null;
  gps_lat?: number | null;
  gps_lng?: number | null;
  email_notifications_enabled?: boolean | null;
}

/** Demande KYC renvoyee par Identity Service. */
export interface BackendVerificationRequest {
  id: string;
  student_id: string;
  student_name: string;
  email: string | null;
  university: string | null;
  id_type: string | null;
  id_file_url: string | null;
  selfie_url: string | null;
  student_card_url: string | null;
  status: string;
  submitted_at: string;
  reviewed_at?: string | null;
}
