import { motion } from "framer-motion";
import { Plus, Trash2, Star, ShoppingBag, ToggleLeft, ToggleRight, Loader2, Eye, EyeOff, Lock, Pencil } from "lucide-react";
import { useNavigate } from "react-router-dom";
import Layout from "@/components/layout/Layout";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { useToast } from "@/hooks/use-toast";
import { useMyGigs, useToggleGig, useDeleteGig, usePublishGig } from "@/hooks/useBackendData";
import { useAuth } from "@/contexts/AuthContext";
import { useLanguage } from "@/contexts/LanguageContext";
import { getErrorMessage } from "@/lib/utils";

const MyGigs = () => {
  const { t } = useLanguage();
  const { toast } = useToast();
  const navigate = useNavigate();
  const { data: gigs, isLoading } = useMyGigs();
  const toggleGig = useToggleGig();
  const deleteGig = useDeleteGig();
  const publishGig = usePublishGig();
  const { user } = useAuth();
  const isVerified = !!user?.verified;

  const handleToggle = (id: string, currentActive: boolean) => {
    toggleGig.mutate({ id, active: !currentActive }, { onSuccess: () => toast({ title: t.mg_status_ok }) });
  };

  const handleDelete = (id: string) => {
    deleteGig.mutate(id, { onSuccess: () => toast({ title: t.mg_deleted }) });
  };

  const handlePublish = (id: string, currentlyPublished: boolean) => {
    if (!currentlyPublished && !isVerified) {
      toast({ title: t.mg_kyc_req, description: t.mg_kyc_req_d, variant: "destructive" });
      return;
    }
    publishGig.mutate({ id, published: !currentlyPublished }, {
      onSuccess: () => toast({ title: currentlyPublished ? t.mg_unpublished : t.mg_published }),
      onError: (e: unknown) => toast({ title: t.c_error, description: getErrorMessage(e), variant: "destructive" }),
    });
  };

  if (isLoading) return <Layout><div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div></Layout>;

  return (
    <Layout>
      <div className="container mx-auto px-4 py-8 max-w-4xl">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-display font-bold text-foreground">{t.mg_title}</h1>
          <Button className="bg-gradient-hero hover:opacity-90" onClick={() => navigate("/mes-gigs/creer")}><Plus className="w-4 h-4 mr-1" /> {t.mg_create}</Button>
        </div>
        <div className="space-y-4">
          {(!gigs || gigs.length === 0) ? (
            <Card className="shadow-card border-border/50 p-8 text-center">
              <p className="text-muted-foreground mb-4">{t.mg_none}</p>
              <Button className="bg-gradient-hero" onClick={() => navigate("/mes-gigs/creer")}><Plus className="w-4 h-4 mr-1" /> {t.mg_create}</Button>
            </Card>
          ) : gigs.map((gig) => (
            <motion.div key={gig.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
              <Card className={`shadow-card border-border/50 ${!gig.active ? "opacity-60" : ""}`}>
                <CardContent className="p-5">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <h3 className="font-display font-bold text-foreground">{gig.title}</h3>
                        <Badge variant={gig.active ? "default" : "outline"} className={gig.active ? "bg-primary/10 text-primary" : ""}>{gig.active ? t.active : t.inactive}</Badge>
                        {gig.published
                          ? <Badge className="bg-green-500/10 text-green-600 border-green-500/30">{t.mg_published}</Badge>
                          : <Badge variant="outline" className="border-secondary/40 text-secondary">{t.mg_draft}</Badge>}
                      </div>
                      <div className="flex items-center gap-4 text-sm text-muted-foreground">
                        <Badge variant="outline">{gig.category}</Badge>
                        <span className="flex items-center gap-1"><Star className="w-3 h-3 text-secondary fill-secondary" /> {gig.rating}</span>
                        <span className="flex items-center gap-1"><ShoppingBag className="w-3 h-3" /> {gig.orderCount} {t.mg_orders_count}</span>
                      </div>
                      <div className="mt-2 flex gap-4 text-sm">
                        <span className="text-muted-foreground">{t.lvl_beginner}: <span className="font-medium text-foreground">{gig.tiers.basique.price.toLocaleString()}</span></span>
                        <span className="text-muted-foreground">{t.lvl_intermediate}: <span className="font-medium text-foreground">{gig.tiers.standard.price.toLocaleString()}</span></span>
                        <span className="text-muted-foreground">{t.lvl_expert}: <span className="font-medium text-foreground">{gig.tiers.premium.price.toLocaleString()}</span></span>
                      </div>
                    </div>
                    <div className="flex gap-1">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => handlePublish(gig.id, !!gig.published)}
                        title={gig.published ? t.mg_unpublish : (isVerified ? t.mg_publish : t.mg_kyc_req)}
                      >
                        {gig.published ? <EyeOff className="w-4 h-4" /> : (isVerified ? <Eye className="w-4 h-4" /> : <Lock className="w-4 h-4 text-muted-foreground" />)}
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => handleToggle(gig.id, gig.active)}>{gig.active ? <ToggleRight className="w-4 h-4 text-primary" /> : <ToggleLeft className="w-4 h-4" />}</Button>
                      {/* Ancien bouton Edit sans gestionnaire remplace par ce parcours complet. */}
                      <Button size="sm" variant="outline" onClick={() => navigate(`/mes-gigs/${gig.id}/modifier`)}>
                        <Pencil className="w-4 h-4 mr-1" /> {t.mg_edit}
                      </Button>
                      <Button size="sm" variant="ghost" className="text-destructive" onClick={() => handleDelete(gig.id)}><Trash2 className="w-4 h-4" /></Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </motion.div>
          ))}
        </div>
      </div>
    </Layout>
  );
};

export default MyGigs;
