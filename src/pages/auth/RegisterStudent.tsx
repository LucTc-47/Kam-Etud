import { useState, useRef } from "react";
import { Link, useNavigate } from "react-router-dom";
import { motion, AnimatePresence } from "framer-motion";
import {
  User, Mail, Lock, Phone, GraduationCap, Upload, Camera,
  ChevronRight, ChevronLeft, Check, Eye, EyeOff,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Checkbox } from "@/components/ui/checkbox";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/contexts/AuthContext";
import { api, uploadApiFile } from "@/lib/api";
import { GoogleButton } from "@/components/auth/GoogleButton";
import { useLanguage } from "@/contexts/LanguageContext";
import { validateCameroonPhone } from "@/lib/utils";

const logoFull ="/logoFull.png";
const SKILLS = [
  "Rédaction académique", "Programmation", "Design graphique", "Traduction",
  "Tutorat mathématiques", "Tutorat physique", "Comptabilité", "Marketing digital",
  "Montage vidéo", "Photographie", "Développement web", "Data science",
];

const UNIVERSITIES = [
  "Université de Yaoundé I", "Université de Yaoundé II", "Université de Douala",
  "Université de Dschang", "Université de Buea", "Université de Bamenda",
  "Université de Maroua", "Université de Ngaoundéré", "Autre",
];

