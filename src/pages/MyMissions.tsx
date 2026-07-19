import { useState } from "react";
import { motion } from "framer-motion";
import {
  Clock, CheckCircle, AlertTriangle, Package, RotateCcw, XCircle,
  Upload, Coins, Loader2, MessageSquare,
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
import { useMyMissions, useUpdateOrder, useChatMessages, useSendMessage, useRespondToDispute, subscribeToChatMessages, uploadPrivateFile } from "@/hooks/useBackendData";
import { useAuth } from "@/contexts/AuthContext";
import { useLanguage } from "@/contexts/LanguageContext";
import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";

import type { Order, OrderStatus } from "@/types";

const getErrorMessage = (error: unknown) => error instanceof Error ? error.message : String(error);

const MyMissions = () => {
  const { t, locale } = useLanguage();
  const { toast } = useToast();
  const { user } = useAuth();

  const statusConfig: Record<OrderStatus, { label: string; className: string; icon: LucideIcon }> = {
    pending: { label: t.st_pending, className: "bg-muted text-muted-foreground", icon: Clock },
    accepted: { label: t.st_accepted, className: "bg-primary/10 text-primary", icon: CheckCircle },
    in_progress: { label: t.st_in_progress, className: "bg-secondary/20 text-secondary-foreground", icon: Clock },
    delivered: { label: t.st_delivered, className: "bg-primary/10 text-primary", icon: Package },
    revision_requested: { label: t.st_revision, className: "bg-accent/10 text-accent", icon: RotateCcw },
    completed: { label: t.st_completed, className: "bg-primary/10 text-primary", icon: CheckCircle },
    disputed: { label: t.st_disputed, className: "bg-destructive/10 text-destructive", icon: AlertTriangle },
    cancelled: { label: t.st_cancelled, className: "bg-muted text-muted-foreground", icon: XCircle },
    refunded: { label: t.st_refunded, className: "bg-muted text-muted-foreground", icon: RotateCcw },
  };
  const { data: dbOrders, isLoading } = useMyMissions();
  const updateOrder = useUpdateOrder();
  const respondToDispute = useRespondToDispute();
  const sendMessage = useSendMessage();
  const [deliverableNote, setDeliverableNote] = useState("");
  const [deliverableFile, setDeliverableFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [disputeResponse, setDisputeResponse] = useState("");
  const [disputeEvidence, setDisputeEvidence] = useState<File | null>(null);
  const evidenceInputRef = useRef<HTMLInputElement>(null);
  const [chatInput, setChatInput] = useState("");
  const [activeChat, setActiveChat] = useState<string | null>(null);
  const { data: chatMessages } = useChatMessages(activeChat);
  const qc = useQueryClient();

  useEffect(() => {
    if (!activeChat) return;
    const unsub = subscribeToChatMessages(activeChat, () => {
      qc.invalidateQueries({ queryKey: ["chat", activeChat] });
    });
    return unsub;
  }, [activeChat, qc]);

  const handleSendMessage = (orderId: string) => {
    if (!chatInput.trim()) return;
    sendMessage.mutate({ orderId, content: chatInput }, { onSuccess: () => setChatInput("") });
  };

  const orders = dbOrders || [];
  // ACCEPTED signifie que le paiement est confirme, mais que l'etudiant n'a
  // pas encore accepte la mission. Elle reste donc dans l'onglet Demandes.
  const pendingMissions = orders.filter((o) => ["pending", "accepted"].includes(o.status));
  const activeMissions = orders.filter((o) => ["in_progress", "revision_requested"].includes(o.status));
  const completedMissions = orders.filter((o) => ["completed", "delivered", "cancelled", "refunded", "disputed"].includes(o.status));

  const handleAcceptMission = (orderId: string) => {
    updateOrder.mutate({ id: orderId, status: 'in_progress' }, { onSuccess: () => toast({ title: t.mm_accepted }) });
  };

  const handleDeclineMission = (orderId: string) => {
    updateOrder.mutate({ id: orderId, status: 'cancelled' }, { onSuccess: () => toast({ title: t.mm_declined }) });
  };

  const handleSubmitDeliverable = async (orderId: string) => {
    if (!deliverableFile) {
      toast({ title: "Fichier requis", description: "Veuillez joindre votre travail.", variant: "destructive" });
      return;
    }

    setIsUploading(true);
    try {
      const fileName = `${user?.id}/${orderId}/${Date.now()}-${deliverableFile.name}`;
      const filePath = await uploadPrivateFile("deliverables", fileName, deliverableFile);

      await updateOrder.mutateAsync({
        id: orderId,
        status: 'delivered',
        deliverable_note: deliverableNote || t.mm_default_note,
        deliverable_url: filePath,
      });

      // Ancienne Edge Function notify-order-event retiree; Support Service prendra le relais.

      setDeliverableNote("");
      setDeliverableFile(null);
      toast({ title: t.mm_submitted });
    } catch (error) {
      toast({ title: "Erreur", description: "Impossible d'envoyer le fichier : " + getErrorMessage(error), variant: "destructive" });
    } finally {
      setIsUploading(false);
    }
  };

  const handleRespondToDispute = async (orderId: string) => {
    if (!disputeResponse.trim()) return;

    let evidenceUrl = undefined;
    if (disputeEvidence) {
      setIsUploading(true);
      try {
        const fileName = `${user?.id}/${orderId}/evidence-${Date.now()}-${disputeEvidence.name}`;
        evidenceUrl = await uploadPrivateFile("disputes", fileName, disputeEvidence);
      } catch (error) {
        toast({ title: "Erreur upload", description: getErrorMessage(error), variant: "destructive" });
        setIsUploading(false);
        return;
      }
    }

    respondToDispute.mutate({ orderId, statement: disputeResponse, evidenceUrl }, {
      onSuccess: () => {
        setDisputeResponse("");
        setDisputeEvidence(null);
        setIsUploading(false);
        toast({ title: "Déclaration envoyée", description: "Le modérateur a été informé de votre réponse." });
      },
      onError: () => setIsUploading(false)
    });
  };

  if (isLoading) return <Layout><div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div></Layout>;

  const renderMissionCard = (order: Order) => {
    const config = statusConfig[order.status];
    return (
      <motion.div key={order.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
        <Card className="shadow-card border-border/50">
          <CardContent className="p-5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-3">
              <div>
                <h3 className="font-display font-bold text-foreground">{order.gigTitle}</h3>
                <p className="text-sm text-muted-foreground">{t.mm_client} : {order.clientName} · {t.mo_tier} {order.tier}</p>
              </div>
              <div className="flex items-center gap-2">
                <Badge className={config.className}><config.icon className="w-3 h-3 mr-1" /> {config.label}</Badge>
                <span className="font-display font-bold text-primary flex items-center gap-1"><Coins className="w-4 h-4" /> {order.budget.toLocaleString()} FCFA</span>
              </div>
            </div>
            <p className="text-sm text-muted-foreground mb-4">{order.description}</p>
            <div className="flex items-center gap-4 text-xs text-muted-foreground mb-4">
              <span className="flex items-center gap-1"><Clock className="w-3 h-3" /> {t.mm_delivery} : {order.deliveryDate ? new Date(order.deliveryDate).toLocaleDateString(locale) : 'N/A'}</span>
              <span>{t.mm_revisions} : {order.revisionsLeft}</span>
            </div>
            {order.status === "pending" && (
              <div className="flex gap-2">
                <p className="text-xs text-muted-foreground self-center">En attente de la confirmation du paiement client.</p>
                <Button size="sm" variant="outline" className="text-destructive" onClick={() => handleDeclineMission(order.id)}><XCircle className="w-4 h-4 mr-1" /> {t.mm_decline}</Button>
              </div>
            )}
            {order.status === "accepted" && (
              <Button size="sm" className="bg-gradient-hero hover:opacity-90" onClick={() => handleAcceptMission(order.id)}>
                <CheckCircle className="w-4 h-4 mr-1" /> {t.mm_accept}
              </Button>
            )}
            <div className="flex flex-wrap gap-2 mt-2">
              {order.status === 'disputed' && (
                <Dialog>
                  <DialogTrigger asChild><Button size="sm" variant="outline" className="text-destructive"><AlertTriangle className="w-4 h-4 mr-1" /> Répondre au litige</Button></DialogTrigger>
                  <DialogContent>
                    <DialogHeader><DialogTitle className="font-display">Votre version des faits</DialogTitle></DialogHeader>
                    <div className="space-y-4 py-4">
                      <p className="text-sm text-muted-foreground">Le client a ouvert un litige. Expliquez votre situation au modérateur pour qu'il puisse trancher.</p>
                      <Textarea placeholder="Décrivez votre version des faits..." value={disputeResponse} onChange={(e) => setDisputeResponse(e.target.value)} />
                      <div className="space-y-2">
                        <label className="text-xs font-medium">Ajouter une preuve (facultatif)</label>
                        <Input type="file" ref={evidenceInputRef} onChange={(e) => setDisputeEvidence(e.target.files?.[0] || null)} />
                      </div>
                      <Button className="w-full bg-gradient-hero" onClick={() => handleRespondToDispute(order.id)} disabled={respondToDispute.isPending || isUploading || !disputeResponse.trim()}>
                        {isUploading ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : null}
                        Envoyer ma déclaration
                      </Button>
                    </div>
                  </DialogContent>
                </Dialog>
              )}
              {["in_progress", "revision_requested"].includes(order.status) && (
                <Dialog onOpenChange={(open) => !open && setDeliverableFile(null)}>
                  <DialogTrigger asChild><Button size="sm" className="bg-gradient-hero hover:opacity-90"><Upload className="w-4 h-4 mr-1" /> {t.mm_submit}</Button></DialogTrigger>
                  <DialogContent>
                    <DialogHeader><DialogTitle className="font-display">{t.mm_submit}</DialogTitle></DialogHeader>
                    <div className="space-y-4 py-4">
                      <div
                        className="border-2 border-dashed border-border rounded-lg p-6 text-center cursor-pointer hover:border-primary/50 transition-colors"
                        onClick={() => fileInputRef.current?.click()}
                      >
                        <Upload className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
                        <p className="text-sm text-muted-foreground">
                          {deliverableFile ? deliverableFile.name : t.mm_dragdrop}
                        </p>
                        <input
                          ref={fileInputRef}
                          type="file"
                          className="hidden"
                          onChange={(e) => setDeliverableFile(e.target.files?.[0] || null)}
                        />
                      </div>
                      <Textarea placeholder={t.mm_note_client} value={deliverableNote} onChange={(e) => setDeliverableNote(e.target.value)} />
                      <Button
                        className="w-full bg-gradient-hero"
                        onClick={() => handleSubmitDeliverable(order.id)}
                        disabled={isUploading || !deliverableFile}
                      >
                        {isUploading ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : null}
                        {t.mo_send}
                      </Button>
                    </div>
                  </DialogContent>
                </Dialog>
              )}
              {order.status === 'delivered' && (
                <p className="text-[10px] text-muted-foreground italic self-center ml-2">Paiement automatique garanti après 72h si le client ne signale aucun problème.</p>
              )}
              {order.status !== "pending" && (
                <Dialog>
                  <DialogTrigger asChild><Button size="sm" variant="outline" onClick={() => setActiveChat(order.id)}><MessageSquare className="w-4 h-4 mr-1" /> {t.mm_chat_client}</Button></DialogTrigger>
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
                      {(!chatMessages || chatMessages.length === 0) && (
                        <p className="text-sm text-muted-foreground text-center py-8">{t.mm_no_msg}</p>
                      )}
                    </div>
                    <div className="flex gap-2 pt-2 border-t border-border">
                      <Textarea placeholder={t.mo_msg_ph} value={chatInput} onChange={(e) => setChatInput(e.target.value)} className="min-h-[60px]" onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); handleSendMessage(order.id); }}} />
                      <Button size="sm" onClick={() => handleSendMessage(order.id)} className="bg-gradient-hero">{t.mo_send}</Button>
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
        <h1 className="text-2xl font-display font-bold text-foreground mb-6">{t.mm_title}</h1>
        <Tabs defaultValue="pending">
          <TabsList>
            <TabsTrigger value="pending">{t.mm_demands} ({pendingMissions.length})</TabsTrigger>
            <TabsTrigger value="active">{t.mm_active} ({activeMissions.length})</TabsTrigger>
            <TabsTrigger value="done">{t.mm_done} ({completedMissions.length})</TabsTrigger>
          </TabsList>
          <TabsContent value="pending" className="space-y-4 mt-4">
            {pendingMissions.length === 0 ? <Card className="shadow-card border-border/50 p-8 text-center"><p className="text-muted-foreground">{t.mm_none_pending}</p></Card> : pendingMissions.map(renderMissionCard)}
          </TabsContent>
          <TabsContent value="active" className="space-y-4 mt-4">
            {activeMissions.length === 0 ? <Card className="shadow-card border-border/50 p-8 text-center"><p className="text-muted-foreground">{t.mm_none_active}</p></Card> : activeMissions.map(renderMissionCard)}
          </TabsContent>
          <TabsContent value="done" className="space-y-4 mt-4">
            {completedMissions.length === 0 ? <Card className="shadow-card border-border/50 p-8 text-center"><p className="text-muted-foreground">{t.mm_none_done}</p></Card> : completedMissions.map(renderMissionCard)}
          </TabsContent>
        </Tabs>
      </div>
    </Layout>
  );
};

export default MyMissions;
