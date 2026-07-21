import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { motion } from "framer-motion";
import { ArrowLeft, Loader2, Plus, Trash2 } from "lucide-react";
import Layout from "@/components/layout/Layout";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { useCreateGig, useUpdateGig, useMyGigs, useCategories, useCities } from "@/hooks/useBackendData";
import { useAuth } from "@/contexts/AuthContext";
import { Switch } from "@/components/ui/switch";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { useLanguage } from "@/contexts/LanguageContext";
import { getErrorMessage } from "@/lib/utils";


const CreateGig = () => {
  const { t } = useLanguage();
  const navigate = useNavigate();
  const { gigId } = useParams();
  const isEditing = !!gigId;
  const { toast } = useToast();
  const createGig = useCreateGig();
  const updateGig = useUpdateGig();
  const { data: myGigs, isLoading: isLoadingGigs } = useMyGigs();
  const gigToEdit = isEditing ? myGigs?.find((gig) => gig.id === gigId) : undefined;
  const {
    data: dbCategories,
    isLoading: categoriesLoading,
    isError: categoriesError,
    refetch: refetchCategories,
  } = useCategories();
  const {
    data: dbCities,
    isLoading: citiesLoading,
    isError: citiesError,
    refetch: refetchCities,
  } = useCities();
  const { user } = useAuth();
  const categories = dbCategories || [];
  const cities = dbCities ? dbCities.map(c => c.name) : [];
  const referenceDataLoading = categoriesLoading || citiesLoading;
  const referenceDataError = categoriesError || citiesError;
  const [publishNow, setPublishNow] = useState(false);
  const isVerified = !!user?.verified;

  const [form, setForm] = useState({
    title: "", description: "", category: "", location: "",
    basique: { price: "", description: "", deliveryDays: "", features: [""] },
    standard: { price: "", description: "", deliveryDays: "", features: [""] },
    premium: { price: "", description: "", deliveryDays: "", features: [""] },
  });

  useEffect(() => {
    if (!gigToEdit) return;
    const tierForm = (tier: typeof gigToEdit.tiers.basique) => ({
      price: String(tier.price),
      description: tier.description || "",
      deliveryDays: String(tier.deliveryDays),
      features: tier.features.length ? tier.features : [""],
    });
    setForm({
      title: gigToEdit.title,
      description: gigToEdit.description,
      category: gigToEdit.category,
      location: gigToEdit.location,
      basique: tierForm(gigToEdit.tiers.basique),
      standard: tierForm(gigToEdit.tiers.standard),
      premium: tierForm(gigToEdit.tiers.premium),
    });
    setPublishNow(!!gigToEdit.published);
  }, [gigToEdit]);

  const updateTier = (tier: 'basique' | 'standard' | 'premium', field: string, value: string) => {
    setForm((prev) => ({ ...prev, [tier]: { ...prev[tier], [field]: value } }));
  };
  const addFeature = (tier: 'basique' | 'standard' | 'premium') => {
    setForm((prev) => ({ ...prev, [tier]: { ...prev[tier], features: [...prev[tier].features, ""] } }));
  };
  const updateFeature = (tier: 'basique' | 'standard' | 'premium', idx: number, value: string) => {
    setForm((prev) => { const f = [...prev[tier].features]; f[idx] = value; return { ...prev, [tier]: { ...prev[tier], features: f } }; });
  };
  const removeFeature = (tier: 'basique' | 'standard' | 'premium', idx: number) => {
    setForm((prev) => { const f = prev[tier].features.filter((_, i) => i !== idx); return { ...prev, [tier]: { ...prev[tier], features: f.length ? f : [""] } }; });
  };

  const buildTier = (tier: 'basique' | 'standard' | 'premium', label: string) => {
    const price = parseInt(form[tier].price) || 0;
    const days = parseInt(form[tier].deliveryDays) || 0;
    return {
      name: label,
      price: price > 0 ? price : 0,
      description: form[tier].description || label,
      deliveryDays: days > 0 ? days : 0,
      features: form[tier].features.filter(f => f.trim().length > 0),
    };
  };

  const handleSubmit = async () => {
    if (!form.title.trim() || !form.category) {
      toast({ title: t.c_error, description: t.c_err_required_fields, variant: "destructive" });
      return;
    }

    const tiers = {
      basique: buildTier('basique', 'Basique'),
      standard: buildTier('standard', 'Standard'),
      premium: buildTier('premium', 'Premium'),
    };

    if (tiers.basique.price <= 0 || tiers.standard.price <= 0 || tiers.premium.price <= 0) {
      toast({ title: t.c_error, description: t.c_err_invalid_price, variant: "destructive" });
      return;
    }
    if (tiers.basique.deliveryDays <= 0 || tiers.standard.deliveryDays <= 0 || tiers.premium.deliveryDays <= 0) {
      toast({ title: t.c_error, description: "Le delai de livraison doit etre d'au moins 1 jour.", variant: "destructive" });
      return;
    }

    try {
      const gigPayload = {
        title: form.title, description: form.description,
        category: form.category, location: form.location,
        tier_basique: tiers.basique,
        tier_standard: tiers.standard,
        tier_premium: tiers.premium,
        published: isEditing ? publishNow : publishNow && isVerified,
      };
      if (isEditing && gigId && gigToEdit) {
        await updateGig.mutateAsync({
          id: gigId,
          gig: { ...gigPayload, images: gigToEdit.images },
        });
        toast({ title: t.cg_updated, description: t.cg_updated_d });
        navigate("/mes-gigs");
        return;
      }
      await createGig.mutateAsync(gigPayload);
      toast({
        title: publishNow && isVerified ? t.cg_gig_published : t.cg_draft_saved,
        description: publishNow && isVerified
          ? t.cg_gig_published_d
          : t.cg_draft_saved_d,
      });
      navigate("/mes-gigs");
    } catch (e: unknown) {
      toast({ title: t.c_error, description: getErrorMessage(e), variant: "destructive" });
    }
  };

  const renderTierForm = (tier: 'basique' | 'standard' | 'premium', label: string) => (
    <Card className="shadow-card border-border/50">
      <CardHeader><CardTitle className="font-display text-base capitalize">{label}</CardTitle></CardHeader>
      <CardContent className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1"><Label className="text-xs">{t.cg_price}</Label><Input type="number" min="1" placeholder="0" value={form[tier].price} onChange={(e) => updateTier(tier, "price", e.target.value)} /></div>
          <div className="space-y-1"><Label className="text-xs">{t.cg_delay}</Label><Input type="number" min="1" placeholder="1" value={form[tier].deliveryDays} onChange={(e) => updateTier(tier, "deliveryDays", e.target.value)} /></div>
        </div>
        <div className="space-y-1"><Label className="text-xs">{t.cg_tier_desc}</Label><Input placeholder={t.cg_tier_desc_ph} value={form[tier].description} onChange={(e) => updateTier(tier, "description", e.target.value)} /></div>
        <div className="space-y-1">
          <Label className="text-xs">{t.cg_included}</Label>
          {form[tier].features.map((f, i) => (
            <div key={i} className="flex gap-1">
              <Input placeholder={t.cg_feature_ph} value={f} onChange={(e) => updateFeature(tier, i, e.target.value)} className="text-sm" />
              {form[tier].features.length > 1 && <Button size="icon" variant="ghost" className="shrink-0" onClick={() => removeFeature(tier, i)}><Trash2 className="w-3 h-3" /></Button>}
            </div>
          ))}
          <Button size="sm" variant="ghost" onClick={() => addFeature(tier)} className="text-xs"><Plus className="w-3 h-3 mr-1" /> {t.cg_add}</Button>
        </div>
      </CardContent>
    </Card>
  );

  if (isEditing && isLoadingGigs) {
    return <Layout><div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div></Layout>;
  }
  if (isEditing && !gigToEdit) {
    return <Layout><div className="container mx-auto px-4 py-16 text-center text-muted-foreground">{t.mg_not_found}</div></Layout>;
  }

  return (
    <Layout>
      <div className="container mx-auto px-4 py-8 max-w-3xl">
        <Button variant="ghost" size="sm" onClick={() => navigate(-1)} className="mb-4"><ArrowLeft className="w-4 h-4 mr-1" /> {t.c_back}</Button>
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
          <h1 className="text-2xl font-display font-bold text-foreground mb-6">{isEditing ? t.cg_edit_title : t.cg_title}</h1>
          <div className="space-y-6">
            <Card className="shadow-card border-border/50">
              <CardHeader><CardTitle className="font-display text-lg">{t.cg_general}</CardTitle></CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2"><Label>{t.cg_gig_title}</Label><Input placeholder={t.cg_gig_title_ph} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></div>
                <div className="space-y-2"><Label>{t.cg_gig_desc}</Label><Textarea placeholder={t.cg_gig_desc_ph} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} className="min-h-[100px]" /></div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div className="space-y-2"><Label>{t.cg_category}</Label><Select value={form.category} disabled={referenceDataLoading || categoriesError} onValueChange={(v) => setForm({ ...form, category: v })}><SelectTrigger><SelectValue placeholder={referenceDataLoading ? t.cg_options_loading : t.cg_choose} /></SelectTrigger><SelectContent>{form.category && !categories.some((c) => c.active && c.name === form.category) && <SelectItem value={form.category}>{form.category}</SelectItem>}{categories.filter((c) => c.active).map((c) => <SelectItem key={c.id} value={c.name}>{c.name}</SelectItem>)}</SelectContent></Select></div>
                  <div className="space-y-2"><Label>{t.city}</Label><Select value={form.location} disabled={referenceDataLoading || citiesError} onValueChange={(v) => setForm({ ...form, location: v })}><SelectTrigger><SelectValue placeholder={referenceDataLoading ? t.cg_options_loading : t.cg_choose} /></SelectTrigger><SelectContent>{form.location && !cities.includes(form.location) && <SelectItem value={form.location}>{form.location}</SelectItem>}{cities.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent></Select></div>
                </div>
                {referenceDataError && (
                  <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
                    <span>{t.cg_options_error}</span>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => void Promise.all([refetchCategories(), refetchCities()])}
                    >
                      <RefreshCw className="mr-1 h-4 w-4" /> {t.cg_options_retry}
                    </Button>
                  </div>
                )}
              </CardContent>
            </Card>
            <h2 className="font-display font-bold text-lg text-foreground">{t.cg_pricing}</h2>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {renderTierForm("basique", `🟢 ${t.lvl_beginner}`)}
              {renderTierForm("standard", `🟡 ${t.lvl_intermediate}`)}
              {renderTierForm("premium", `🔴 ${t.lvl_expert}`)}
            </div>
            <Card className="shadow-card border-border/50">
              <CardContent className="p-4 flex items-center justify-between gap-4">
                <div>
                  <p className="font-medium text-foreground">{t.cg_publish_now}</p>
                  <p className="text-xs text-muted-foreground">
                    {isVerified
                      ? t.cg_pn_yes
                      : t.cg_pn_no}
                  </p>
                </div>
                <Switch checked={publishNow} disabled={!isVerified && !publishNow} onCheckedChange={setPublishNow} />
              </CardContent>
            </Card>
            {!isVerified && (
              <div className="flex items-start gap-2 p-3 rounded-lg bg-secondary/10 border border-secondary/30 text-sm">
                <AlertTriangle className="w-4 h-4 text-secondary mt-0.5 shrink-0" />
                <p className="text-foreground">{t.cg_warn} <strong>{t.mg_draft}</strong>{t.cg_warn_2}</p>
              </div>
            )}
            <Button className="w-full bg-gradient-hero hover:opacity-90 h-12" onClick={handleSubmit} disabled={createGig.isPending || updateGig.isPending || !form.title || !form.category}>
              {(createGig.isPending || updateGig.isPending) ? t.cg_saving : (isEditing ? t.cg_update_btn : (publishNow && isVerified ? t.cg_publish_btn : t.cg_save_draft))}
            </Button>
          </div>
        </motion.div>
      </div>
    </Layout>
  );
};

export default CreateGig;
