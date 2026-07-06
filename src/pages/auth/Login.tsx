import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Eye, EyeOff, Mail, Lock, Phone } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/components/ui/input-otp";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/contexts/AuthContext";
import { GoogleButton } from "@/components/auth/GoogleButton";
import { useLanguage } from "@/contexts/LanguageContext";

const Login = () => {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [phone, setPhone] = useState("");
  const [otp, setOtp] = useState("");
  const [otpSent, setOtpSent] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const { toast } = useToast();
  const { login, loginWithPhone, sendPhoneOtp } = useAuth();
  const navigate = useNavigate();
  const { t } = useLanguage();

  const handleEmailLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError("");
    const result = await login(email, password);
    setIsLoading(false);
    if (result.success) {
      toast({ title: t.au_login_ok, description: t.au_login_welcome });
      navigate("/");
    } else {
      setError(result.error || t.au_err_creds);
    }
  };

  const handleSendOtp = async () => {
    if (!phone.trim()) return;
    setIsLoading(true);
    const success = await sendPhoneOtp(phone);
    setIsLoading(false);
    if (success) {
      setOtpSent(true);
      toast({ title: t.au_otp_sent, description: `${t.au_otp_sent_desc} ${phone}` });
    } else {
      toast({ title: t.c_error, description: t.au_otp_err, variant: "destructive" });
    }
  };

  const handlePhoneLogin = async () => {
    if (otp.length < 6) return;
    setIsLoading(true);
    const success = await loginWithPhone(phone, otp);
    setIsLoading(false);
    if (success) {
      toast({ title: t.au_login_ok, description: t.au_login_welcome });
      navigate("/");
    } else {
      setError(t.au_err_otp);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-warm px-4">
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-md">
        <div className="text-center mb-8">
          <Link to="/" className="inline-flex items-center gap-2 mb-6">
            <div className="w-10 h-10 rounded-lg bg-gradient-hero flex items-center justify-center">
              <span className="text-primary-foreground font-display font-bold text-xl">K</span>
            </div>
            <span className="font-display font-bold text-2xl text-foreground">
              Kam'<span className="text-gradient-primary">Etud</span>
            </span>
          </Link>
          <h1 className="text-2xl font-display font-bold text-foreground">{t.login}</h1>
          <p className="text-muted-foreground mt-1">{t.au_welcome_subtitle}</p>
        </div>

        <Card className="shadow-elevated border-border/50">
          <CardContent className="pt-6">
            <div className="space-y-3 mb-4">
              <GoogleButton label={t.au_google_signin} />
              <div className="relative">
                <div className="absolute inset-0 flex items-center"><span className="w-full border-t border-border" /></div>
                <div className="relative flex justify-center text-xs"><span className="bg-card px-2 text-muted-foreground">{t.au_or}</span></div>
              </div>
            </div>
            <Tabs defaultValue="email">
              <TabsList className="w-full mb-4">
                <TabsTrigger value="email" className="flex-1"><Mail className="w-4 h-4 mr-1" /> {t.au_email}</TabsTrigger>
                <TabsTrigger value="phone" className="flex-1"><Phone className="w-4 h-4 mr-1" /> {t.au_phone}</TabsTrigger>
              </TabsList>

              <TabsContent value="email">
                <form onSubmit={handleEmailLogin} className="space-y-4">
                  {error && <p className="text-sm text-destructive bg-destructive/10 p-2 rounded">{error}</p>}
                  <div className="space-y-2">
                    <Label htmlFor="email">{t.au_email}</Label>
                    <div className="relative">
                      <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                      <Input id="email" type="email" placeholder="votre@email.com" className="pl-10" value={email} onChange={(e) => setEmail(e.target.value)} required />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <div className="flex justify-between">
                      <Label htmlFor="password">{t.au_password}</Label>
                      <Link to="/mot-de-passe-oublie" className="text-xs text-primary hover:underline">{t.au_forgot}</Link>
                    </div>
                    <div className="relative">
                      <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                      <Input id="password" type={showPassword ? "text" : "password"} placeholder="••••••••" className="pl-10 pr-10" value={password} onChange={(e) => setPassword(e.target.value)} required />
                      <button type="button" onClick={() => setShowPassword(!showPassword)} className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                        {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                      </button>
                    </div>
                  </div>
                  <Button type="submit" className="w-full bg-gradient-hero hover:opacity-90" disabled={isLoading}>
                    {isLoading ? t.au_logging : t.au_login}
                  </Button>
                </form>
              </TabsContent>

              <TabsContent value="phone">
                <div className="space-y-4">
                  <div className="space-y-2">
                    <Label>{t.au_phone_number}</Label>
                    <div className="relative">
                      <Phone className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                      <Input placeholder="+237 6XX XXX XXX" className="pl-10" value={phone} onChange={(e) => setPhone(e.target.value)} />
                    </div>
                  </div>
                  {!otpSent ? (
                    <Button className="w-full bg-gradient-hero hover:opacity-90" onClick={handleSendOtp} disabled={!phone.trim() || isLoading}>
                      {isLoading ? t.au_sending : t.au_send_otp}
                    </Button>
                  ) : (
                    <>
                      <div className="space-y-2">
                        <Label>{t.au_otp_label}</Label>
                        <div className="flex justify-center">
                          <InputOTP maxLength={6} value={otp} onChange={setOtp}>
                            <InputOTPGroup>
                              <InputOTPSlot index={0} /><InputOTPSlot index={1} /><InputOTPSlot index={2} />
                              <InputOTPSlot index={3} /><InputOTPSlot index={4} /><InputOTPSlot index={5} />
                            </InputOTPGroup>
                          </InputOTP>
                        </div>
                      </div>
                      <Button className="w-full bg-gradient-hero hover:opacity-90" onClick={handlePhoneLogin} disabled={isLoading || otp.length < 6}>
                        {isLoading ? t.au_verifying : t.au_verify}
                      </Button>
                      <Button variant="ghost" size="sm" className="w-full" onClick={() => { setOtpSent(false); setOtp(""); }}>
                        {t.au_resend}
                      </Button>
                    </>
                  )}
                </div>
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>

        <p className="text-center text-sm text-muted-foreground mt-6">
          {t.au_no_account}{" "}
          <Link to="/inscription" className="text-primary font-medium hover:underline">{t.au_signup}</Link>
        </p>
      </motion.div>
    </div>
  );
};

export default Login;
