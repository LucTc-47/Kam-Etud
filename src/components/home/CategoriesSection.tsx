import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { GraduationCap, Monitor, Home, Truck, Wrench, PartyPopper, Sparkles } from "lucide-react";
import { useLanguage } from "@/contexts/LanguageContext";

const CategoriesSection = () => {
  const { t } = useLanguage();
  const categories = [
    { icon: GraduationCap, routeKey: "Académique", title: t.cat_acad, description: t.cat_acad_d, color: "bg-primary/10 text-primary", count: 120 },
    { icon: Monitor, routeKey: "Numérique", title: t.cat_num, description: t.cat_num_d, color: "bg-secondary/20 text-secondary-foreground", count: 85 },
    { icon: Home, routeKey: "Aide à domicile", title: t.cat_home, description: t.cat_home_d, color: "bg-accent/10 text-accent", count: 64 },
    { icon: Truck, routeKey: "Livraison & Courses", title: t.cat_deliv, description: t.cat_deliv_d, color: "bg-primary/10 text-primary", count: 43 },
    { icon: Wrench, routeKey: "Bricolage", title: t.cat_bric, description: t.cat_bric_d, color: "bg-secondary/20 text-secondary-foreground", count: 31 },
    { icon: PartyPopper, routeKey: "Événementiel", title: t.cat_event, description: t.cat_event_d, color: "bg-accent/10 text-accent", count: 27 },
    { icon: Sparkles, routeKey: "Beauté & Bien-être", title: t.cat_beauty, description: t.cat_beauty_d, color: "bg-primary/10 text-primary", count: 38 },
  ];
  return (
    <section className="py-20 md:py-28">
      <div className="container mx-auto px-4">
        <div className="text-center mb-14">
          <h2 className="font-display text-3xl md:text-4xl font-bold text-foreground">
            {t.cats_title1} <span className="text-gradient-primary">{t.cats_title2}</span>
          </h2>
          <p className="mt-4 text-muted-foreground text-lg max-w-xl mx-auto">
            {t.cats_subtitle}
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
          {categories.map((cat, i) => (
            <motion.div
              key={cat.routeKey}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.07 }}
            >
              <Link
                to={`/services?categorie=${encodeURIComponent(cat.routeKey)}`}
                className="group block p-6 rounded-2xl bg-card border border-border shadow-card hover:shadow-elevated transition-all duration-300 hover:-translate-y-1"
              >
                <div className={`w-12 h-12 rounded-xl ${cat.color} flex items-center justify-center mb-4`}>
                  <cat.icon className="w-6 h-6" />
                </div>
                <h3 className="font-display font-semibold text-lg text-foreground group-hover:text-primary transition-colors">
                  {cat.title}
                </h3>
                <p className="mt-1 text-sm text-muted-foreground">{cat.description}</p>
                <p className="mt-3 text-xs font-medium text-primary">{cat.count} {t.cats_providers}</p>
              </Link>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
};

export default CategoriesSection;
