import { Link } from "react-router-dom";
import { Loader2, Coins, MapPin, Calendar, Plus } from "lucide-react";
import Layout from "@/components/layout/Layout";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { useMyRequests } from "@/hooks/useBackendData";
import { useLanguage } from "@/contexts/LanguageContext";

const MyRequests = () => {
  const { t, locale } = useLanguage();
  const { data: requests, isLoading } = useMyRequests();

  const statusBadge = (s: string) => {
    const map: Record<string, { label: string; cls: string }> = {
      open: { label: t.mr_st_open, cls: "bg-secondary/20 text-secondary-foreground" },
      assigned: { label: t.mr_st_assigned, cls: "bg-primary/10 text-primary" },
      cancelled: { label: t.mr_st_cancelled, cls: "bg-muted text-muted-foreground" },
      closed: { label: t.mr_st_closed, cls: "bg-muted text-muted-foreground" },
    };
    const v = map[s] || map.open;
    return <Badge className={v.cls}>{v.label}</Badge>;
  };

  return (
    <Layout>
      <div className="container mx-auto px-4 py-8 max-w-4xl">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-display font-bold text-foreground">{t.mr_title}</h1>
          <Button asChild className="bg-gradient-hero"><Link to="/demandes"><Plus className="w-4 h-4 mr-1" /> {t.mr_new}</Link></Button>
        </div>

        {isLoading ? (
          <div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>
        ) : !requests || requests.length === 0 ? (
          <Card className="shadow-card border-border/50 p-8 text-center">
            <p className="text-muted-foreground">{t.mr_none}</p>
            <Button asChild className="mt-4 bg-gradient-hero"><Link to="/demandes">{t.mr_publish}</Link></Button>
          </Card>
        ) : (
          <div className="space-y-3">
            {requests.map((r) => (
              <Link key={r.id} to={`/demandes/${r.id}`}>
                <Card className="shadow-card border-border/50 hover:shadow-elevated transition-all">
                  <CardContent className="p-5">
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <h3 className="font-display font-bold text-foreground">{r.title}</h3>
                          {statusBadge(r.status)}
                        </div>
                        <p className="text-sm text-muted-foreground line-clamp-2 mt-1">{r.description}</p>
                        <div className="flex wrap gap-3 mt-3 text-xs text-muted-foreground">
                          {r.category && <Badge variant="outline">{r.category}</Badge>}
                          {r.location && <span className="flex items-center gap-1"><MapPin className="w-3 h-3" /> {r.location}</span>}
                          {r.deadline && <span className="flex items-center gap-1"><Calendar className="w-3 h-3" /> {new Date(r.deadline).toLocaleDateString(locale)}</span>}
                        </div>
                      </div>
                      <div className="flex items-center gap-1 text-primary font-bold">
                        <Coins className="w-4 h-4" /> {Number(r.budget).toLocaleString()} FCFA
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </div>
    </Layout>
  );
};

export default MyRequests;
