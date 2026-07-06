import { motion } from "framer-motion";
import { Link } from "react-router-dom";
import { Loader2, Coins, Calendar, Clock, CheckCircle, XCircle, type LucideIcon } from "lucide-react";
import Layout from "@/components/layout/Layout";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { useMyProposals } from "@/hooks/useBackendData";
import { useLanguage } from "@/contexts/LanguageContext";

const MyProposals = () => {
  const { t, locale } = useLanguage();
  const { data: proposals, isLoading } = useMyProposals();

  const statusConfig: Record<string, { label: string; cls: string; icon: LucideIcon }> = {
    pending: { label: t.prp_status_pending, cls: "bg-muted text-muted-foreground", icon: Clock },
    accepted: { label: t.prp_status_accepted, cls: "bg-primary/10 text-primary", icon: CheckCircle },
    rejected: { label: t.prp_status_rejected, cls: "bg-destructive/10 text-destructive", icon: XCircle },
  };

  return (
    <Layout>
      <div className="container mx-auto px-4 py-8 max-w-4xl">
        <h1 className="text-2xl font-display font-bold text-foreground mb-6">{t.prp_title}</h1>

        {isLoading ? (
          <div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>
        ) : !proposals || proposals.length === 0 ? (
          <Card className="shadow-card border-border/50 p-8 text-center">
            <p className="text-muted-foreground">{t.prp_none}</p>
          </Card>
        ) : (
          <div className="space-y-4">
            {proposals.map((p) => {
              const config = statusConfig[p.status] || statusConfig.pending;
              return (
                <motion.div key={p.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
                  <Card className="shadow-card border-border/50 overflow-hidden">
                    <CardContent className="p-5">
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <h3 className="font-display font-bold text-foreground line-clamp-1">{p.gig_requests?.title || "Demande"}</h3>
                            <Badge className={config.cls}><config.icon className="w-3 h-3 mr-1" /> {config.label}</Badge>
                          </div>
                          <p className="text-sm text-muted-foreground line-clamp-2 italic mb-3">"{p.message}"</p>
                          <div className="flex flex-wrap gap-4 text-xs text-muted-foreground">
                            <span className="flex items-center gap-1"><Coins className="w-3 h-3" /> {p.price.toLocaleString()} FCFA</span>
                            <span className="flex items-center gap-1"><Clock className="w-3 h-3" /> {p.delivery_days} {t.rd_delay_days}</span>
                            <span className="flex items-center gap-1"><Calendar className="w-3 h-3" /> {new Date(p.created_at).toLocaleDateString(locale)}</span>
                          </div>
                        </div>
                        {p.status === 'accepted' && (
                          <div className="shrink-0">
                            <Link to="/mes-missions" className="text-primary text-sm font-medium hover:underline flex items-center gap-1">
                              {t.prp_view_mission} <CheckCircle className="w-4 h-4" />
                            </Link>
                          </div>
                        )}
                      </div>
                    </CardContent>
                  </Card>
                </motion.div>
              );
            })}
          </div>
        )}
      </div>
    </Layout>
  );
};

export default MyProposals;
