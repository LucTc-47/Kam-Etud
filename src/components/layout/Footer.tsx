import { Link } from "react-router-dom";
import { useLanguage } from "@/contexts/LanguageContext";
const logoFull ="/logoFull.png";

const Footer = () => {
  const { t } = useLanguage();
  return (
    <footer className="bg-foreground text-primary-foreground">
      <div className="container mx-auto px-4 py-16">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-10">
          <div className="md:col-span-1">
            <Link to="/" className="flex items-center gap-2">
                      <div className="flex items-center rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 transition-all">
                        <img
                          src={logoFull}
                          alt="Kam'Etud - Retour à l'accueil"
                          className="h-7 w-auto md:h-9 object-contain select-none"
                        />
                      </div>
                    </Link>
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
