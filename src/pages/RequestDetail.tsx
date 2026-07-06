import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Coins, Calendar, MapPin, Loader2, Check, X } from "lucide-react";
import Layout from "@/components/layout/Layout";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/contexts/AuthContext";
import {
  useGigRequest, useProposals, useCreateProposal, useAcceptProposal, useCancelGigRequest,
} from "@/hooks/useBackendData";
import { useLanguage } from "@/contexts/LanguageContext";
import { getErrorMessage } from "@/lib/utils";

const RequestDetail = () => {
  const { t, locale } = useLanguage();
  const { id } = useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const { user } = useAuth();
  const { data: proposals, isLoading: pLoading } = useProposals(id);
  const createProposal = useCreateProposal();
  const acceptProposal = useAcceptProposal();
  const cancelReq = useCancelGigRequest();
  const { data: req, isLoading } = useGigRequest(id);

  const [form, setForm] = useState({ price: "", delivery_days: "", message: "" });

  if (isLoading) return <Layout><div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div></Layout>;
  if (!req) return <Layout><div className="container mx-auto py-16 text-center text-muted-foreground">{t.rd_404}</div></Layout>;

  const isOwner = user?.id === req.client_id;
  const isStudent = user?.role === 'student';
  const canPropose = isStudent && user?.verified && req.status === 'open';
  const alreadyProposed = (proposals || []).some((p) => p.student_id === user?.id);

  const submit = async () => {
    const price = parseInt(form.price) || 0;
    const days = parseInt(form.delivery_days) || 0;

    if (price <= 0 || days <= 0) {
      toast({ title: t.c_error, description: "Prix et délai invalides.", variant: "destructive" });
      return;
    }

    try {
      await createProposal.mutateAsync({
        request_id: id!,
        price,
        delivery_days: days,
        message: form.message.trim() || "Je propose mes services pour cette mission.",
      });
      toast({ title: t.rd_sent });
      setForm({ price: "", delivery_days: "", message: "" });
    } catch (e: unknown) {
      toast({ title: t.c_error, description: getErrorMessage(e), variant: "destructive" });
    }
  };

  const accept = async (proposalId: string) => {
    try {
      await acceptProposal.mutateAsync({ proposalId, requestId: id! });
      toast({ title: t.rd_accepted, description: t.rd_accepted_d });
    } catch (e: unknown) {
      toast({ title: t.c_error, description: getErrorMessage(e), variant: "destructive" });
    }
  };

  const handleCancelRequest = async () => {
    try {
      await cancelReq.mutateAsync(id!);
      // Le backend rejette les propositions restantes atomiquement.
      toast({ title: t.rd_cancelled });
      navigate('/demandes');
    } catch (e: unknown) {
      toast({ title: t.c_error, description: getErrorMessage(e), variant: "destructive" });
    }
  };

  return (
    <Layout>
      <div className="container mx-auto px-4 py-8 max-w-3xl">
        <Button variant="ghost" size="sm" onClick={() => navigate(-1)} className="mb-4"><ArrowLeft className="w-4 h-4 mr-1" /> {t.c_back}</Button>

        <Card className="shadow-card border-border/50 mb-6">
          <CardContent className="p-6">
            <div className="flex items-start justify-between gap-4">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-2">
                  <h1 className="text-2xl font-display font-bold text-foreground">{req.title}</h1>
                  <Badge variant={req.status === 'open' ? 'default' : 'outline'}>{req.status}</Badge>
                </div>
                <p className="text-muted-foreground whitespace-pre-line">{req.description}</p>
                <div className="flex flex-wrap gap-3 mt-4 text-sm text-muted-foreground">
                  {req.category && <Badge variant="outline">{req.category}</Badge>}
                  {req.location && <span className="flex items-center gap-1"><MapPin className="w-3 h-3" /> {req.location}</span>}
                  {req.deadline && <span className="flex items-center gap-1"><Calendar className="w-3 h-3" /> {new Date(req.deadline).toLocaleDateString(locale)}</span>}
                  <span>{t.rq_by} {req.client_name}</span>
                </div>
              </div>
              <div className="text-right shrink-0">
                <div className="flex items-center gap-1 text-primary font-bold text-lg">
                  <Coins className="w-5 h-5" /> {Number(req.budget).toLocaleString()} FCFA
                </div>
                <p className="text-xs text-muted-foreground">{t.rq_budget_caption}</p>
              </div>
            </div>
            {isOwner && req.status === 'open' && (
              <Button variant="outline" size="sm" className="mt-4" onClick={handleCancelRequest} disabled={cancelReq.isPending}>
                {cancelReq.isPending ? <Loader2 className="w-3 h-3 animate-spin mr-1" /> : null}
                {t.rd_cancel}
              </Button>
            )}
          </CardContent>
        </Card>

        {canPropose && !alreadyProposed && (
          <Card className="shadow-card border-border/50 mb-6">
            <CardContent className="p-6 space-y-3">
              <h2 className="font-display font-bold">{t.rd_propose}</h2>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1"><Label>{t.rd_your_price}</Label><Input type="number" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} /></div>
                <div className="space-y-1"><Label>{t.rd_delay}</Label><Input type="number" value={form.delivery_days} onChange={(e) => setForm({ ...form, delivery_days: e.target.value })} /></div>
              </div>
              <div className="space-y-1"><Label>{t.rd_message}</Label><Textarea value={form.message} onChange={(e) => setForm({ ...form, message: e.target.value })} placeholder={t.rd_message_ph} /></div>
              <Button className="bg-gradient-hero" onClick={submit} disabled={createProposal.isPending || !form.price}>
                {createProposal.isPending ? t.rd_sending : t.rd_send}
              </Button>
            </CardContent>
          </Card>
        )}
        {isStudent && !user?.verified && (
          <p className="text-sm text-secondary mb-6">{t.rd_must_verify}</p>
        )}

        <h2 className="font-display font-bold text-lg mb-3">{t.rd_proposals} ({proposals?.length || 0})</h2>
        {pLoading ? (
          <Loader2 className="w-6 h-6 animate-spin text-primary" />
        ) : !proposals || proposals.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t.rd_no_proposals}</p>
        ) : (
          <div className="space-y-3">
            {proposals.map((p) => (
              <Card key={p.id} className="shadow-card border-border/50">
                <CardContent className="p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <p className="font-medium text-foreground">{p.student_name}</p>
                        <Badge variant={p.status === 'accepted' ? 'default' : 'outline'} className={p.status === 'accepted' ? 'bg-green-500/10 text-green-600' : ''}>{p.status}</Badge>
                      </div>
                      <p className="text-sm text-muted-foreground mt-1">{p.message}</p>
                      <p className="text-xs text-muted-foreground mt-2">{t.op_delay} : {p.delivery_days} {t.rd_delay_days}</p>
                      <p className="text-[10px] text-muted-foreground">{new Date(p.created_at).toLocaleDateString(locale)}</p>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-primary font-bold">{Number(p.price).toLocaleString()} FCFA</p>
                      {isOwner && req.status === 'open' && (
                        <Button size="sm" className="mt-2 bg-gradient-hero" onClick={() => accept(p.id)} disabled={acceptProposal.isPending}>
                          <Check className="w-3 h-3 mr-1" /> {t.rd_accept}
                        </Button>
                      )}
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>
    </Layout>
  );
};

export default RequestDetail;
