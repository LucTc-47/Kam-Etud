import { Link } from "react-router-dom";
import { useLanguage } from "@/contexts/LanguageContext";

const Footer = () => {
  const { t } = useLanguage();
  return (
    <footer className="bg-foreground text-primary-foreground">
      <div className="container mx-auto px-4 py-16">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-10">
          <div className="md:col-span-1">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-9 h-9 rounded-lg bg-gradient-gold flex items-center justify-center">
                <span className="font-display font-bold text-lg text-foreground">K</span>
              </div>
              <span className="font-display font-bold text-xl">Kam'Etud</span>
            </div>
            <p className="text-sm opacity-70 leading-relaxed">
              {t.ft_tagline}
            </p>
          </div>

          <div>
            <h4 className="font-display font-semibold mb-4">{t.ft_platform}</h4>
            <ul className="space-y-2 text-sm opacity-70">
              <li><Link to="/services" className="hover:opacity-100 transition-opacity">{t.services}</Link></li>
              <li><Link to="/comment-ca-marche" className="hover:opacity-100 transition-opacity">{t.howItWorks}</Link></li>
              <li><Link to="/inscription" className="hover:opacity-100 transition-opacity">{t.hero_cta2}</Link></li>
            </ul>
          </div>

          <div>
            <h4 className="font-display font-semibold mb-4">{t.ft_support}</h4>
            <ul className="space-y-2 text-sm opacity-70">
              <li><a href="#" className="hover:opacity-100 transition-opacity">{t.ft_help}</a></li>
              <li><a href="#" className="hover:opacity-100 transition-opacity">{t.ft_contact}</a></li>
              <li><Link to="/cgu" className="hover:opacity-100 transition-opacity">{t.ft_terms}</Link></li>
              <li><Link to="/confidentialite" className="hover:opacity-100 transition-opacity">{t.ft_privacy}</Link></li>
            </ul>
          </div>

          <div>
            <h4 className="font-display font-semibold mb-4">{t.ft_cities}</h4>
            <ul className="space-y-2 text-sm opacity-70">
              <li>Dschang</li>
              <li>Yaoundé</li>
              <li>Douala</li>
              <li>Bafoussam</li>
            </ul>
          </div>
        </div>

        <div className="border-t border-primary-foreground/10 mt-12 pt-8 flex flex-col md:flex-row justify-between items-center gap-4">
          <p className="text-sm opacity-50">{t.ft_copy}</p>
          <div className="flex gap-4 text-sm opacity-50">
            <Link to="/confidentialite" className="hover:opacity-100">{t.ft_privacy}</Link>
            <Link to="/cgu" className="hover:opacity-100">{t.ft_cgu}</Link>
          </div>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
