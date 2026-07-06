import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { GraduationCap, Briefcase } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { GoogleButton } from "@/components/auth/GoogleButton";
import { useLanguage } from "@/contexts/LanguageContext";

const Register = () => {
  const { t } = useLanguage();
  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-warm px-4">
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-lg">
        <div className="text-center mb-8">
          <Link to="/" className="inline-flex items-center gap-2 mb-6">
            <div className="w-10 h-10 rounded-lg bg-gradient-hero flex items-center justify-center">
              <span className="text-primary-foreground font-display font-bold text-xl">K</span>
            </div>
            <span className="font-display font-bold text-2xl text-foreground">
              Kam'<span className="text-gradient-primary">Etud</span>
            </span>
          </Link>
          <h1 className="text-2xl font-display font-bold text-foreground">{t.au_create_account}</h1>
          <p className="text-muted-foreground mt-1">{t.au_choose_profile}</p>
        </div>

        <div className="mb-6 max-w-sm mx-auto">
          <GoogleButton label={t.au_google_signup} />
          <p className="text-xs text-center text-muted-foreground mt-2">{t.au_complete_later}</p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Link to="/inscription/etudiant">
            <Card className="h-full shadow-card hover:shadow-elevated border-border/50 transition-all hover:-translate-y-1 cursor-pointer group">
              <CardContent className="p-8 text-center">
                <div className="w-16 h-16 rounded-2xl bg-gradient-hero flex items-center justify-center mx-auto mb-4 group-hover:scale-110 transition-transform">
                  <GraduationCap className="w-8 h-8 text-primary-foreground" />
                </div>
                <h3 className="font-display font-bold text-lg text-foreground mb-2">{t.au_student_t}</h3>
                <p className="text-sm text-muted-foreground">{t.au_student_d}</p>
              </CardContent>
            </Card>
          </Link>
          <Link to="/inscription/client">
            <Card className="h-full shadow-card hover:shadow-elevated border-border/50 transition-all hover:-translate-y-1 cursor-pointer group">
              <CardContent className="p-8 text-center">
                <div className="w-16 h-16 rounded-2xl bg-gradient-gold flex items-center justify-center mx-auto mb-4 group-hover:scale-110 transition-transform">
                  <Briefcase className="w-8 h-8 text-primary-foreground" />
                </div>
                <h3 className="font-display font-bold text-lg text-foreground mb-2">{t.au_client_t}</h3>
                <p className="text-sm text-muted-foreground">{t.au_client_d}</p>
              </CardContent>
            </Card>
          </Link>
        </div>

        <p className="text-center text-sm text-muted-foreground mt-6">
          {t.au_have_account}{" "}
          <Link to="/connexion" className="text-primary font-medium hover:underline">{t.au_signin}</Link>
        </p>
      </motion.div>
    </div>
  );
};

export default Register;
