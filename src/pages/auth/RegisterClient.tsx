import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { User, Mail, Lock, Phone, Eye, EyeOff, MapPin } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/contexts/AuthContext";
import { useCities } from "@/hooks/useBackendData";
import { GoogleButton } from "@/components/auth/GoogleButton";
import { useLanguage } from "@/contexts/LanguageContext";
import { validateCameroonPhone } from "@/lib/utils";

const logoFull ="logoFull.png";
const FALLBACK_CITIES = ["Yaoundé", "Douala", "Bafoussam", "Bamenda", "Garoua", "Maroua", "Bertoua", "Ebolowa", "Ngaoundéré", "Buea"];

const RegisterClient = () => {
  const [showPassword, setShowPassword] = useState(false);
  const [acceptTerms, setAcceptTerms] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const { toast } = useToast();
  const navigate = useNavigate();
  const { register } = useAuth();
  const { data: dbCities } = useCities();
  const { t } = useLanguage();
  const cities = dbCities && dbCities.length > 0 ? dbCities.map((c) => c.name) : FALLBACK_CITIES;

  const [form, setForm] = useState({
    firstName: "", lastName: "", email: "", phone: "", city: "", password: "", confirmPassword: "",
  });

  const update = (key: string, value: string) => setForm((prev) => ({ ...prev, [key]: value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (form.password !== form.confirmPassword) {
      setError("Les mots de passe ne correspondent pas.");
      return;
    }

    if (!validateCameroonPhone(form.phone)) {
      toast({ title: t.c_error, description: "Format de numéro invalide.", variant: "destructive" });
      return;
    }

    setIsLoading(true);
    setError("");
    const { success, error: registrationError } = await register({
      email: form.email, password: form.password,
      firstName: form.firstName, lastName: form.lastName,
      phone: form.phone, city: form.city, role: 'client',
    });
    setIsLoading(false);
    if (success) {
      toast({ title: t.au_signup_ok, description: t.au_signup_ok_d });
      // Le backend renvoie maintenant un JWT des l'inscription.
      // Ancien comportement : navigate("/connexion");
      navigate("/");
    } else {
      setError(registrationError || t.au_err_register);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-warm px-4 py-8">
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-md">
        <div className="text-center mb-8">
          <Link to="/" className="flex items-center justify-center gap-2">
            <div className="flex items-center rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 transition-all">
              <img
                src={logoFull}
                alt="Kam'Etud - Retour à l'accueil"
                className="h-7 w-auto md:h-9 object-contain select-none"
              />
            </div>
          </Link>
          <h1 className="text-2xl font-display font-bold text-foreground">{t.au_signup_client}</h1>
          <p className="text-muted-foreground mt-1">{t.au_signup_client_sub}</p>
        </div>

        <Card className="shadow-elevated border-border/50">
          <CardContent className="pt-6">
            <div className="space-y-3 mb-4">
              <GoogleButton label={t.au_google_signup} />
              <div className="relative">
                <div className="absolute inset-0 flex items-center"><span className="w-full border-t border-border" /></div>
                <div className="relative flex justify-center text-xs"><span className="bg-card px-2 text-muted-foreground">{t.au_or_email}</span></div>
              </div>
            </div>
            <form onSubmit={handleSubmit} className="space-y-4">
              {error && <p className="text-sm text-destructive bg-destructive/10 p-2 rounded">{error}</p>}
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2"><Label>{t.au_first_name}</Label><Input placeholder="Marie" value={form.firstName} onChange={(e) => update("firstName", e.target.value)} required /></div>
                <div className="space-y-2"><Label>{t.au_last_name}</Label><Input placeholder="Fotso" value={form.lastName} onChange={(e) => update("lastName", e.target.value)} required /></div>
              </div>
              <div className="space-y-2">
                <Label>{t.au_email}</Label>
                <div className="relative"><Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" /><Input type="email" placeholder="marie@email.com" className="pl-10" value={form.email} onChange={(e) => update("email", e.target.value)} required /></div>
              </div>
              <div className="space-y-2">
                <Label>{t.au_phone}</Label>
                <div className="relative">
                  <Phone className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input placeholder="+237 6XX XXX XXX" className="pl-10" value={form.phone} onChange={(e) => update("phone", e.target.value)} required />
                </div>
                {form.phone && !validateCameroonPhone(form.phone) && (
                  <p className="text-[10px] text-destructive">Format invalide (Ex: 6XXXXXXXX)</p>
                )}
              </div>
              <div className="space-y-2">
                <Label>{t.au_city}</Label>
                <Select value={form.city} onValueChange={(v) => update("city", v)}>
                  <SelectTrigger><MapPin className="w-4 h-4 mr-2 text-muted-foreground" /><SelectValue placeholder={t.au_city_yours} /></SelectTrigger>
                  <SelectContent>{cities.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>{t.au_password}</Label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input type={showPassword ? "text" : "password"} placeholder={t.au_pw_hint} className="pl-10 pr-10" value={form.password} onChange={(e) => update("password", e.target.value)} required />
                  <button type="button" onClick={() => setShowPassword(!showPassword)} className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>
              <div className="space-y-2"><Label>{t.au_confirm}</Label><Input type="password" placeholder="••••••••" value={form.confirmPassword} onChange={(e) => update("confirmPassword", e.target.value)} required /></div>
              <div className="flex items-start gap-2">
                <Checkbox id="terms" checked={acceptTerms} onCheckedChange={(v) => setAcceptTerms(v === true)} />
                <label htmlFor="terms" className="text-sm text-muted-foreground leading-tight">
                  {t.au_accept} <Link to="/conditions" className="text-primary hover:underline">{t.au_terms}</Link> {t.au_and} <Link to="/confidentialite" className="text-primary hover:underline">{t.au_privacy}</Link>
                </label>
              </div>
              <Button type="submit" className="w-full bg-gradient-hero hover:opacity-90" disabled={isLoading || !acceptTerms}>
                {isLoading ? t.au_creating : t.au_create_btn}
              </Button>
            </form>
          </CardContent>
        </Card>

        <p className="text-center text-sm text-muted-foreground mt-6">
          {t.au_have_account} <Link to="/connexion" className="text-primary font-medium hover:underline">{t.au_signin}</Link>
          {" · "}<Link to="/inscription/etudiant" className="text-primary font-medium hover:underline">{t.au_or_student}</Link>
        </p>
      </motion.div>
    </div>
  );
};

export default RegisterClient;
