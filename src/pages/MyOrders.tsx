import { useState, useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import {
  Clock, CheckCircle, AlertTriangle, Star, MessageSquare, Package,
  ThumbsUp, RotateCcw, Flag, XCircle, Loader2, Download,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import Layout from "@/components/layout/Layout";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/contexts/AuthContext";
import { useMyOrders, useUpdateOrder, useChatMessages, useSendMessage, useCreateReview, useCreateDispute, subscribeToChatMessages, getSignedFileUrl, uploadPrivateFile, initiateOrderPayment, releaseOrderPayment } from "@/hooks/useBackendData";
import { useLanguage } from "@/contexts/LanguageContext";
import { useQueryClient } from "@tanstack/react-query";

import type { Order, OrderStatus } from "@/types";

const getErrorMessage = (error: unknown) => error instanceof Error ? error.message : String(error);

const MyOrders = () => {
  const { t, locale } = useLanguage();
  const { user } = useAuth();
  const { toast } = useToast();

  const statusConfig: Record<OrderStatus, { label: string; className: string; icon: LucideIcon }> = {
    pending: { label: t.st_pending, className: "bg-muted text-muted-foreground", icon: Clock },
    accepted: { label: t.st_accepted, className: "bg-primary/10 text-primary", icon: CheckCircle },
    in_progress: { label: t.st_in_progress, className: "bg-secondary/20 text-secondary-foreground", icon: Clock },
    delivered: { label: t.st_delivered, className: "bg-primary/10 text-primary", icon: Package },
    revision_requested: { label: t.st_revision_short, className: "bg-accent/10 text-accent", icon: RotateCcw },
    completed: { label: t.st_completed, className: "bg-primary/10 text-primary", icon: CheckCircle },
    disputed: { label: t.st_disputed, className: "bg-destructive/10 text-destructive", icon: AlertTriangle },
    cancelled: { label: t.st_cancelled, className: "bg-muted text-muted-foreground", icon: XCircle },
    refunded: { label: t.st_refunded, className: "bg-muted text-muted-foreground", icon: RotateCcw },
  };
  const { data: dbOrders, isLoading } = useMyOrders();
  const updateOrder = useUpdateOrder();
  const createDispute = useCreateDispute();
  const sendMessage = useSendMessage();
  const createReview = useCreateReview();
  const [reviewText, setReviewText] = useState("");
  const [reviewRating, setReviewRating] = useState(5);
  const [chatInput, setChatInput] = useState("");
  const [activeChat, setActiveChat] = useState<string | null>(null);
  const [disputeText, setDisputeText] = useState("");
  const [disputeEvidence, setDisputeEvidence] = useState<File | null>(null);
  const evidenceInputRef = useRef<HTMLInputElement>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [selectedOrderForDispute, setSelectedOrderForDispute] = useState<Order | null>(null);
  const [payingOrderId, setPayingOrderId] = useState<string | null>(null);
  const [openingDeliverableId, setOpeningDeliverableId] = useState<string | null>(null);

  const orders = dbOrders || [];
  const activeOrders = orders.filter((o) => !["completed", "cancelled", "refunded"].includes(o.status));
  const pastOrders = orders.filter((o) => ["completed", "cancelled", "refunded"].includes(o.status));

  const { data: chatMessages } = useChatMessages(activeChat);
  const qc = useQueryClient();

  useEffect(() => {
    if (!activeChat) return;
    const unsub = subscribeToChatMessages(activeChat, () => {
      qc.invalidateQueries({ queryKey: ["chat", activeChat] });
    });
    return unsub;
  }, [activeChat, qc]);

  const handleAcceptDeliverable = async (orderId: string) => {
    try {
      await updateOrder.mutateAsync({ id: orderId, status: 'completed' });
      try {
        await releaseOrderPayment(orderId);
        toast({ title: t.mo_valid_ok, description: t.mo_valid_ok_d });
      } catch {
        toast({ title: t.mo_valid_pending, description: t.mo_valid_pending_d });
      }
    } catch (error) {
      toast({ title: t.c_error, description: getErrorMessage(error), variant: "destructive" });
    }
  };

  const handleOpenDeliverable = async (order: Order) => {
    if (!order.deliverableUrl) return;

    // Ouvrir la fenetre pendant le clic utilisateur evite que le navigateur
    // la bloque apres le telechargement asynchrone du fichier prive.
    const previewWindow = window.open("about:blank", "_blank");
    if (previewWindow) previewWindow.opener = null;

    try {
      if (!previewWindow) {
        throw new Error("Autorisez les fenêtres contextuelles pour consulter le livrable.");
      }
      setOpeningDeliverableId(order.id);
      const url = await getSignedFileUrl("deliverables", order.deliverableUrl);
      previewWindow.location.href = url;
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (error) {
      previewWindow?.close();
      toast({ title: t.c_error, description: getErrorMessage(error), variant: "destructive" });
    } finally {
      setOpeningDeliverableId(null);
    }
  };

  const handleRequestRevision = (order: Order) => {
    if (order.revisionsLeft > 0) {
      updateOrder.mutate(
        { id: order.id, status: 'revision_requested', revisions_left: order.revisionsLeft - 1 },
        {
          onSuccess: () => {
            toast({ title: t.mo_revision_asked });
            // Ancienne Edge Function notify-order-event retiree; Support Service prendra le relais.
          },
        },
      );
    }
  };

  const handleDispute = async () => {
    if (!selectedOrderForDispute || !disputeText.trim()) return;

    let evidenceUrl = undefined;
    if (disputeEvidence) {
      setIsUploading(true);
      try {
        const fileName = `${user?.id}/${selectedOrderForDispute.id}/client-evidence-${Date.now()}-${disputeEvidence.name}`;
        evidenceUrl = await uploadPrivateFile("disputes", fileName, disputeEvidence);
      } catch (error) {
        toast({ title: t.c_error, description: "Upload échoué: " + getErrorMessage(error), variant: "destructive" });
        setIsUploading(false);
        return;
      }
    }

    try {
      await createDispute.mutateAsync({
        orderId: selectedOrderForDispute.id,
        gigTitle: selectedOrderForDispute.gigTitle,
        studentId: selectedOrderForDispute.studentId,
        studentName: selectedOrderForDispute.studentName,
        clientStatement: disputeText,
        clientEvidenceUrl: evidenceUrl,
      });
      toast({ title: t.mo_dispute_open });
      setDisputeText("");
      setDisputeEvidence(null);
      setSelectedOrderForDispute(null);
    } catch (error) {
      toast({ title: t.c_error, description: getErrorMessage(error), variant: "destructive" });
    } finally {
      setIsUploading(false);
    }
  };

  const handleSendMessage = (orderId: string) => {
    if (!chatInput.trim()) return;
    sendMessage.mutate({ orderId, content: chatInput }, { onSuccess: () => setChatInput("") });
  };

  const handlePayOrder = async (order: Order) => {
    try {
      setPayingOrderId(order.id);
      const response = await initiateOrderPayment(order.id, user?.phone || '');
      await qc.invalidateQueries({ queryKey: ['my-orders'] });
      const ussd = response.ussdCode;
      toast({ title: t.op_validate_phone, description: ussd ? `${t.op_ussd} ${ussd} ${t.op_to_confirm} ${order.budget.toLocaleString()} FCFA.` : response.message || `${t.op_request_sent_to} ${order.budget.toLocaleString()} FCFA ${t.op_has_been_sent_to} ${user?.phone}.` });
    } catch (error) {
      toast({ title: t.c_error, description: getErrorMessage(error), variant: "destructive" });
    } finally {
      setPayingOrderId(null);
    }
  };

  const handleSubmitReview = (order: Order) => {
    createReview.mutate({
      orderId: order.id, gigId: order.gigId, studentId: order.studentId,
      rating: reviewRating, text: reviewText,
    }, { onSuccess: () => { toast({ title: t.mo_thanks }); setReviewText(""); setReviewRating(5); } });
  };

  if (isLoading) return <Layout><div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div></Layout>;

  const renderOrderCard = (order: Order) => {
    const config = statusConfig[order.status];
    return (
      <motion.div key={order.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
        <Card className="shadow-card border-border/50">
          <CardContent className="p-5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
              <div>
                <h3 className="font-display font-bold text-foreground">{order.gigTitle}</h3>
                <p className="text-sm text-muted-foreground">{t.mo_by} {order.studentName} · {t.mo_tier} {order.tier}</p>
              </div>
              <div className="flex items-center gap-2">
                <Badge className={config.className}><config.icon className="w-3 h-3 mr-1" /> {config.label}</Badge>
                <span className="font-display font-bold text-primary">{order.budget.toLocaleString()} FCFA</span>
              </div>
            </div>
            <p className="text-sm text-muted-foreground mb-4 line-clamp-2">{order.description}</p>

            {order.deliverableUrl && (
              <div className="p-4 rounded-lg bg-primary/5 border border-primary/20 mb-4">
                <p className="text-sm font-medium text-foreground mb-1">📦 {t.mo_deliverable}</p>
                <p className="text-sm text-muted-foreground">{order.deliverableNote || t.mo_deliverable_default}</p>
                <div className="flex gap-2 mt-3">
                  {order.deliverableUrl && (
                    <Button size="sm" variant="outline" onClick={() => handleOpenDeliverable(order)} disabled={openingDeliverableId === order.id}>
                      {openingDeliverableId === order.id ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : <Download className="w-4 h-4 mr-1" />}
                      {t.ad_download}
                    </Button>
                  )}
                  {order.status === "delivered" && (
                    <Button size="sm" onClick={() => handleAcceptDeliverable(order.id)} className="bg-gradient-hero hover:opacity-90"><ThumbsUp className="w-4 h-4 mr-1" /> {t.mo_validate}</Button>
                  )}
                  {order.status === "delivered" && order.revisionsLeft > 0 && <Button size="sm" variant="outline" onClick={() => handleRequestRevision(order)}><RotateCcw className="w-4 h-4 mr-1" /> {t.mo_revision} ({order.revisionsLeft})</Button>}
                </div>
                {order.status === "delivered" && <p className="text-[10px] text-muted-foreground mt-2 italic">Sans action de votre part, la commande sera automatiquement validée dans 72h.</p>}
              </div>
            )}

            <div className="flex flex-wrap gap-2">
              {order.status === 'pending' && (
                <Button size="sm" className="bg-gradient-hero hover:opacity-90" onClick={() => handlePayOrder(order)} disabled={payingOrderId === order.id}>
                  {payingOrderId === order.id ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : <Package className="w-4 h-4 mr-1" />}
                  {t.op_pay_btn}
                </Button>
              )}
              <Dialog>
                <DialogTrigger asChild><Button size="sm" variant="outline" onClick={() => setActiveChat(order.id)}><MessageSquare className="w-4 h-4 mr-1" /> {t.mo_chat}</Button></DialogTrigger>
                <DialogContent className="max-w-lg max-h-[80vh] flex flex-col">
                  <DialogHeader><DialogTitle className="font-display">{t.mo_chat} — {order.gigTitle}</DialogTitle></DialogHeader>
                  <div className="flex-1 overflow-y-auto space-y-3 py-4 min-h-[300px]">
                    {(chatMessages || []).map((msg) => (
                      <div key={msg.id} className={`flex ${msg.senderId === user?.id ? "justify-end" : "justify-start"}`}>
                        {msg.type === "system" ? (
                          <p className="text-xs text-muted-foreground text-center w-full">{msg.content}</p>
                        ) : (
                          <div className={`max-w-[75%] p-3 rounded-xl text-sm ${msg.senderId === user?.id ? "bg-primary text-primary-foreground" : "bg-muted text-foreground"}`}>
                            <p className="text-xs font-medium mb-1 opacity-70">{msg.senderName}</p>{msg.content}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                  <div className="flex gap-2 pt-2 border-t border-border">
                    <Textarea placeholder={t.mo_msg_ph} value={chatInput} onChange={(e) => setChatInput(e.target.value)} className="min-h-[60px]" onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); handleSendMessage(order.id); }}} />
                    <Button size="sm" onClick={() => handleSendMessage(order.id)} className="bg-gradient-hero">{t.mo_send}</Button>
                  </div>
                </DialogContent>
              </Dialog>
              {["in_progress", "delivered", "revision_requested"].includes(order.status) && (
                <Dialog open={selectedOrderForDispute?.id === order.id} onOpenChange={(open) => !open && setSelectedOrderForDispute(null)}>
                  <DialogTrigger asChild>
                    <Button size="sm" variant="outline" className="text-destructive" onClick={() => setSelectedOrderForDispute(order)}><Flag className="w-4 h-4 mr-1" /> {t.mo_dispute}</Button>
                  </DialogTrigger>
                  <DialogContent>
                    <DialogHeader><DialogTitle className="font-display">{t.mo_dispute} — {order.gigTitle}</DialogTitle></DialogHeader>
                    <div className="space-y-4 py-4">
                      <p className="text-sm text-muted-foreground">Expliquez pourquoi vous contestez cette mission. Un modérateur étudiera votre demande.</p>
                      <Textarea placeholder="Motif du litige..." value={disputeText} onChange={(e) => setDisputeText(e.target.value)} />
                      <div className="space-y-2">
                        <label className="text-xs font-medium">Ajouter une preuve (capture d'écran, etc.)</label>
                        <Input type="file" ref={evidenceInputRef} onChange={(e) => setDisputeEvidence(e.target.files?.[0] || null)} />
                      </div>
                      <Button className="w-full bg-destructive text-destructive-foreground hover:bg-destructive/90" onClick={handleDispute} disabled={createDispute.isPending || isUploading || !disputeText.trim()}>
                        {isUploading ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : null}
                        {createDispute.isPending ? t.c_loading : "Ouvrir un litige"}
                      </Button>
                    </div>
                  </DialogContent>
                </Dialog>
              )}
              {order.status === "completed" && (
                <Dialog>
                  <DialogTrigger asChild><Button size="sm" variant="outline"><Star className="w-4 h-4 mr-1" /> {t.mo_review}</Button></DialogTrigger>
                  <DialogContent>
                    <DialogHeader><DialogTitle className="font-display">{t.mo_review_for} {order.studentName}</DialogTitle></DialogHeader>
                    <div className="space-y-4 py-4">
                      <div className="flex gap-1 justify-center">{[1,2,3,4,5].map((s) => <button key={s} onClick={() => setReviewRating(s)}><Star className={`w-8 h-8 ${s <= reviewRating ? "text-secondary fill-secondary" : "text-muted"}`} /></button>)}</div>
                      <Textarea placeholder={t.mo_review_ph} value={reviewText} onChange={(e) => setReviewText(e.target.value)} />
                      <Button className="w-full bg-gradient-hero" onClick={() => handleSubmitReview(order)}>{t.mo_publish}</Button>
                    </div>
                  </DialogContent>
                </Dialog>
              )}
            </div>
          </CardContent>
        </Card>
      </motion.div>
    );
  };

  return (
    <Layout>
      <div className="container mx-auto px-4 py-8 max-w-4xl">
        <h1 className="text-2xl font-display font-bold text-foreground mb-6">{t.mo_title}</h1>
        <Tabs defaultValue="active">
          <TabsList>
            <TabsTrigger value="active">{t.mo_active} ({activeOrders.length})</TabsTrigger>
            <TabsTrigger value="past">{t.mo_past} ({pastOrders.length})</TabsTrigger>
          </TabsList>
          <TabsContent value="active" className="space-y-4 mt-4">
            {activeOrders.length === 0 ? (
              <Card className="shadow-card border-border/50 p-8 text-center"><p className="text-muted-foreground">{t.mo_none_active}</p><Button asChild className="mt-4 bg-gradient-hero"><Link to="/services">{t.mo_explore}</Link></Button></Card>
            ) : activeOrders.map(renderOrderCard)}
          </TabsContent>
          <TabsContent value="past" className="space-y-4 mt-4">
            {pastOrders.length === 0 ? <p className="text-center text-muted-foreground py-8">{t.mo_none_past}</p> : pastOrders.map(renderOrderCard)}
          </TabsContent>
        </Tabs>
      </div>
    </Layout>
  );
};

export default MyOrders;