const RegisterStudent = () => {
  const { t } = useLanguage();
  const steps = [
    { title: t.au_step1, icon: User },
    { title: t.au_step2, icon: GraduationCap },
    { title: t.au_step3, icon: Upload },
  ];
  const [step, setStep] = useState(0);
  const [showPassword, setShowPassword] = useState(false);
  const [selectedSkills, setSelectedSkills] = useState<string[]>([]);
  const [acceptTerms, setAcceptTerms] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const { toast } = useToast();
  const navigate = useNavigate();
  const { register } = useAuth();
  // Ancien hook : const createVerification = useCreateVerification();
  const idFileRef = useRef<HTMLInputElement>(null);
  const selfieRef = useRef<HTMLInputElement>(null);
  const cardRef = useRef<HTMLInputElement>(null);

  const [form, setForm] = useState({
    firstName: "", lastName: "", email: "", phone: "", password: "", confirmPassword: "",
    university: "", faculty: "", level: "", bio: "",
    idType: "", idFile: null as File | null, selfieFile: null as File | null,
    studentCardFile: null as File | null,
  });

  const update = (key: string, value: string | File | null) => setForm((prev) => ({ ...prev, [key]: value }));
  const toggleSkill = (skill: string) => setSelectedSkills((prev) => prev.includes(skill) ? prev.filter((s) => s !== skill) : [...prev, skill]);

  const canNext = () => {
    if (step === 0) {
      const isPhoneValid = validateCameroonPhone(form.phone);
      return form.firstName && form.lastName && form.email && isPhoneValid && form.password && form.password === form.confirmPassword;
    }
    if (step === 1) return form.university && form.level && selectedSkills.length > 0;
    if (step === 2) return form.idType && form.idFile && form.selfieFile && acceptTerms;
    return false;
  };

  const handleSubmit = async () => {
    setIsLoading(true);
    setError("");
    
    const { success, userId, error: registrationError } = await register({
      email: form.email, password: form.password,
      firstName: form.firstName, lastName: form.lastName,
      phone: form.phone, role: 'student',
      university: form.university, faculty: form.faculty,
      level: form.level, bio: form.bio, skills: selectedSkills,
    });

    if (!success || !userId) {
      setIsLoading(false);
      setError(registrationError || t.au_err_register);
      return;
    }

    try {
      let idFileUrl = "";
      let selfieFileUrl = "";
      let studentCardUrl = "";

      if (form.idFile) {
        idFileUrl = await uploadApiFile(form.idFile, "private");
      }
      if (form.selfieFile) {
        selfieFileUrl = await uploadApiFile(form.selfieFile, "private");
      }
      if (form.studentCardFile) {
        studentCardUrl = await uploadApiFile(form.studentCardFile, "private");
      }

      // Le backend deduit l'identite de l'etudiant depuis le JWT.
      await api.post("/api/verifications", {
        university: form.university,
        id_type: form.idType,
        id_file_url: idFileUrl,
        selfie_url: selfieFileUrl,
        student_card_url: studentCardUrl,
      });

      toast({ title: t.au_sent, description: t.au_sent_d });
      setIsLoading(false);
      navigate("/", { state: { confetti: true } });
    } catch (e: unknown) {
      console.error("Verification upload error:", e);
      // Le compte et son JWT existent deja, mais l'envoi des pieces a echoue.
      // Ne PAS afficher la confirmation festive (confetti) : ce serait une
      // fausse reussite. On dirige l'etudiant vers son espace, ou la banniere
      // de verification lui permet de redeposer son dossier.
      toast({
        title: t.au_kyc_failed,
        description: t.au_kyc_failed_d,
        variant: "destructive",
      });
      setIsLoading(false);
      navigate("/mes-gigs");
    }
  };

  const progress = ((step + 1) / steps.length) * 100;

  return (
    <div className="min-h-screen bg-gradient-warm py-8 px-4">
      <div className="max-w-2xl mx-auto">
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
          <h1 className="text-2xl font-display font-bold text-foreground">{t.au_signup_student}</h1>
          <p className="text-muted-foreground mt-1">{t.au_signup_student_sub}</p>
        </div>

        <div className="mb-8">
          <Progress value={progress} className="h-2 mb-4" />
          <div className="flex justify-between">
            {steps.map((s, i) => (
              <div key={i} className="flex items-center gap-2">
                <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold transition-colors ${i < step ? "bg-primary text-primary-foreground" : i === step ? "bg-gradient-hero text-primary-foreground" : "bg-muted text-muted-foreground"}`}>
                  {i < step ? <Check className="w-4 h-4" /> : i + 1}
                </div>
                <span className={`text-xs hidden sm:inline ${i === step ? "text-foreground font-medium" : "text-muted-foreground"}`}>{s.title}</span>
              </div>
            ))}
          </div>
        </div>

        <Card className="shadow-elevated border-border/50">
          <CardHeader>
            <CardTitle className="font-display flex items-center gap-2">
              {(() => { const Icon = steps[step].icon; return <Icon className="w-5 h-5 text-primary" />; })()}
              {steps[step].title}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {step === 0 && (
              <div className="space-y-3 mb-4">
                <GoogleButton label={t.au_google_signup} />
                <div className="relative">
                  <div className="absolute inset-0 flex items-center"><span className="w-full border-t border-border" /></div>
                  <div className="relative flex justify-center text-xs"><span className="bg-card px-2 text-muted-foreground">{t.au_or_email}</span></div>
                </div>
              </div>
            )}
            {error && <p className="text-sm text-destructive bg-destructive/10 p-2 rounded mb-4">{error}</p>}
            <AnimatePresence mode="wait">
              <motion.div key={step} initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} transition={{ duration: 0.2 }}>
                {step === 0 && (
                  <div className="space-y-4">
                    <div className="grid grid-cols-2 gap-4">
                      <div className="space-y-2"><Label>{t.au_first_name}</Label><Input placeholder="Jean" value={form.firstName} onChange={(e) => update("firstName", e.target.value)} /></div>
                      <div className="space-y-2"><Label>{t.au_last_name}</Label><Input placeholder="Nkoulou" value={form.lastName} onChange={(e) => update("lastName", e.target.value)} /></div>
                    </div>
                    <div className="space-y-2"><Label>{t.au_email}</Label><div className="relative"><Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" /><Input placeholder="jean@email.com" className="pl-10" value={form.email} onChange={(e) => update("email", e.target.value)} /></div></div>
                    <div className="space-y-2">
                      <Label>{t.au_phone}</Label>
                      <div className="relative">
                        <Phone className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <Input placeholder="+237 6XX XXX XXX" className="pl-10" value={form.phone} onChange={(e) => update("phone", e.target.value)} />
                      </div>
                      {form.phone && !validateCameroonPhone(form.phone) && (
                        <p className="text-[10px] text-destructive">Format invalide (Ex: 6XXXXXXXX)</p>
                      )}
                    </div>
                    <div className="space-y-2"><Label>{t.au_password}</Label><div className="relative"><Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" /><Input type={showPassword ? "text" : "password"} placeholder={t.au_pw_hint} className="pl-10 pr-10" value={form.password} onChange={(e) => update("password", e.target.value)} /><button type="button" onClick={() => setShowPassword(!showPassword)} className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground">{showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}</button></div></div>
                    <div className="space-y-2"><Label>{t.au_confirm_pw}</Label><Input type="password" placeholder="••••••••" value={form.confirmPassword} onChange={(e) => update("confirmPassword", e.target.value)} />{form.confirmPassword && form.password !== form.confirmPassword && <p className="text-xs text-destructive">{t.au_pw_mismatch}</p>}</div>
                  </div>
                )}
                {step === 1 && (
                  <div className="space-y-4">
                    <div className="space-y-2"><Label>{t.au_uni}</Label><Select value={form.university} onValueChange={(v) => update("university", v)}><SelectTrigger><SelectValue placeholder={t.au_level_select} /></SelectTrigger><SelectContent>{UNIVERSITIES.map((u) => <SelectItem key={u} value={u}>{u}</SelectItem>)}</SelectContent></Select></div>
                    <div className="space-y-2"><Label>{t.au_faculty}</Label><Input placeholder={t.au_faculty_eg} value={form.faculty} onChange={(e) => update("faculty", e.target.value)} /></div>
                    <div className="space-y-2"><Label>{t.au_level}</Label><Select value={form.level} onValueChange={(v) => update("level", v)}><SelectTrigger><SelectValue placeholder={t.au_level_select} /></SelectTrigger><SelectContent><SelectItem value="licence1">Licence 1</SelectItem><SelectItem value="licence2">Licence 2</SelectItem><SelectItem value="licence3">Licence 3</SelectItem><SelectItem value="master1">Master 1</SelectItem><SelectItem value="master2">Master 2</SelectItem><SelectItem value="doctorat">Doctorat</SelectItem></SelectContent></Select></div>
                    <div className="space-y-2"><Label>{t.au_bio}</Label><Textarea placeholder={t.au_bio_hint} value={form.bio} onChange={(e) => update("bio", e.target.value)} className="min-h-[80px]" /></div>
                    <div className="space-y-2"><Label>{t.au_skills}</Label><div className="flex flex-wrap gap-2">{SKILLS.map((skill) => <Badge key={skill} variant={selectedSkills.includes(skill) ? "default" : "outline"} className="cursor-pointer" onClick={() => toggleSkill(skill)}>{skill}</Badge>)}</div></div>
                  </div>
                )}
                {step === 2 && (
                  <div className="space-y-4">
                    <div className="p-4 rounded-lg bg-muted/50 border border-border"><p className="text-sm text-muted-foreground">{t.au_doc_warn}</p></div>
                    <div className="space-y-2"><Label>{t.au_doc_type}</Label><Select value={form.idType} onValueChange={(v) => update("idType", v)}><SelectTrigger><SelectValue placeholder={t.au_doc_select} /></SelectTrigger><SelectContent><SelectItem value="cni">CNI</SelectItem><SelectItem value="passport">Passeport</SelectItem><SelectItem value="permis">Permis</SelectItem></SelectContent></Select></div>
                    <div className="space-y-2">
                      <Label>{t.au_id_photo}</Label>
                      <div className="border-2 border-dashed border-border rounded-lg p-6 text-center hover:border-primary/50 transition-colors cursor-pointer" onClick={() => idFileRef.current?.click()}>
                        <Upload className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
                        <p className="text-sm text-muted-foreground">{form.idFile ? form.idFile.name : t.au_id_drop}</p>
                        <input ref={idFileRef} type="file" accept="image/*" className="hidden" onChange={(e) => update("idFile", e.target.files?.[0] || null)} />
                      </div>
                    </div>
                    <div className="space-y-2">
                      <Label>{t.au_selfie}</Label>
                      <div className="border-2 border-dashed border-border rounded-lg p-6 text-center hover:border-primary/50 transition-colors cursor-pointer" onClick={() => selfieRef.current?.click()}>
                        <Camera className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
                        <p className="text-sm text-muted-foreground">{form.selfieFile ? form.selfieFile.name : t.au_selfie_hint}</p>
                        <input ref={selfieRef} type="file" accept="image/*" capture="user" className="hidden" onChange={(e) => update("selfieFile", e.target.files?.[0] || null)} />
                      </div>
                    </div>
                    <div className="space-y-2">
                      <Label>{t.au_student_card}</Label>
                      <div className="border-2 border-dashed border-border rounded-lg p-6 text-center hover:border-primary/50 transition-colors cursor-pointer" onClick={() => cardRef.current?.click()}>
                        <GraduationCap className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
                        <p className="text-sm text-muted-foreground">{form.studentCardFile ? form.studentCardFile.name : t.au_student_card_hint}</p>
                        <input ref={cardRef} type="file" accept="image/*" className="hidden" onChange={(e) => update("studentCardFile", e.target.files?.[0] || null)} />
                      </div>
                    </div>
                    <div className="flex items-start gap-2 pt-2">
                      <Checkbox id="terms" checked={acceptTerms} onCheckedChange={(v) => setAcceptTerms(v === true)} />
                      <label htmlFor="terms" className="text-sm text-muted-foreground leading-tight">
                        {t.au_accept} <Link to="/conditions" className="text-primary hover:underline">{t.au_terms}</Link> {t.au_and} <Link to="/confidentialite" className="text-primary hover:underline">{t.au_privacy}</Link>
                      </label>
                    </div>
                  </div>
                )}
              </motion.div>
            </AnimatePresence>
            <div className="flex justify-between mt-8">
              <Button variant="outline" onClick={() => setStep(step - 1)} disabled={step === 0}><ChevronLeft className="w-4 h-4 mr-1" /> {t.c_previous}</Button>
              {step < 2 ? (
                <Button onClick={() => setStep(step + 1)} disabled={!canNext()} className="bg-gradient-hero hover:opacity-90">{t.c_next} <ChevronRight className="w-4 h-4 ml-1" /></Button>
              ) : (
                <Button onClick={handleSubmit} disabled={!canNext() || isLoading} className="bg-gradient-hero hover:opacity-90">{isLoading ? t.au_sending : t.c_submit} <Check className="w-4 h-4 ml-1" /></Button>
              )}
            </div>
          </CardContent>
        </Card>

        <p className="text-center text-sm text-muted-foreground mt-6">
          {t.au_have_account} <Link to="/connexion" className="text-primary font-medium hover:underline">{t.au_signin}</Link>
          {" · "}<Link to="/inscription/client" className="text-primary font-medium hover:underline">{t.au_or_client}</Link>
        </p>
      </div>
    </div>
  );
};

export default RegisterStudent;
