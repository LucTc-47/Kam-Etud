import { useState } from "react";
import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { Plus, Loader2, MapPin, Coins, Calendar } from "lucide-react";
import Layout from "@/components/layout/Layout";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/contexts/AuthContext";
import { useGigRequests, useCreateGigRequest, useCategories, useCities } from "@/hooks/useBackendData";
import { useLanguage } from "@/contexts/LanguageContext";
import SEO from "@/components/seo/SEO";

const getErrorMessage = (error: unknown) => error instanceof Error ? error.message : String(error);

const Requests = () => {
  const { t, locale } = useLanguage();
  const { user } = useAuth();
  const { toast } = useToast();
  const { data: requests, isLoading } = useGigRequests();
  const { data: categories } = useCategories();
  const { data: cities } = useCities();
  const createReq = useCreateGigRequest();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ title: "", description: "", category: "", location: "", budget: "", deadline: "" });

  const isClient = user?.role === "client";
  const today = new Date().toISOString().split("T")[0];

  const submit = async () => {
    const budget = parseInt(form.budget) || 0;
    if (budget <= 0) {
      toast({ title: t.c_error, description: t.c_err_invalid_price, variant: "destructive" });
      return;
    }
    if (form.deadline && form.deadline < today) {
      toast({ title: t.c_error, description: "La date ne peut pas etre anterieure a aujourd'hui.", variant: "destructive" });
      return;
    }
    try {
      await createReq.mutateAsync({
        title: form.title.trim(),
        description: form.description.trim(),
        category: form.category,
        location: form.location,
        budget,
        deadline: form.deadline || undefined,
      });
      toast({ title: t.rq_posted, description: t.rq_posted_d });
      setOpen(false);
      setForm({ title: "", description: "", category: "", location: "", budget: "", deadline: "" });
    } catch (error) {
      toast({ title: t.c_error, description: getErrorMessage(error), variant: "destructive" });
    }
  };

  return (
    <Layout>
      <SEO title={t.rq_title} description={t.rq_subtitle} />
      <div className="container mx-auto px-4 py-8 max-w-4xl">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-display font-bold text-foreground">{t.rq_title}</h1>
            <p className="text-sm text-muted-foreground">{t.rq_subtitle}</p>
          </div>
          {isClient && (
            <Dialog open={open} onOpenChange={setOpen}>
              <DialogTrigger asChild>
                <Button className="bg-gradient-hero hover:opacity-90"><Plus className="w-4 h-4 mr-1" /> {t.rq_new}</Button>
              </DialogTrigger>
              <DialogContent className="max-w-lg">
                <DialogHeader><DialogTitle>{t.rq_new_modal}</DialogTitle></DialogHeader>
                <div className="space-y-3">
                  <div className="space-y-1"><Label>{t.rq_title_f}</Label><Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder={t.rq_title_ph} /></div>
                  <div className="space-y-1"><Label>{t.rq_desc}</Label><Textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder={t.rq_desc_ph} /></div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <Label>{t.cg_category}</Label>
                      <Select value={form.category} onValueChange={(v) => setForm({ ...form, category: v })}>
                        <SelectTrigger><SelectValue placeholder={t.cg_choose} /></SelectTrigger>
                        <SelectContent>{(categories || []).map((c) => <SelectItem key={c.id} value={c.name}>{c.name}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-1">
                      <Label>{t.city}</Label>
                      <Select value={form.location} onValueChange={(v) => setForm({ ...form, location: v })}>
                        <SelectTrigger><SelectValue placeholder={t.cg_choose} /></SelectTrigger>
                        <SelectContent>{(cities || []).map((c) => <SelectItem key={c.id} value={c.name}>{c.name}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1"><Label>{t.rq_budget}</Label><Input type="number" value={form.budget} onChange={(e) => setForm({ ...form, budget: e.target.value })} placeholder="50000" /></div>
                    <div className="space-y-1"><Label>{t.rq_deadline}</Label><Input type="date" min={today} value={form.deadline} onChange={(e) => setForm({ ...form, deadline: e.target.value })} /></div>
                  </div>
                  <Button className="w-full bg-gradient-hero" onClick={submit} disabled={createReq.isPending || !form.title || !form.budget}>
                    {createReq.isPending ? t.rq_publishing : t.rq_publish}
                  </Button>
                </div>
              </DialogContent>
            </Dialog>
          )}
        </div>

        {isLoading ? (
          <div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>
        ) : !requests || requests.length === 0 ? (
          <Card className="shadow-card border-border/50 p-8 text-center">
            <p className="text-muted-foreground">{t.rq_none}</p>
          </Card>
        ) : (
          <div className="space-y-3">
            {requests.map((r) => (
              <motion.div key={r.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
                <Link to={`/demandes/${r.id}`}>
                  <Card className="shadow-card border-border/50 hover:shadow-elevated transition-all">
                    <CardContent className="p-5">
                      <div className="flex items-start justify-between gap-4">
                        <div className="flex-1">
                          <h3 className="font-display font-bold text-foreground">{r.title}</h3>
                          <p className="text-sm text-muted-foreground line-clamp-2 mt-1">{r.description}</p>
                          <div className="flex flex-wrap gap-3 mt-3 text-xs text-muted-foreground">
                            {r.category && <Badge variant="outline">{r.category}</Badge>}
                            {r.location && <span className="flex items-center gap-1"><MapPin className="w-3 h-3" /> {r.location}</span>}
                            {r.deadline && <span className="flex items-center gap-1"><Calendar className="w-3 h-3" /> {new Date(r.deadline).toLocaleDateString(locale)}</span>}
                            <span>{t.rq_by} {r.client_name}</span>
                          </div>
                        </div>
                        <div className="text-right">
                          <div className="flex items-center gap-1 text-primary font-bold">
                            <Coins className="w-4 h-4" /> {r.budget.toLocaleString()} FCFA
                          </div>
                          <p className="text-xs text-muted-foreground mt-1">{t.rq_budget_caption}</p>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                </Link>
              </motion.div>
            ))}
          </div>
        )}
      </div>
    </Layout>
  );
};

export default Requests;
