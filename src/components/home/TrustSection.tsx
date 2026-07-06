import { motion } from "framer-motion";
import { Shield, Smartphone, Award, Clock } from "lucide-react";
import { useLanguage } from "@/contexts/LanguageContext";

const TrustSection = () => {
  const { t } = useLanguage();
  const features = [
    {
      icon: Shield,
      title: t.trust_f1_t,
      description: t.trust_f1_d,
    },
    {
      icon: Smartphone,
      title: t.trust_f2_t,
      description: t.trust_f2_d,
    },
    {
      icon: Award,
      title: t.trust_f3_t,
      description: t.trust_f3_d,
    },
    {
      icon: Clock,
      title: t.trust_f4_t,
      description: t.trust_f4_d,
    },
  ];
  return (
    <section className="py-20 md:py-28">
      <div className="container mx-auto px-4">
        <div className="text-center mb-14">
          <h2 className="font-display text-3xl md:text-4xl font-bold text-foreground">
            {t.trust_title1} <span className="text-gradient-primary">{t.trust_title2}</span> ?
          </h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 max-w-4xl mx-auto">
          {features.map((f, i) => (
            <motion.div
              key={f.title}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.1 }}
              className="flex gap-4 p-6 rounded-2xl bg-card border border-border shadow-card"
            >
              <div className="shrink-0 w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center">
                <f.icon className="w-6 h-6 text-primary" />
              </div>
              <div>
                <h3 className="font-display font-semibold text-foreground">{f.title}</h3>
                <p className="mt-1 text-sm text-muted-foreground leading-relaxed">{f.description}</p>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
};

export default TrustSection;
