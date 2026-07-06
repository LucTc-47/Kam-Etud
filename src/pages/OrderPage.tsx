import { useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { motion } from "framer-motion";
import { ArrowLeft, Clock, Star, Shield, Smartphone, Loader2 } from "lucide-react";
import Layout from "@/components/layout/Layout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/contexts/AuthContext";
import { useGigById, useCreateOrder, initiateOrderPayment } from "@/hooks/useBackendData";
import { useLanguage } from "@/contexts/LanguageContext";
import { getErrorMessage, validateCameroonPhone } from "@/lib/utils";


const OrderPage = () => {
  const { t } = useLanguage();
  const { gigId } = useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const { user, isAuthenticated } = useAuth();
  const { data: dbGig, isLoading } = useGigById(gigId);
  const createOrder = useCreateOrder();
  const [selectedTier, setSelectedTier] = useState<'basique' | 'standard' | 'premium'>('standard');
  const [description, setDescription] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("mtn");
  const [phone, setPhone] = useState("");

  const gig = dbGig;
  const showLoading = isLoading;

  if (showLoading) return <Layout><div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div></Layout>;
  if (!gig) return <Layout><div className="container mx-auto px-4 py-16 text-center"><p>{t.op_gig_404}</p><Link to="/services" className="text-primary hover:underline">{t.c_back}</Link></div></Layout>;

  const tier = gig.tiers[selectedTier];

  const isRealGig = !!gigId && /^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(gigId);

  const handleOrder = async () => {
    if (!description.trim() || !phone.trim()) return;

    if (!validateCameroonPhone(phone)) {
      toast({ title: t.c_error, description: "Format de numéro invalide.", variant: "destructive" });
      return;
    }

    if (!isAuthenticated) { toast({ title: t.op_login_required, description: t.op_login_required_d, variant: "destructive" }); navigate("/connexion"); return; }
    if (!isRealGig) {
      toast({ title: t.op_demo, description: t.op_demo_d });
      return;
    }
    try {
      const deliveryDate = new Date();
      deliveryDate.setDate(deliveryDate.getDate() + tier.deliveryDays);
      const newOrder = await createOrder.mutateAsync({
        gig_id: gig.id, gig_title: gig.title,
        student_id: gig.studentId, student_name: gig.studentName,
        tier: selectedTier, description, budget: tier.price,
        payment_method: paymentMethod,
        delivery_date: deliveryDate.toISOString(),
      });
      const orderId = newOrder.id;
      if (orderId) {
        try {
          const payment = await initiateOrderPayment(orderId, phone);
          const ussd = payment.ussdCode;
          toast({ title: t.op_validate_phone, description: ussd ? `${t.op_ussd} ${ussd} ${t.op_to_confirm} ${tier.price.toLocaleString()} FCFA.` : payment.message || `${t.op_request_sent_to} ${tier.price.toLocaleString()} FCFA ${t.op_has_been_sent_to} ${phone}.` });
        } catch {
          toast({ title: t.op_mm_pending, description: t.op_mm_pending_d, variant: "destructive" });
        }
      }
      navigate("/mes-commandes");
    } catch (e: unknown) {
      toast({ title: t.c_error, description: getErrorMessage(e), variant: "destructive" });
    }
  };

  return (
    <Layout>
      <div className="container mx-auto px-4 py-8 max-w-4xl">
        <Button variant="ghost" size="sm" onClick={() => navigate(-1)} className="mb-4"><ArrowLeft className="w-4 h-4 mr-1" /> {t.c_back}</Button>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
              <h1 className="text-2xl font-display font-bold text-foreground mb-1">{t.op_h}</h1>
              <p className="text-muted-foreground">{gig.title} — {t.op_by} {gig.studentName}</p>
            </motion.div>
            <Card className="shadow-card border-border/50">
              <CardHeader><CardTitle className="font-display text-lg">{t.op_choose_tier}</CardTitle></CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  {(["basique", "standard", "premium"] as const).map((key) => {
                    const t_tier = gig.tiers[key];
                    return (
                      <div key={key} onClick={() => setSelectedTier(key)} className={`p-4 rounded-xl border-2 cursor-pointer transition-all ${selectedTier === key ? "border-primary bg-primary/5" : "border-border hover:border-primary/30"}`}>
                        <p className="font-display font-bold text-foreground capitalize">{t_tier.name}</p>
                        <p className="text-2xl font-display font-bold text-primary mt-1">{t_tier.price.toLocaleString()} <span className="text-sm">FCFA</span></p>
                        <p className="text-xs text-muted-foreground mt-1">{t_tier.description}</p>
                        <div className="flex items-center gap-1 text-xs text-muted-foreground mt-2"><Clock className="w-3 h-3" /> {t_tier.deliveryDays}{t.pf_days_short}</div>
                        <ul className="mt-3 space-y-1">{t_tier.features.map((f) => <li key={f} className="text-xs text-muted-foreground flex items-start gap-1"><span className="text-primary mt-0.5">✓</span> {f}</li>)}</ul>
                      </div>
                    );
                  })}
                </div>
              </CardContent>
            </Card>
            <Card className="shadow-card border-border/50">
              <CardHeader><CardTitle className="font-display text-lg">{t.op_describe}</CardTitle></CardHeader>
              <CardContent><Textarea placeholder={t.op_describe_ph} value={description} onChange={(e) => setDescription(e.target.value)} className="min-h-[120px]" /></CardContent>
            </Card>
            <Card className="shadow-card border-border/50">
              <CardHeader><CardTitle className="font-display text-lg">{t.op_secure_pay}</CardTitle></CardHeader>
              <CardContent className="space-y-4">
                <div className="p-3 rounded-lg bg-primary/5 border border-primary/20 flex items-start gap-2">
                  <Shield className="w-5 h-5 text-primary mt-0.5 shrink-0" /><p className="text-sm text-foreground"><span className="font-medium">{t.op_escrow_t}</span> {t.op_escrow_d}</p>
                </div>
                <RadioGroup value={paymentMethod} onValueChange={setPaymentMethod} className="space-y-2">
                  <div className="flex items-center space-x-2 p-3 rounded-lg border border-border hover:bg-muted/30 cursor-pointer"><RadioGroupItem value="mtn" id="mtn" /><Label htmlFor="mtn" className="flex items-center gap-2 cursor-pointer flex-1"><Smartphone className="w-4 h-4 text-secondary" /> MTN Mobile Money</Label></div>
                  <div className="flex items-center space-x-2 p-3 rounded-lg border border-border hover:bg-muted/30 cursor-pointer"><RadioGroupItem value="orange" id="orange" /><Label htmlFor="orange" className="flex items-center gap-2 cursor-pointer flex-1"><Smartphone className="w-4 h-4 text-accent" /> Orange Money</Label></div>
                </RadioGroup>
                <div className="space-y-2"><Label>{t.op_phone}</Label><Input placeholder="+237 6XX XXX XXX" value={phone} onChange={(e) => setPhone(e.target.value)} /></div>
              </CardContent>
            </Card>
          </div>
          <div className="lg:col-span-1">
            <div className="sticky top-20">
              <Card className="shadow-elevated border-border/50">
                <CardContent className="p-6">
                  <h3 className="font-display font-bold text-foreground mb-4">{t.op_summary}</h3>
                  <div className="space-y-3 text-sm">
                    <div className="flex justify-between"><span className="text-muted-foreground">{t.op_tier}</span><span className="font-medium text-foreground">{tier.name}</span></div>
                    <div className="flex justify-between"><span className="text-muted-foreground">{t.op_provider}</span><span className="font-medium text-foreground">{gig.studentName}</span></div>
                    <div className="flex justify-between"><span className="text-muted-foreground">{t.pf_stat_rating}</span><span className="flex items-center gap-1"><Star className="w-3 h-3 text-secondary fill-secondary" />{gig.rating}</span></div>
                    <div className="flex justify-between"><span className="text-muted-foreground">{t.op_delay}</span><span>{tier.deliveryDays}{t.pf_days_short}</span></div>
                    <hr className="border-border" />
                    <div className="flex justify-between text-lg font-display font-bold"><span>{t.op_total}</span><span className="text-primary">{tier.price.toLocaleString()} FCFA</span></div>
                  </div>
                  <Button className="w-full mt-6 bg-gradient-hero hover:opacity-90" onClick={handleOrder} disabled={createOrder.isPending || !description.trim() || !phone.trim()}>
                    {createOrder.isPending ? t.op_processing : `${t.op_pay_btn} ${tier.price.toLocaleString()} FCFA`}
                  </Button>
                  <p className="text-xs text-muted-foreground text-center mt-2">{t.op_escrow_caption}</p>
                </CardContent>
              </Card>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default OrderPage;
