import { useState, useRef } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { motion } from "framer-motion";
import {
  Star, MapPin, GraduationCap, Calendar, Download, Award, Shield, Clock, MessageSquare, Loader2, Camera, Trash2, Check, X, Flag,
} from "lucide-react";
import Layout from "@/components/layout/Layout";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Progress } from "@/components/ui/progress";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useProfile, useGigs, useReviewsByStudent, useStudentIncome, useReportReview } from "@/hooks/useBackendData";
import { useAuth } from "@/contexts/AuthContext";
import { api, uploadApiFile } from "@/lib/api";
import { useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { useLanguage } from "@/contexts/LanguageContext";
import jsPDF from "jspdf";
import autoTable from "jspdf-autotable";
import { getErrorMessage, validateCameroonPhone } from "@/lib/utils";


const StudentProfile = () => {
  const { t, locale, language } = useLanguage();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const reportReview = useReportReview();

  const levelColors: Record<string, string> = {
    [t.lvl_beginner]: "bg-muted text-muted-foreground",
    [t.lvl_intermediate]: "bg-primary/10 text-primary",
    [t.lvl_advanced]: "bg-secondary/20 text-secondary-foreground",
    [t.lvl_expert]: "bg-gradient-gold text-primary-foreground",
  };
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: profile, isLoading } = useProfile(id);
  const { data: allGigs } = useGigs();
  const { data: dbReviews } = useReviewsByStudent(id);
  const { data: income } = useStudentIncome(id);
  const { user } = useAuth();
  const isOwner = !!user && user.id === id;
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [previewFile, setPreviewFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [newPayoutPhone, setNewPayoutPhone] = useState("");
  const [updatingPayout, setUpdatingPayout] = useState(false);

  const handleUpdatePayoutPhone = async () => {
    if (!user || !newPayoutPhone.trim()) return;

    if (!validateCameroonPhone(newPayoutPhone)) {
      toast({ title: t.c_error, description: "Format de numéro invalide.", variant: "destructive" });
      return;
    }

    setUpdatingPayout(true);
    try {
      await api.put("/api/profiles/me", { phone: newPayoutPhone });
      toast({ title: t.inc_payout_ok });
      queryClient.invalidateQueries({ queryKey: ["profile", id] });
    } catch (err: unknown) {
      toast({ title: "Erreur", description: getErrorMessage(err), variant: "destructive" });
    } finally {
      setUpdatingPayout(false);
    }
  };

  const ALLOWED_TYPES = ["image/jpeg", "image/jpg", "image/png"];
  const MAX_SIZE = 5 * 1024 * 1024;

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !user) return;
    if (!ALLOWED_TYPES.includes(file.type)) {
      toast({ title: t.pf_format_invalid, description: t.pf_format_d, variant: "destructive" });
      if (fileInputRef.current) fileInputRef.current.value = "";
      return;
    }
    if (file.size > MAX_SIZE) {
      toast({ title: t.pf_too_big, description: t.pf_too_big_d, variant: "destructive" });
      if (fileInputRef.current) fileInputRef.current.value = "";
      return;
    }
    setPreviewFile(file);
    setPreviewUrl(URL.createObjectURL(file));
  };

  const cancelPreview = () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setPreviewFile(null);
    setPreviewUrl(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const confirmAvatarUpload = async () => {
    if (!previewFile || !user) return;
    setUploading(true);
    try {
      const avatarUrl = await uploadApiFile(previewFile);
      await api.put("/api/profiles/me", { avatarUrl });
      toast({ title: t.pf_photo_ok });
      queryClient.invalidateQueries({ queryKey: ["profile", id] });
      cancelPreview();
    } catch (err: unknown) {
      toast({ title: t.pf_up_err, description: getErrorMessage(err), variant: "destructive" });
    } finally {
      setUploading(false);
    }
  };

  const handleDeleteAvatar = async () => {
    if (!user || !profile?.avatar_url) return;
    if (!window.confirm(t.pf_del_confirm)) return;
    setUploading(true);
    try {
      await api.put("/api/profiles/me", { removeAvatar: true });
      toast({ title: t.pf_photo_del });
      queryClient.invalidateQueries({ queryKey: ["profile", id] });
    } catch (err: unknown) {
      toast({ title: t.pf_del_err, description: getErrorMessage(err), variant: "destructive" });
    } finally {
      setUploading(false);
    }
  };

  if (isLoading) return <Layout><div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div></Layout>;

  if (!profile) {
    return (
      <Layout>
        <div className="container mx-auto px-4 py-16 text-center">
          <p className="text-muted-foreground">{t.nf_title}</p>
          <Button asChild className="mt-4 bg-gradient-hero">
            <Link to="/services">{t.c_back}</Link>
          </Button>
        </div>
      </Layout>
    );
  }
  const getLevelLabel = (lvl: string) => {
    if (lvl === "Débutant") return t.lvl_beginner;
    if (lvl === "Intermédiaire") return t.lvl_intermediate;
    if (lvl === "Avancé") return t.lvl_advanced;
    if (lvl === "Expert") return t.lvl_expert;
    return lvl;
  };

  const s = profile ? {
    name: `${profile.first_name} ${profile.last_name}`.trim(),
    avatar: profile.avatar_url || '',
    university: profile.university || t.pf_not_set,
    faculty: profile.faculty || '',
    level: profile.level || '',
    city: profile.city || '',
    bio: profile.bio || '',
    rating: Number(profile.rating) || 0,
    reviewCount: profile.review_count || 0,
    completedJobs: profile.completed_jobs || 0,
    responseTime: profile.response_time || '< 24h',
    memberSince: new Date(profile.created_at).toLocaleDateString(locale, { month: 'long', year: 'numeric' }),
    verified: profile.verified || false,
    levelBadge: getLevelLabel(profile.level_badge || 'Débutant'),
    xp: profile.xp || 0,
    nextLevelXp: profile.next_level_xp || 100,
    skills: profile.skills || [],
    phone: '',
  } : {
    name: t.student, avatar: '', university: '', faculty: '', level: '', city: '',
    bio: '', rating: 0, reviewCount: 0, completedJobs: 0, responseTime: '< 24h',
    memberSince: '', verified: false, levelBadge: t.lvl_beginner, xp: 0, nextLevelXp: 100, skills: [], phone: '',
  };

  const studentGigs = allGigs?.filter(g => g.studentId === id) || [];
  const studentReviews = dbReviews || [];

  const handleDownloadCV = () => {
    const doc = new jsPDF();
    // Header bar
    doc.setFillColor(16, 122, 87);
    doc.rect(0, 0, 210, 32, "F");
    doc.setTextColor(255, 255, 255);
    doc.setFont("helvetica", "bold"); doc.setFontSize(20);
    doc.text(t.pf_cv_title, 14, 14);
    doc.setFont("helvetica", "normal"); doc.setFontSize(11);
    doc.text(`${t.pf_cv_gen} ${new Date().toLocaleDateString(locale)}`, 14, 22);

    // Identity
    doc.setTextColor(20, 20, 20);
    doc.setFont("helvetica", "bold"); doc.setFontSize(18);
    doc.text(s.name, 14, 46);
    doc.setFont("helvetica", "normal"); doc.setFontSize(11);
    doc.setTextColor(90, 90, 90);
    doc.text(`${s.university}${s.faculty ? " — " + s.faculty : ""}${s.level ? " (" + s.level + ")" : ""}`, 14, 53);
    doc.text(`${t.pf_cv_city} ${s.city || "—"}   ·   ${t.pf_member_since} ${s.memberSince}`, 14, 60);
    if (s.verified) {
      const verifiedLabel = t.verified.replace(/[✓✔]/g, "").trim();
      doc.setTextColor(16, 122, 87);
      doc.text(verifiedLabel || t.pf_verified, 14, 67);
    }

    let y = 78;
    doc.setTextColor(20, 20, 20);
    if (s.bio) {
      doc.setFont("helvetica", "bold"); doc.setFontSize(12); doc.text(t.pf_cv_intro, 14, y); y += 6;
      doc.setFont("helvetica", "normal"); doc.setFontSize(10);
      const bio = doc.splitTextToSize(s.bio, 180);
      doc.text(bio, 14, y); y += bio.length * 5 + 6;
    }
    if (s.skills.length) {
      doc.setFont("helvetica", "bold"); doc.setFontSize(12); doc.text(t.pf_cv_skills, 14, y); y += 6;
      doc.setFont("helvetica", "normal"); doc.setFontSize(10);
      const sk = doc.splitTextToSize(s.skills.join(" • "), 180);
      doc.text(sk, 14, y); y += sk.length * 5 + 6;
    }
    autoTable(doc, {
      startY: y,
      head: [[t.c_stats, t.c_value]],
      body: [
        [t.pf_cv_jobs, String(s.completedJobs)],
        [t.pf_cv_rating, `${s.rating}/5 (${s.reviewCount} ${t.pf_stat_reviews})`],
        [t.pf_cv_level, s.levelBadge],
        [t.pf_cv_response, s.responseTime],
      ],
      theme: "striped",
      headStyles: { fillColor: [16, 122, 87] },
    });
    if (studentGigs.length) {
      autoTable(doc, {
        head: [[t.pf_cv_offered, t.pf_cv_from]],
        body: studentGigs.map((g) => [g.title, `${g.tiers.basique.price.toLocaleString().replace(/[\u00A0\u202F]/g, " ")} FCFA`]),
        theme: "striped",
        headStyles: { fillColor: [212, 168, 65] },
      });
    }
    doc.setFontSize(9); doc.setTextColor(140, 140, 140);
    doc.text(t.pf_cv_footer, 14, 287);
    doc.save(`CV_KamEtud_${s.name.replace(/\s/g, "_")}.pdf`);
  };

  return (
    <Layout>
      <div className="container mx-auto px-4 py-8 max-w-5xl">
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
          <Card className="shadow-elevated border-border/50 overflow-hidden">
            <div className="h-32 bg-gradient-hero" />
            <CardContent className="relative pt-0 pb-6">
              <div className="flex flex-col sm:flex-row gap-4 -mt-12">
                <div className="relative">
                  <Avatar className="w-24 h-24 border-4 border-background shadow-lg">
                    <AvatarImage src={previewUrl || s.avatar} />
                    <AvatarFallback className="bg-gradient-hero text-primary-foreground text-2xl font-display">{s.name.split(" ").map(n => n[0]).join("")}</AvatarFallback>
                  </Avatar>
                  {isOwner && (
                    <>
                      {!previewFile && (
                        <button
                          type="button"
                          onClick={() => fileInputRef.current?.click()}
                          disabled={uploading}
                          className="absolute bottom-0 right-0 w-8 h-8 rounded-full bg-primary text-primary-foreground shadow-lg flex items-center justify-center hover:bg-primary/90 transition disabled:opacity-50"
                          title={t.pf_change_photo}
                        >
                          {uploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Camera className="w-4 h-4" />}
                        </button>
                      )}
                      <input ref={fileInputRef} type="file" accept="image/jpeg,image/png" className="hidden" onChange={handleFileSelect} />
                      {previewFile && (
                        <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 flex gap-1">
                          <button type="button" onClick={confirmAvatarUpload} disabled={uploading}
                            className="w-8 h-8 rounded-full bg-primary text-primary-foreground shadow-lg flex items-center justify-center hover:bg-primary/90 disabled:opacity-50"
                            title={t.c_confirm}>
                            {uploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                          </button>
                          <button type="button" onClick={cancelPreview} disabled={uploading}
                            className="w-8 h-8 rounded-full bg-destructive text-destructive-foreground shadow-lg flex items-center justify-center hover:bg-destructive/90 disabled:opacity-50"
                            title={t.c_cancel}>
                            <X className="w-4 h-4" />
                          </button>
                        </div>
                      )}
                    </>
                  )}
                </div>
                <div className="flex-1 pt-2 sm:pt-4">
                  <div className="flex flex-wrap items-center gap-2 mb-1">
                    <h1 className="text-2xl font-display font-bold text-foreground">{s.name}</h1>
                    {s.verified && <Badge className="bg-primary/10 text-primary border-primary/20"><Shield className="w-3 h-3 mr-1" /> {t.pf_verified}</Badge>}
                    <Badge className={levelColors[s.levelBadge] || "bg-muted text-muted-foreground"}><Award className="w-3 h-3 mr-1" /> {s.levelBadge}</Badge>
                  </div>
                  <div className="flex flex-wrap items-center gap-4 text-sm text-muted-foreground">
                    <span className="flex items-center gap-1"><GraduationCap className="w-4 h-4" /> {s.university}</span>
                    <span className="flex items-center gap-1"><MapPin className="w-4 h-4" /> {s.city}</span>
                    <span className="flex items-center gap-1"><Calendar className="w-4 h-4" /> {s.memberSince}</span>
                  </div>
                </div>
                <div className="flex gap-2 sm:self-end">
                  <Button variant="outline" size="sm" onClick={handleDownloadCV}><Download className="w-4 h-4 mr-1" /> {t.pf_cv}</Button>
                  {isOwner && s.avatar && !previewFile && (
                    <Button variant="outline" size="sm" onClick={handleDeleteAvatar} disabled={uploading} className="text-destructive">
                      <Trash2 className="w-4 h-4 mr-1" /> {t.pf_photo}
                    </Button>
                  )}
                  <Button size="sm" className="bg-gradient-hero hover:opacity-90"><MessageSquare className="w-4 h-4 mr-1" /> {t.pf_contact}</Button>
                </div>
              </div>
            </CardContent>
          </Card>
        </motion.div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mt-6">
          {[
            { label: t.pf_stat_rating, value: `${s.rating}/5`, icon: Star, color: "text-secondary" },
            { label: t.pf_stat_missions, value: s.completedJobs, icon: Award, color: "text-primary" },
            { label: t.pf_stat_reviews, value: s.reviewCount, icon: MessageSquare, color: "text-accent" },
            { label: t.pf_stat_response, value: s.responseTime, icon: Clock, color: "text-muted-foreground" },
          ].map((stat, i) => (
            <motion.div key={stat.label} initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.1 }}>
              <Card className="shadow-card border-border/50 text-center p-4">
                <stat.icon className={`w-5 h-5 mx-auto mb-1 ${stat.color}`} />
                <p className="text-xl font-display font-bold text-foreground">{stat.value}</p>
                <p className="text-xs text-muted-foreground">{stat.label}</p>
              </Card>
            </motion.div>
          ))}
        </div>

        <Card className="shadow-card border-border/50 mt-6 p-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm font-medium text-foreground">{t.pf_progress}</span>
            <span className="text-xs text-muted-foreground">{s.xp} / {s.nextLevelXp} XP</span>
          </div>
          <Progress value={(s.xp / s.nextLevelXp) * 100} className="h-3" />
        </Card>

        <Tabs defaultValue="services" className="mt-6">
          <TabsList className="w-full justify-start">
            <TabsTrigger value="services">{t.pf_tab_services} ({studentGigs.length})</TabsTrigger>
            <TabsTrigger value="avis">{t.pf_tab_reviews} ({studentReviews.length})</TabsTrigger>
            <TabsTrigger value="competences">{t.pf_tab_skills}</TabsTrigger>
            {isOwner && <TabsTrigger value="revenu">{t.inc_title}</TabsTrigger>}
          </TabsList>
          <TabsContent value="revenu">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
              <Card className="p-6 bg-primary/5 border-primary/20">
                <p className="text-sm text-muted-foreground mb-1">{t.inc_available}</p>
                <p className="text-3xl font-display font-bold text-primary">{income?.completed.toLocaleString() || 0} <span className="text-sm">FCFA</span></p>
              </Card>
              <Card className="p-6 bg-secondary/5 border-secondary/20">
                <p className="text-sm text-muted-foreground mb-1">{t.inc_pending}</p>
                <p className="text-3xl font-display font-bold text-secondary">{income?.pending.toLocaleString() || 0} <span className="text-sm">FCFA</span></p>
              </Card>
              <Card className="p-6 bg-accent/5 border-accent/20">
                <p className="text-sm text-muted-foreground mb-1">{t.inc_paid_missions}</p>
                <p className="text-3xl font-display font-bold text-accent">{income?.count || 0}</p>
              </Card>
            </div>
            <Card className="mt-4 p-6 border-border/50">
              <h3 className="font-display font-bold text-lg mb-4">{t.inc_payout_settings}</h3>
              <div className="space-y-4 max-w-md">
                <div className="space-y-2">
                  <Label>{t.inc_payout_phone}</Label>
                  <div className="flex gap-2">
                    <Input
                      placeholder={s.phone || "+237 6XX XXX XXX"}
                      value={newPayoutPhone}
                      onChange={(e) => setNewPayoutPhone(e.target.value)}
                    />
                    <Button onClick={handleUpdatePayoutPhone} disabled={updatingPayout || !newPayoutPhone}>
                      {updatingPayout ? <Loader2 className="w-4 h-4 animate-spin" /> : t.c_save}
                    </Button>
                  </div>
                  <p className="text-[10px] text-muted-foreground">{t.inc_payout_help}</p>
                </div>
              </div>
            </Card>
          </TabsContent>
          <TabsContent value="services">
            <div className="space-y-4 mt-4">
              {studentGigs.map((gig) => (
                <Card key={gig.id} className="shadow-card border-border/50 overflow-hidden">
                  <div className="h-1.5 bg-gradient-hero" />
                  <CardContent className="p-5">
                    <h3 className="font-display font-bold text-foreground text-lg mb-1">{gig.title}</h3>
                    <p className="text-sm text-muted-foreground mb-4">{gig.description}</p>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-4">
                      {(["basique", "standard", "premium"] as const).map((key) => {
                        const t_tier = gig.tiers[key];
                        return (
                          <div key={key} className="p-3 rounded-lg border border-border bg-muted/30">
                            <p className="font-medium text-sm text-foreground capitalize">{t_tier.name}</p>
                            <p className="text-lg font-display font-bold text-primary">{t_tier.price.toLocaleString()} <span className="text-xs">FCFA</span></p>
                            <p className="text-xs text-muted-foreground">{t_tier.deliveryDays}{t.pf_days_short} · {t_tier.features.length} {t.pf_included}</p>
                          </div>
                        );
                      })}
                    </div>
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3 text-sm text-muted-foreground">
                        <span className="flex items-center gap-1"><Star className="w-3 h-3 text-secondary fill-secondary" /> {gig.rating}</span>
                        <span>{gig.orderCount} {t.pf_orders_count}</span>
                      </div>
                      <Button size="sm" className="bg-gradient-hero hover:opacity-90" onClick={() => navigate(`/commander/${gig.id}`)}>{t.pf_order}</Button>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </TabsContent>
          <TabsContent value="avis">
            <div className="space-y-4 mt-4">
              {studentReviews.map((review) => (
                <Card key={review.id} className="shadow-card border-border/50">
                  <CardContent className="p-4">
                    <div className="flex items-center justify-between mb-2">
                      <span className="font-medium text-sm text-foreground">{review.reviewerName}</span>
                      <div className="flex items-center gap-2">
                        <div className="flex items-center gap-1">{Array.from({ length: 5 }).map((_, i) => <Star key={i} className={`w-3 h-3 ${i < review.rating ? "text-secondary fill-secondary" : "text-muted"}`} />)}</div>
                        {isOwner && !review.reported && (
                          <Button
                            type="button"
                            size="sm"
                            variant="ghost"
                            className="h-7 px-2 text-muted-foreground"
                            title="Signaler cet avis"
                            onClick={() => reportReview.mutate(
                              { reviewId: review.id, reason: "Avis conteste par l'etudiant concerne" },
                              {
                                onSuccess: () => toast({ title: "Avis signale", description: "La moderation va examiner cet avis." }),
                                onError: (error) => toast({ title: "Erreur", description: getErrorMessage(error), variant: "destructive" }),
                              },
                            )}
                          >
                            <Flag className="w-3 h-3" />
                          </Button>
                        )}
                      </div>
                    </div>
                    <p className="text-sm text-muted-foreground">{review.text}</p>
                    <p className="text-xs text-muted-foreground mt-2">{new Date(review.date).toLocaleDateString(locale)}</p>
                  </CardContent>
                </Card>
              ))}
            </div>
          </TabsContent>
          <TabsContent value="competences">
            <div className="mt-4">
              <div className="flex flex-wrap gap-2 mb-6">{s.skills.map((skill) => <Badge key={skill} variant="outline" className="px-3 py-1 text-sm">{skill}</Badge>)}</div>
              <Card className="shadow-card border-border/50 p-6"><h3 className="font-display font-bold text-foreground mb-2">{t.pf_about}</h3><p className="text-muted-foreground text-sm leading-relaxed">{s.bio}</p></Card>
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </Layout>
  );
};

export default StudentProfile;
