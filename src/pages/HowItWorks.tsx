import Layout from "@/components/layout/Layout";
import HowItWorksSection from "@/components/home/HowItWorksSection";
import TrustSection from "@/components/home/TrustSection";
import CTASection from "@/components/home/CTASection";
import { motion } from "framer-motion";
import { Shield, Wallet, RefreshCw } from "lucide-react";
import { useLanguage } from "@/contexts/LanguageContext";

const HowItWorks = () => {
  const { t } = useLanguage();
  return (
    <Layout>
      <section className="py-10 md:py-16 bg-gradient-warm">
        <div className="container mx-auto px-4 text-center">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
            <h1 className="font-display text-3xl md:text-5xl font-bold text-foreground">
              {t.hw_title1} <span className="text-gradient-primary">Kam'Etud</span> ?
            </h1>
            <p className="mt-4 text-lg text-muted-foreground max-w-2xl mx-auto">
              {t.hw_subtitle}
            </p>
          </motion.div>
        </div>
      </section>

      <HowItWorksSection />

      {/* Escrow explanation */}
      <section className="py-20 bg-card">
        <div className="container mx-auto px-4">
          <div className="text-center mb-14">
            <h2 className="font-display text-3xl md:text-4xl font-bold text-foreground">
              {t.hw_escrow_t1} <span className="text-gradient-gold">{t.hw_escrow_t2}</span>
            </h2>
          </div>

          <div className="max-w-3xl mx-auto space-y-6">
            {[
              { icon: Wallet, title: t.hw_e1_t, desc: t.hw_e1_d },
              { icon: RefreshCw, title: t.hw_e2_t, desc: t.hw_e2_d },
              { icon: Shield, title: t.hw_e3_t, desc: t.hw_e3_d },
            ].map((item, i) => (
              <motion.div
                key={item.title}
                initial={{ opacity: 0, x: -20 }}
                whileInView={{ opacity: 1, x: 0 }}
                viewport={{ once: true }}
                transition={{ delay: i * 0.1 }}
                className="flex gap-5 p-6 rounded-2xl border border-border bg-background"
              >
                <div className="shrink-0 w-12 h-12 rounded-xl bg-gradient-hero flex items-center justify-center">
                  <item.icon className="w-6 h-6 text-primary-foreground" />
                </div>
                <div>
                  <h3 className="font-display font-semibold text-foreground">{item.title}</h3>
                  <p className="mt-1 text-sm text-muted-foreground">{item.desc}</p>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      <TrustSection />
      <CTASection />
    </Layout>
  );
};

export default HowItWorks;
