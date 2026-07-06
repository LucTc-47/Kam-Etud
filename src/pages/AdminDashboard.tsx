import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import {
  Users, DollarSign, ShoppingBag, AlertTriangle, CheckCircle, XCircle,
  Eye, Search, TrendingUp, Shield, Clock, BarChart3,
  Ban, MapPin, Tag, Plus, Trash2, FileSpreadsheet, FileText, Loader2, LogOut,
  ZoomIn, ZoomOut, Download, RotateCcw,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { api, authTokenStorage } from "@/lib/api";

// Les URLs KYC privées sont chargées avec le JWT puis converties en blob local.
function KycImage({ url, alt, onOpen }: { url: string; alt: string; onOpen?: (src: string) => void }) {
  const { t } = useLanguage();
  const [src, setSrc] = useState("");
  useEffect(() => {
    let active = true;
    let objectUrl = "";
    const load = async () => {
      try {
        const token = authTokenStorage.getAccessToken();
        const response = await fetch(url, {
          headers: token ? { Authorization: `Bearer ${token}` } : undefined,
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        objectUrl = URL.createObjectURL(await response.blob());
        if (active) setSrc(objectUrl);
      } catch {
        // Repli pour une URL de document deja publique.
        if (active) setSrc(url);
      }
    };
    void load();
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [url]);
  if (!src) return <div className="w-full h-full flex items-center justify-center text-xs text-muted-foreground">{t.ad_loading_img}</div>;
  return (
    <button type="button" onClick={() => onOpen?.(src)} className="block w-full h-full">
      <img src={src} alt={alt} className="w-full h-full object-cover cursor-zoom-in hover:opacity-90 transition" />
    </button>
  );
}
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import {
  SidebarProvider, Sidebar, SidebarContent, SidebarGroup, SidebarGroupLabel,
  SidebarGroupContent, SidebarMenu, SidebarMenuItem, SidebarMenuButton, SidebarTrigger,
} from "@/components/ui/sidebar";
import { useToast } from "@/hooks/use-toast";
import { useLanguage } from "@/contexts/LanguageContext";
import { useAuth } from "@/contexts/AuthContext";
import {
  useAllProfiles, useAllOrders, useVerificationRequests, useUpdateVerification,
  useCategories, useAllCities, useCreateCategory, useToggleCategory, useDeleteCategory,
  useCreateCity, useDeleteCity, useUpdateProfile, useReportedContent, useModerateReview,
  useAbuseReports, useDecideAbuseReport, getSignedFileUrl,
} from "@/hooks/useBackendData";
import type { AbuseReport } from "@/types";
import * as XLSX from 'xlsx';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import { getErrorMessage } from "@/lib/utils";

type AdminTab = "dashboard" | "users" | "verifications" | "orders" | "moderation" | "categories" | "reports";

type VerificationRecord = {
  id: string;
  student_id: string;
  student_name: string;
  email: string;
  university: string | null;
  id_type: string | null;
  id_file_url: string | null;
  selfie_url: string | null;
  student_card_url: string | null;
  submitted_at: string;
};

type PdfWithAutoTable = jsPDF & { lastAutoTable?: { finalY: number } };

const AdminDashboard = () => {
  const { user, loading, logout } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (!loading && (!user || user.role !== 'admin')) {
      navigate("/");
    }
  }, [user, loading, navigate]);

  const { toast } = useToast();
  const { t, locale } = useLanguage();
  const [activeTab, setActiveTab] = useState<AdminTab>("dashboard");
  const [searchQuery, setSearchQuery] = useState("");
  const [newCategory, setNewCategory] = useState("");
  const [newCity, setNewCity] = useState("");
  const [selectedVerification, setSelectedVerification] = useState<VerificationRecord | null>(null);
  const [lightbox, setLightbox] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [selectedAbuse, setSelectedAbuse] = useState<AbuseReport | null>(null);
  const [adminDecisionNote, setAdminDecisionNote] = useState("");

  // Reset zoom whenever lightbox image changes
  useEffect(() => { setZoom(1); }, [lightbox]);

  const handleDownload = async () => {
    if (!lightbox) return;
    try {
      const res = await fetch(lightbox);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      const ext = (blob.type.split("/")[1] || "jpg").split("+")[0];
      a.download = `kametud-${Date.now()}.${ext}`;
      document.body.appendChild(a); a.click(); a.remove();
      URL.revokeObjectURL(url);
    } catch (e: unknown) {
      toast({ title: t.ad_download_err, description: getErrorMessage(e), variant: "destructive" });
    }
  };

  // ─── Real data hooks ───
  const { data: profiles = [], isLoading: profilesLoading } = useAllProfiles();
  const { data: orders = [], isLoading: ordersLoading, refetch: refetchOrders } = useAllOrders();
  const { data: verifications = [], isLoading: verificationsLoading } = useVerificationRequests();
  const { data: categories = [], isLoading: categoriesLoading } = useCategories();
  const { data: cities = [], isLoading: citiesLoading } = useAllCities();
  const { data: reportedContent = [] } = useReportedContent();
  const { data: abuseReports = [], isLoading: abuseReportsLoading } = useAbuseReports();

  // ─── Mutations ───
  const updateVerification = useUpdateVerification();
  const createCategory = useCreateCategory();
  const toggleCategory = useToggleCategory();
  const deleteCategory = useDeleteCategory();
  const createCity = useCreateCity();
  const deleteCity = useDeleteCity();
  const updateProfile = useUpdateProfile();
  const moderateReview = useModerateReview();
  const decideAbuseReport = useDecideAbuseReport();

  // ─── Computed stats ───
  const totalUsers = profiles.length;
  const totalOrders = orders.length;
  const totalRevenue = orders.reduce((s, o) => s + o.budget, 0);
  const disputedOrders = orders.filter(o => o.status === 'disputed').length;
  const pendingVerifications = verifications.filter(v => v.status === 'pending');

  const STATS = [
    { label: t.activeUsers, value: totalUsers.toLocaleString(), icon: Users, color: "text-primary" },
    { label: t.revenue, value: `${(totalRevenue / 1000).toFixed(0)}K`, icon: DollarSign, color: "text-secondary" },
    { label: t.orders, value: totalOrders.toLocaleString(), icon: ShoppingBag, color: "text-accent" },
    { label: t.disputes, value: disputedOrders.toString(), icon: AlertTriangle, color: "text-destructive" },
  ];

  const statusBadge = (status: string) => {
    const map: Record<string, { label: string; className: string }> = {
      completed: { label: t.completed, className: "bg-primary/10 text-primary" },
      in_progress: { label: t.inProgress, className: "bg-secondary/20 text-secondary-foreground" },
      disputed: { label: t.disputed, className: "bg-destructive/10 text-destructive" },
      pending: { label: t.pending, className: "bg-muted text-muted-foreground" },
      active: { label: t.active, className: "bg-primary/10 text-primary" },
      approved: { label: t.verified, className: "bg-primary/10 text-primary" },
      rejected: { label: t.rejected, className: "bg-destructive/10 text-destructive" },
      banned: { label: t.banned, className: "bg-destructive/10 text-destructive" },
    };
    const s = map[status] || map.pending;
    return <Badge className={s.className}>{s.label}</Badge>;
  };

  const abuseReasonLabel: Record<AbuseReport['reason'], string> = {
    fraud: t.md_reason_fraud,
    harassment: t.md_reason_harassment,
    false_evidence: t.md_reason_false_evidence,
    scam: t.md_reason_scam,
    inappropriate_content: t.md_reason_inappropriate,
    other: t.md_reason_other,
  };

  const abuseStatusLabel: Record<AbuseReport['status'], string> = {
    open: t.ad_status_open,
    dismissed: t.ad_status_dismissed,
    warned: t.ad_status_warned,
    banned: t.banned,
  };

  const sidebarItems: { title: string; tab: AdminTab; icon: typeof BarChart3 }[] = [
    { title: t.dashboard, tab: "dashboard", icon: BarChart3 },
    { title: t.users, tab: "users", icon: Users },
    { title: t.verifications, tab: "verifications", icon: Shield },
    { title: t.orders, tab: "orders", icon: ShoppingBag },
    { title: t.moderation, tab: "moderation", icon: AlertTriangle },
    { title: t.categories, tab: "categories", icon: Tag },
    { title: t.reports, tab: "reports", icon: FileSpreadsheet },
  ];

  const handleVerification = async (id: string, action: "approve" | "reject") => {
    try {
      await updateVerification.mutateAsync({ id, status: action === "approve" ? "approved" : "rejected" });
      toast({ title: action === "approve" ? t.verified : t.rejected, description: action === "approve" ? t.badgeAssigned : t.willBeNotified });
    } catch (e: unknown) {
      toast({ title: "Erreur", description: getErrorMessage(e), variant: "destructive" });
    }
  };

  const handleUserAction = async (userId: string, action: "ban" | "activate") => {
    try {
      await updateProfile.mutateAsync({ userId, updates: { banned: action === "ban" } });
      toast({ title: action === "ban" ? t.banned : t.reactivated });
    } catch (e: unknown) {
      toast({ title: "Erreur", description: getErrorMessage(e), variant: "destructive" });
    }
  };

  const handleOpenDisputeEvidence = async (path: string) => {
    try {
      const url = await getSignedFileUrl("disputes", path);
      window.open(url, "_blank", "noopener,noreferrer");
    } catch (error) {
      toast({ title: "Erreur", description: getErrorMessage(error), variant: "destructive" });
    }
  };

  const handleAbuseDecision = async (action: 'DISMISS' | 'WARN' | 'BAN') => {
    if (!selectedAbuse || !adminDecisionNote.trim()) return;
    try {
      // Le bannissement reste la responsabilite d'Identity. Cette operation est
      // idempotente et est terminee avant de cloturer le dossier disciplinaire.
      if (action === 'BAN') {
        await updateProfile.mutateAsync({
          userId: selectedAbuse.targetUserId,
          updates: { banned: true },
        });
      }
      await decideAbuseReport.mutateAsync({
        reportId: selectedAbuse.id,
        action,
        adminNote: adminDecisionNote.trim(),
      });
      toast({ title: t.ad_decision_saved });
      setSelectedAbuse(null);
      setAdminDecisionNote("");
    } catch (error) {
      toast({ title: "Erreur", description: getErrorMessage(error), variant: "destructive" });
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate("/");
  };

  const handleExport = (format: "excel" | "pdf") => {
    try {
      if (format === "excel") {
        const wb = XLSX.utils.book_new();
        // Users sheet
        const usersData = profiles.map(p => ({
          Nom: `${p.first_name} ${p.last_name}`,
          Email: p.email,
          Rôle: p.role,
          Ville: p.city || '',
          Vérifié: p.verified ? 'Oui' : 'Non',
          Banni: p.banned ? 'Oui' : 'Non',
          Inscription: new Date(p.created_at).toLocaleDateString(locale),
        }));
        XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(usersData), t.users);
        // Orders sheet
        const ordersData = orders.map(o => ({
          Client: o.clientName,
          Étudiant: o.studentName,
          Service: o.gigTitle,
          Montant: o.budget,
          Statut: o.status,
          Date: new Date(o.createdAt).toLocaleDateString(locale),
        }));
        XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(ordersData), t.orders);
        XLSX.writeFile(wb, `KamEtud_Export_${new Date().toISOString().slice(0, 10)}.xlsx`);
        toast({ title: t.exportLaunched + " Excel", description: t.downloadSoon });
      } else {
        const doc = new jsPDF();
        doc.setFontSize(18);
        doc.text(t.ad_report_title, 14, 22);
        doc.setFontSize(10);
        doc.text(`${t.pf_cv_gen} ${new Date().toLocaleDateString(locale)}`, 14, 30);

        // Stats
        doc.setFontSize(14);
        doc.text(t.c_stats, 14, 42);
        autoTable(doc, {
          startY: 46,
          head: [[t.c_metric, t.c_value]],
          body: [
            [t.users, totalUsers.toString()],
            [t.orders, totalOrders.toString()],
            [t.revenue, `${totalRevenue.toLocaleString()} FCFA`],
            [t.disputes, disputedOrders.toString()],
          ],
        });

        // Orders table
        const finalY = (doc as PdfWithAutoTable).lastAutoTable?.finalY || 80;
        doc.setFontSize(14);
        doc.text(t.orders, 14, finalY + 10);
        autoTable(doc, {
          startY: finalY + 14,
          head: [[t.client, t.student, t.service, t.amount, t.status]],
          body: orders.slice(0, 50).map(o => [o.clientName, o.studentName, o.gigTitle, `${o.budget} FCFA`, o.status]),
          styles: { fontSize: 8 },
        });

        doc.save(`KamEtud_Rapport_${new Date().toISOString().slice(0, 10)}.pdf`);
        toast({ title: t.exportLaunched + " PDF", description: t.downloadSoon });
      }
    } catch (e: unknown) {
      toast({ title: "Erreur d'export", description: getErrorMessage(e), variant: "destructive" });
    }
  };

  const addCategory = async () => {
    if (!newCategory.trim()) return;
    try {
      await createCategory.mutateAsync(newCategory.trim());
      setNewCategory("");
      toast({ title: t.categoryAdded });
    } catch (e: unknown) {
      toast({ title: "Erreur", description: getErrorMessage(e), variant: "destructive" });
    }
  };

  const addCity = async () => {
    if (!newCity.trim()) return;
    try {
      await createCity.mutateAsync(newCity.trim());
      setNewCity("");
      toast({ title: t.cityAdded });
    } catch (e: unknown) {
      toast({ title: "Erreur", description: getErrorMessage(e), variant: "destructive" });
    }
  };

  // Filter profiles by search
  const filteredProfiles = profiles.filter(p =>
    !searchQuery || `${p.first_name} ${p.last_name} ${p.email}`.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const Loader = () => <div className="flex justify-center py-8"><Loader2 className="w-6 h-6 animate-spin text-primary" /></div>;

  const renderContent = () => {
    switch (activeTab) {
      case "dashboard":
        return (
          <div className="space-y-6">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {STATS.map((stat, i) => (
                <motion.div key={stat.label} initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.1 }}>
                  <Card className="shadow-card border-border/50">
                    <CardContent className="p-4">
                      <div className="flex items-center justify-between mb-2">
                        <stat.icon className={`w-5 h-5 ${stat.color}`} />
                      </div>
                      <p className="text-2xl font-display font-bold text-foreground">{stat.value}</p>
                      <p className="text-xs text-muted-foreground">{stat.label}</p>
                    </CardContent>
                  </Card>
                </motion.div>
              ))}
            </div>
            {pendingVerifications.length > 0 && (
              <Card className="shadow-card border-border/50 border-l-4 border-l-secondary">
                <CardContent className="p-4 flex items-center gap-3">
                  <Shield className="w-5 h-5 text-secondary" />
                  <p className="text-sm text-foreground">
                    <span className="font-bold">{pendingVerifications.length}</span> {t.verifications} en attente
                  </p>
                  <Button size="sm" variant="outline" onClick={() => setActiveTab("verifications")}>{t.verifications}</Button>
                </CardContent>
              </Card>
            )}
          </div>
        );

      case "verifications":
        if (verificationsLoading) return <Loader />;
        return (
          <>
            <Card className="shadow-card border-border/50">
              <CardHeader><CardTitle className="font-display text-lg">{t.identityVerification}</CardTitle></CardHeader>
              <CardContent>
                {pendingVerifications.length === 0 ? (
                  <div className="text-center py-8"><CheckCircle className="w-12 h-12 text-primary mx-auto mb-2" /><p className="text-muted-foreground">{t.allProcessed}</p></div>
                ) : (
                  <Table>
                    <TableHeader><TableRow>
                      <TableHead>{t.student}</TableHead><TableHead>{t.university}</TableHead><TableHead>{t.documentType}</TableHead><TableHead>{t.submitted}</TableHead><TableHead className="text-right">{t.actions}</TableHead>
                    </TableRow></TableHeader>
                    <TableBody>
                      {pendingVerifications.map((v) => (
                        (() => {
                          const sp = profiles.find(p => p.user_id === v.student_id);
                          return (
                        <TableRow key={v.id}>
                          <TableCell>
                            <div className="flex items-center gap-2">
                              <button
                                type="button"
                                onClick={() => sp?.avatar_url && setLightbox(sp.avatar_url)}
                                className="rounded-full focus:outline-none focus:ring-2 focus:ring-primary"
                                title={sp?.avatar_url ? t.ad_preview : ""}
                              >
                                <Avatar className="w-8 h-8">
                                  {sp?.avatar_url && <AvatarImage src={sp.avatar_url} alt={v.student_name} />}
                                  <AvatarFallback className="bg-muted text-xs">{v.student_name?.split(" ").map((n: string) => n[0]).join("") || "?"}</AvatarFallback>
                                </Avatar>
                              </button>
                              <div><p className="font-medium text-foreground text-sm">{v.student_name}</p><p className="text-xs text-muted-foreground">{v.email}</p></div>
                            </div>
                          </TableCell>
                          <TableCell className="text-sm">{v.university || '-'}</TableCell>
                          <TableCell><Badge variant="outline">{v.id_type || 'CNI'}</Badge></TableCell>
                          <TableCell className="text-sm text-muted-foreground">{new Date(v.submitted_at).toLocaleDateString('fr-FR')}</TableCell>
                          <TableCell className="text-right">
                            <div className="flex justify-end gap-1">
                              <Button size="sm" variant="ghost" onClick={() => setSelectedVerification(v)}><Eye className="w-4 h-4" /></Button>
                              <Button size="sm" variant="ghost" className="text-primary" onClick={() => handleVerification(v.id, "approve")}><CheckCircle className="w-4 h-4" /></Button>
                              <Button size="sm" variant="ghost" className="text-destructive" onClick={() => handleVerification(v.id, "reject")}><XCircle className="w-4 h-4" /></Button>
                            </div>
                          </TableCell>
                        </TableRow>
                          );
                        })()
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
            {/* All verifications history */}
            {verifications.filter(v => v.status !== 'pending').length > 0 && (
              <Card className="shadow-card border-border/50 mt-4">
                <CardHeader><CardTitle className="font-display text-lg">{t.c_history}</CardTitle></CardHeader>
                <CardContent>
                  <Table>
                    <TableHeader><TableRow>
                      <TableHead>{t.student}</TableHead><TableHead>{t.status}</TableHead><TableHead>{t.submitted}</TableHead>
                    </TableRow></TableHeader>
                    <TableBody>
                      {verifications.filter(v => v.status !== 'pending').map(v => (
                        <TableRow key={v.id}>
                          <TableCell className="text-sm">{v.student_name}</TableCell>
                          <TableCell>{statusBadge(v.status)}</TableCell>
                          <TableCell className="text-sm text-muted-foreground">{new Date(v.submitted_at).toLocaleDateString(locale)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            )}
            <Dialog open={!!selectedVerification} onOpenChange={(open) => !open && setSelectedVerification(null)}>
              <DialogContent className="max-w-2xl">
                <DialogHeader>
                  <DialogTitle className="font-display">{t.verifications} — {selectedVerification?.student_name}</DialogTitle>
                </DialogHeader>
                {selectedVerification && (
                  <div className="space-y-4">
                    {(() => {
                      const sp = profiles.find(p => p.user_id === selectedVerification.student_id);
                      if (!sp?.avatar_url) return null;
                      return (
                        <div className="flex items-center gap-3">
                          <button type="button" onClick={() => setLightbox(sp.avatar_url!)} className="shrink-0">
                            <Avatar className="w-16 h-16 cursor-zoom-in ring-2 ring-primary/20">
                              <AvatarImage src={sp.avatar_url} alt={selectedVerification.student_name} />
                              <AvatarFallback>{selectedVerification.student_name?.[0]}</AvatarFallback>
                            </Avatar>
                          </button>
                          <div>
                            <p className="text-sm font-medium">{t.pf_photo}</p>
                            <p className="text-xs text-muted-foreground">{t.ad_preview}</p>
                          </div>
                        </div>
                      );
                    })()}
                    <div className="grid grid-cols-2 gap-4 text-sm">
                      <div><span className="text-muted-foreground">{t.email} :</span> <span className="font-medium">{selectedVerification.email}</span></div>
                      <div><span className="text-muted-foreground">{t.university} :</span> <span className="font-medium">{selectedVerification.university || '-'}</span></div>
                      <div><span className="text-muted-foreground">{t.documentType} :</span> <Badge variant="outline">{selectedVerification.id_type || 'CNI'}</Badge></div>
                      <div><span className="text-muted-foreground">{t.submitted} :</span> <span className="font-medium">{new Date(selectedVerification.submitted_at).toLocaleDateString(locale)}</span></div>
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                      {selectedVerification.id_file_url && (
                        <div className="space-y-2">
                          <Label className="text-sm font-medium">{t.idDocument}</Label>
                          <div className="border border-border rounded-lg overflow-hidden bg-muted aspect-[4/3]">
                            <KycImage url={selectedVerification.id_file_url} alt={t.idDocument} onOpen={setLightbox} />
                          </div>
                        </div>
                      )}
                      {selectedVerification.student_card_url && (
                        <div className="space-y-2">
                          <Label className="text-sm font-medium">{t.studentCard}</Label>
                          <div className="border border-border rounded-lg overflow-hidden bg-muted aspect-[4/3]">
                            <KycImage url={selectedVerification.student_card_url} alt={t.studentCard} onOpen={setLightbox} />
                          </div>
                        </div>
                      )}
                      {selectedVerification.selfie_url && (
                        <div className="space-y-2">
                          <Label className="text-sm font-medium">{t.verificationSelfie}</Label>
                          <div className="border border-border rounded-lg overflow-hidden bg-muted aspect-[4/3]">
                            <KycImage url={selectedVerification.selfie_url} alt={t.verificationSelfie} onOpen={setLightbox} />
                          </div>
                        </div>
                      )}
                      {!selectedVerification.id_file_url && !selectedVerification.student_card_url && !selectedVerification.selfie_url && (
                        <p className="text-sm text-muted-foreground col-span-3 text-center py-4">{t.ad_no_docs}</p>
                      )}
                    </div>
                    <div className="flex justify-end gap-2 pt-2">
                      <Button variant="outline" className="text-destructive border-destructive/30" onClick={() => { handleVerification(selectedVerification.id, "reject"); setSelectedVerification(null); }}>
                        <XCircle className="w-4 h-4 mr-1" /> {t.reject}
                      </Button>
                      <Button className="bg-gradient-hero" onClick={() => { handleVerification(selectedVerification.id, "approve"); setSelectedVerification(null); }}>
                        <CheckCircle className="w-4 h-4 mr-1" /> {t.approve}
                      </Button>
                    </div>
                  </div>
                )}
              </DialogContent>
            </Dialog>
          </>
        );

      case "users":
        if (profilesLoading) return <Loader />;
        return (
          <Card className="shadow-card border-border/50">
            <CardHeader><CardTitle className="font-display text-lg">{t.userManagement} ({filteredProfiles.length})</CardTitle></CardHeader>
            <CardContent>
              <Table>
                <TableHeader><TableRow>
                  <TableHead>{t.users}</TableHead><TableHead>{t.role}</TableHead><TableHead>{t.city}</TableHead><TableHead>{t.status}</TableHead><TableHead>{t.joined}</TableHead><TableHead className="text-right">{t.actions}</TableHead>
                </TableRow></TableHeader>
                <TableBody>
                  {filteredProfiles.map((p) => (
                    <TableRow key={p.id}>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <Avatar className="w-8 h-8"><AvatarFallback className="bg-muted text-xs">{`${p.first_name?.[0] || ''}${p.last_name?.[0] || ''}`}</AvatarFallback></Avatar>
                          <div><p className="font-medium text-foreground text-sm">{p.first_name} {p.last_name}</p><p className="text-xs text-muted-foreground">{p.email}</p></div>
                        </div>
                      </TableCell>
                      <TableCell><Badge variant="outline" className="capitalize">{p.role === "student" ? t.student : t.client}</Badge></TableCell>
                      <TableCell className="text-sm">{p.city || '-'}</TableCell>
                      <TableCell>{statusBadge(p.banned ? "banned" : "active")}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">{new Date(p.created_at).toLocaleDateString(locale)}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          {!p.banned ? (
                            <Button size="sm" variant="ghost" onClick={() => handleUserAction(p.user_id, "ban")} title={t.banned}>
                              <Ban className="w-4 h-4 text-destructive" />
                            </Button>
                          ) : (
                            <Button size="sm" variant="ghost" onClick={() => handleUserAction(p.user_id, "activate")} title={t.reactivated}>
                              <CheckCircle className="w-4 h-4 text-primary" />
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                  {filteredProfiles.length === 0 && (
                    <TableRow><TableCell colSpan={6} className="text-center text-muted-foreground py-8">{t.ad_no_users}</TableCell></TableRow>
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        );

      case "moderation":
        return (
          <div className="space-y-6">
            <Card className="shadow-card border-border/50">
              <CardHeader>
                <CardTitle className="font-display text-lg flex items-center gap-2">
                  {t.ad_abuse_reports}
                  <Badge variant="destructive">{abuseReports.filter(report => report.status === 'open').length}</Badge>
                </CardTitle>
              </CardHeader>
              <CardContent>
                {abuseReportsLoading ? <Loader /> : abuseReports.length === 0 ? (
                  <div className="text-center py-8"><CheckCircle className="w-12 h-12 text-primary mx-auto mb-2" /><p className="text-muted-foreground">{t.ad_no_abuse}</p></div>
                ) : (
                  <Table>
                    <TableHeader><TableRow>
                      <TableHead>{t.service}</TableHead>
                      <TableHead>{t.ad_abuse_target}</TableHead>
                      <TableHead>{t.ad_abuse_reason}</TableHead>
                      <TableHead>{t.status}</TableHead>
                      <TableHead className="text-right">{t.actions}</TableHead>
                    </TableRow></TableHeader>
                    <TableBody>
                      {abuseReports.map(report => (
                        <TableRow key={report.id}>
                          <TableCell className="max-w-[320px]">
                            <p className="font-medium text-sm">{report.gigTitle}</p>
                            <p className="text-xs text-muted-foreground truncate">{report.note}</p>
                          </TableCell>
                          <TableCell>
                            <p className="text-sm">{report.targetName}</p>
                            <Badge variant="outline" className="text-xs">{report.targetRole === 'student' ? t.student : t.client}</Badge>
                          </TableCell>
                          <TableCell className="text-sm">{abuseReasonLabel[report.reason]}</TableCell>
                          <TableCell>
                            <Badge variant={report.status === 'open' ? 'destructive' : 'outline'}>{abuseStatusLabel[report.status]}</Badge>
                          </TableCell>
                          <TableCell className="text-right">
                            <Button size="sm" variant="outline" onClick={() => {
                              setSelectedAbuse(report);
                              setAdminDecisionNote(report.adminNote || "");
                            }}>
                              <Eye className="w-4 h-4 mr-1" /> {t.ad_abuse_review}
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>

            <Card className="shadow-card border-border/50">
              <CardHeader><CardTitle className="font-display text-lg">{t.reportedContent}</CardTitle></CardHeader>
              <CardContent>
                {reportedContent.length === 0 ? (
                  <div className="text-center py-8"><CheckCircle className="w-12 h-12 text-primary mx-auto mb-2" /><p className="text-muted-foreground">{t.ad_no_reports}</p></div>
                ) : (
                  <Table>
                    <TableHeader><TableRow>
                      <TableHead>{t.pf_tab_reviews}</TableHead><TableHead>{t.pf_stat_rating}</TableHead><TableHead>{t.author}</TableHead><TableHead className="text-right">{t.actions}</TableHead>
                    </TableRow></TableHeader>
                    <TableBody>
                      {reportedContent.map((r) => (
                        <TableRow key={r.id}>
                          <TableCell className="text-sm max-w-[300px] truncate">{r.text || t.ad_no_text}</TableCell>
                          <TableCell><Badge variant="outline">{r.rating}/5</Badge></TableCell>
                          <TableCell className="text-sm">{r.reviewer_name}</TableCell>
                          <TableCell className="text-right">
                            <div className="flex justify-end gap-1">
                              <Button
                                size="sm"
                                variant="ghost"
                                title="Conserver l'avis"
                                onClick={() => moderateReview.mutate({ reviewId: r.id, action: 'DISMISS' })}
                              >
                                <CheckCircle className="w-4 h-4 text-primary" />
                              </Button>
                              <Button
                                size="sm"
                                variant="ghost"
                                title="Masquer l'avis"
                                className="text-destructive"
                                onClick={() => moderateReview.mutate({ reviewId: r.id, action: 'HIDE' })}
                              >
                                <Trash2 className="w-4 h-4" />
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </div>
        );

      case "orders": {
        if (ordersLoading) return <Loader />;
        const runAutoValidation = async () => {
          try {
            const { data } = await api.post<{ eligible: number; validated: number; released: number; pending: number }>(
              '/api/orders/auto-validation/run',
            );
            await refetchOrders();
            toast({
              title: data.eligible === 0 && data.pending === 0 ? "Tout est à jour" : "Validation terminée",
              description: `${data.validated} commande(s) validée(s), ${data.released} paiement(s) libéré(s), ${data.pending} en attente.`,
            });
          } catch (error) {
            toast({ title: "Erreur", description: getErrorMessage(error), variant: "destructive" });
          }
        };

        return (
          <Card className="shadow-card border-border/50">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="font-display text-lg">{t.orders} ({orders.length})</CardTitle>
              <Button size="sm" variant="outline" onClick={runAutoValidation} className="text-xs">
                <RotateCcw className="w-3 h-3 mr-1" /> Auto-valider (+24h)
              </Button>
            </CardHeader>
            <CardContent>
              {orders.length === 0 ? (
                <div className="text-center py-8"><ShoppingBag className="w-12 h-12 text-muted-foreground mx-auto mb-2" /><p className="text-muted-foreground">{t.ad_no_orders}</p></div>
              ) : (
                <Table>
                  <TableHeader><TableRow>
                    <TableHead>{t.client}</TableHead><TableHead>{t.student}</TableHead><TableHead>{t.service}</TableHead><TableHead>{t.amount}</TableHead><TableHead>{t.status}</TableHead><TableHead>Date</TableHead>
                  </TableRow></TableHeader>
                  <TableBody>
                    {orders.map((order) => (
                      <TableRow key={order.id}>
                        <TableCell className="text-sm">{order.clientName}</TableCell>
                        <TableCell className="text-sm">{order.studentName}</TableCell>
                        <TableCell className="text-sm max-w-[200px] truncate">{order.gigTitle}</TableCell>
                        <TableCell className="text-sm font-medium">{order.budget.toLocaleString()} FCFA</TableCell>
                        <TableCell>{statusBadge(order.status)}</TableCell>
                        <TableCell className="text-sm text-muted-foreground">{new Date(order.createdAt).toLocaleDateString(locale)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        );
      }

      case "categories":
        if (categoriesLoading || citiesLoading) return <Loader />;
        return (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card className="shadow-card border-border/50">
              <CardHeader><CardTitle className="font-display text-lg flex items-center gap-2"><Tag className="w-5 h-5" /> {t.categories} ({categories.length})</CardTitle></CardHeader>
              <CardContent>
                <div className="flex gap-2 mb-4">
                  <Input placeholder={t.newCategory} value={newCategory} onChange={(e) => setNewCategory(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && addCategory()} />
                  <Button size="sm" className="bg-gradient-hero" onClick={addCategory} disabled={createCategory.isPending}><Plus className="w-4 h-4" /></Button>
                </div>
                <div className="space-y-2">
                  {categories.map((cat) => (
                    <div key={cat.id} className="flex items-center justify-between p-2 rounded-lg border border-border">
                      <span className="text-sm font-medium text-foreground">{cat.icon ? `${cat.icon} ` : ""}{cat.name}</span>
                      <div className="flex gap-1">
                        <Badge variant={cat.active ? "default" : "outline"} className={cat.active ? "bg-primary/10 text-primary" : ""}>
                          {cat.active ? t.active : t.inactive}
                        </Badge>
                        <Button size="sm" variant="ghost" onClick={() => toggleCategory.mutate({ id: cat.id, active: !cat.active })}>
                          <Eye className="w-3 h-3" />
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => deleteCategory.mutate(cat.id)}>
                          <Trash2 className="w-3 h-3 text-destructive" />
                        </Button>
                      </div>
                    </div>
                  ))}
                  {categories.length === 0 && <p className="text-sm text-muted-foreground text-center py-4">{t.ad_no_cats}</p>}
                </div>
              </CardContent>
            </Card>
            <Card className="shadow-card border-border/50">
              <CardHeader><CardTitle className="font-display text-lg flex items-center gap-2"><MapPin className="w-5 h-5" /> {t.cities} ({cities.length})</CardTitle></CardHeader>
              <CardContent>
                <div className="flex gap-2 mb-4">
                  <Input placeholder={t.newCity} value={newCity} onChange={(e) => setNewCity(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && addCity()} />
                  <Button size="sm" className="bg-gradient-hero" onClick={addCity} disabled={createCity.isPending}><Plus className="w-4 h-4" /></Button>
                </div>
                <div className="flex flex-wrap gap-2">
                  {cities.map((city) => (
                    <Badge key={city.id} variant="outline" className="px-3 py-1 text-sm flex items-center gap-1">
                      {city.name}
                      <button onClick={() => deleteCity.mutate(city.id)}>
                        <Trash2 className="w-3 h-3 text-destructive" />
                      </button>
                    </Badge>
                  ))}
                  {cities.length === 0 && <p className="text-sm text-muted-foreground">{t.ad_no_cities}</p>}
                </div>
              </CardContent>
            </Card>
          </div>
        );

      case "reports":
        return (
          <Card className="shadow-card border-border/50">
            <CardHeader><CardTitle className="font-display text-lg">{t.reports}</CardTitle></CardHeader>
            <CardContent>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => handleExport("excel")}><FileSpreadsheet className="w-4 h-4 mr-1" /> Excel</Button>
                <Button variant="outline" onClick={() => handleExport("pdf")}><FileText className="w-4 h-4 mr-1" /> PDF</Button>
              </div>
            </CardContent>
          </Card>
        );
    }
  };

  return (
    <SidebarProvider>
      <div className="min-h-screen flex w-full">
        <Sidebar collapsible="icon">
          <SidebarContent>
            <SidebarGroup>
              <div className="p-4">
                <div className="flex items-center gap-2">
                  <div className="w-8 h-8 rounded-lg bg-gradient-hero flex items-center justify-center">
                    <span className="text-primary-foreground font-display font-bold text-sm">K</span>
                  </div>
                  <span className="font-display font-bold text-sm text-sidebar-foreground">{t.admin}</span>
                </div>
              </div>
              <SidebarGroupLabel>Navigation</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
                  {sidebarItems.map((item) => (
                    <SidebarMenuItem key={item.tab}>
                      <SidebarMenuButton
                        onClick={() => setActiveTab(item.tab)}
                        className={`cursor-pointer ${activeTab === item.tab ? "bg-sidebar-accent text-sidebar-primary font-medium" : "hover:bg-sidebar-accent/50"}`}
                      >
                        <item.icon className="mr-2 h-4 w-4" />
                        <span>{item.title}</span>
                        {item.tab === "verifications" && pendingVerifications.length > 0 && (
                          <Badge className="ml-auto bg-secondary text-secondary-foreground text-xs">{pendingVerifications.length}</Badge>
                        )}
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          </SidebarContent>
        </Sidebar>

        <div className="flex-1 flex flex-col">
          <header className="h-14 flex items-center justify-between border-b border-border px-4 bg-background">
            <div className="flex items-center gap-4">
              <SidebarTrigger />
              <h1 className="text-lg font-display font-bold text-foreground">{t.administration}</h1>
            </div>
            <div className="flex items-center gap-2">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                <Input placeholder={t.search} className="pl-9 w-64" value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} />
              </div>
              <Button variant="ghost" size="icon" onClick={handleLogout} title={t.logout}>
                <LogOut className="w-4 h-4 text-destructive" />
              </Button>
            </div>
          </header>

          <main className="flex-1 p-6 bg-muted/30 overflow-auto">
            {renderContent()}
          </main>
        </div>
      </div>
      <Dialog open={!!selectedAbuse} onOpenChange={(open) => {
        if (!open) {
          setSelectedAbuse(null);
          setAdminDecisionNote("");
        }
      }}>
        <DialogContent className="max-w-2xl">
          <DialogHeader><DialogTitle>{t.ad_abuse_reports}</DialogTitle></DialogHeader>
          {selectedAbuse && (
            <div className="space-y-4">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <h3 className="font-display font-bold">{selectedAbuse.gigTitle}</h3>
                  <p className="text-sm text-muted-foreground">#{selectedAbuse.orderId}</p>
                </div>
                <Badge variant={selectedAbuse.status === 'open' ? 'destructive' : 'outline'}>
                  {abuseStatusLabel[selectedAbuse.status]}
                </Badge>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="rounded-lg border p-3">
                  <p className="text-xs text-muted-foreground">{t.ad_abuse_target}</p>
                  <p className="font-medium">{selectedAbuse.targetName} ({selectedAbuse.targetRole === 'student' ? t.student : t.client})</p>
                </div>
                <div className="rounded-lg border p-3">
                  <p className="text-xs text-muted-foreground">{t.ad_abuse_reason}</p>
                  <p className="font-medium">{abuseReasonLabel[selectedAbuse.reason]}</p>
                </div>
              </div>
              <div className="rounded-lg bg-muted/50 border p-3">
                <p className="text-xs text-muted-foreground mb-1">{t.ad_abuse_note}</p>
                <p className="text-sm whitespace-pre-wrap">{selectedAbuse.note}</p>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="rounded-lg border p-3">
                  <p className="text-xs font-medium mb-1">{t.client}</p>
                  <p className="text-sm mb-2">{selectedAbuse.clientStatement}</p>
                  {selectedAbuse.clientEvidenceUrl && (
                    <Button size="sm" variant="outline" onClick={() => handleOpenDisputeEvidence(selectedAbuse.clientEvidenceUrl!)}>
                      <Download className="w-4 h-4 mr-1" /> {t.ad_client_evidence}
                    </Button>
                  )}
                </div>
                <div className="rounded-lg border p-3">
                  <p className="text-xs font-medium mb-1">{t.student}</p>
                  <p className="text-sm mb-2">{selectedAbuse.studentStatement || '-'}</p>
                  {selectedAbuse.studentEvidenceUrl && (
                    <Button size="sm" variant="outline" onClick={() => handleOpenDisputeEvidence(selectedAbuse.studentEvidenceUrl!)}>
                      <Download className="w-4 h-4 mr-1" /> {t.ad_student_evidence}
                    </Button>
                  )}
                </div>
              </div>
              {selectedAbuse.status === 'open' ? (
                <>
                  <Textarea value={adminDecisionNote} onChange={(event) => setAdminDecisionNote(event.target.value)} placeholder={t.ad_admin_note} />
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                    <Button variant="outline" disabled={!adminDecisionNote.trim() || decideAbuseReport.isPending} onClick={() => handleAbuseDecision('DISMISS')}>
                      <XCircle className="w-4 h-4 mr-1" /> {t.ad_dismiss_abuse}
                    </Button>
                    <Button disabled={!adminDecisionNote.trim() || decideAbuseReport.isPending} onClick={() => handleAbuseDecision('WARN')}>
                      <AlertTriangle className="w-4 h-4 mr-1" /> {t.ad_warn}
                    </Button>
                    <Button variant="destructive" disabled={!adminDecisionNote.trim() || decideAbuseReport.isPending || updateProfile.isPending} onClick={() => handleAbuseDecision('BAN')}>
                      <Ban className="w-4 h-4 mr-1" /> {t.ad_ban_user}
                    </Button>
                  </div>
                </>
              ) : selectedAbuse.adminNote ? (
                <div className="rounded-lg bg-muted/50 border p-3">
                  <p className="text-xs text-muted-foreground mb-1">{t.administration}</p>
                  <p className="text-sm whitespace-pre-wrap">{selectedAbuse.adminNote}</p>
                </div>
              ) : null}
            </div>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={!!lightbox} onOpenChange={(o) => !o && setLightbox(null)}>
        <DialogContent className="max-w-6xl p-0 bg-background overflow-hidden">
          <DialogHeader className="sr-only"><DialogTitle>{t.ad_preview_img}</DialogTitle></DialogHeader>
          <div className="flex items-center justify-between gap-2 p-2 border-b border-border bg-muted/40">
            <div className="flex items-center gap-1">
              <Button size="sm" variant="ghost" onClick={() => setZoom((z) => Math.max(0.25, z - 0.25))} title={t.ad_zoom_out}><ZoomOut className="w-4 h-4" /></Button>
              <span className="text-xs text-muted-foreground w-12 text-center">{Math.round(zoom * 100)}%</span>
              <Button size="sm" variant="ghost" onClick={() => setZoom((z) => Math.min(5, z + 0.25))} title={t.ad_zoom_in}><ZoomIn className="w-4 h-4" /></Button>
              <Button size="sm" variant="ghost" onClick={() => setZoom(1)} title={t.ad_reset_zoom}><RotateCcw className="w-4 h-4" /></Button>
            </div>
            <Button size="sm" variant="outline" onClick={handleDownload}><Download className="w-4 h-4 mr-1" /> {t.ad_download}</Button>
          </div>
          <div className="overflow-auto max-h-[85vh] flex items-center justify-center bg-black/40">
            {lightbox && (
              <img
                src={lightbox}
                alt={t.ad_preview}
                style={{ transform: `scale(${zoom})`, transformOrigin: "center center", transition: "transform 0.15s" }}
                className="max-w-full max-h-[85vh] object-contain select-none"
                onDoubleClick={() => setZoom((z) => (z === 1 ? 2 : 1))}
              />
            )}
          </div>
        </DialogContent>
      </Dialog>
    </SidebarProvider>
  );
};

export default AdminDashboard;
