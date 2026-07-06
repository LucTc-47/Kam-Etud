import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { ArrowRight } from "lucide-react";
import { motion } from "framer-motion";
import { useLanguage } from "@/contexts/LanguageContext";

const CTASection = () => {
  const { t } = useLanguage();
  return (
    <section className="py-20 md:py-28">
      <div className="container mx-auto px-4">
        <motion.div
          initial={{ opacity: 0, scale: 0.97 }}
          whileInView={{ opacity: 1, scale: 1 }}
          viewport={{ once: true }}
          className="relative overflow-hidden rounded-3xl bg-gradient-hero px-8 py-16 md:px-16 md:py-20 text-center"
        >
          <div className="absolute top-0 right-0 w-64 h-64 bg-primary-foreground/5 rounded-full blur-3xl" />
          <div className="absolute bottom-0 left-0 w-48 h-48 bg-secondary/10 rounded-full blur-3xl" />

          <div className="relative">
            <h2 className="font-display text-3xl md:text-5xl font-bold text-primary-foreground leading-tight">
              {t.cta_title1}<br />
              <span className="text-gradient-gold bg-clip-text text-transparent" style={{ backgroundImage: 'var(--gradient-gold)' }}>
                {t.cta_title2}
              </span>
            </h2>
            <p className="mt-6 text-primary-foreground/80 text-lg max-w-xl mx-auto">
              {t.cta_subtitle}
            </p>
            <div className="mt-10 flex flex-col sm:flex-row gap-4 justify-center">
              <Button size="lg" className="bg-gradient-gold text-foreground hover:opacity-90 text-lg px-8 h-13 font-semibold" asChild>
                <Link to="/inscription">
                  {t.cta_btn1} <ArrowRight className="ml-2 w-5 h-5" />
                </Link>
              </Button>
              <Button size="lg" variant="outline" className="text-primary-foreground border-primary-foreground/30 hover:bg-primary-foreground/10 text-lg px-8 h-13" asChild>
                <Link to="/comment-ca-marche">{t.cta_btn2}</Link>
              </Button>
            </div>
          </div>
        </motion.div>
      </div>
    </section>
  );
};

export default CTASection;
