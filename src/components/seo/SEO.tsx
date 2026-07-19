import { useEffect } from 'react';
import { useLanguage } from '@/contexts/LanguageContext';

interface SEOProps {
  title?: string;
  description?: string;
  keywords?: string;
}

const SEO = ({ title, description, keywords }: SEOProps) => {
  const { locale } = useLanguage();

  useEffect(() => {
    const baseTitle = "Kam'Etud - Plateforme Freelance Étudiante Cameroun";
    document.title = title ? `${title} | Kam'Etud` : baseTitle;

    // Meta description
    let metaDesc = document.querySelector('meta[name="description"]');
    if (!metaDesc) {
      metaDesc = document.createElement('meta');
      metaDesc.setAttribute('name', 'description');
      document.head.appendChild(metaDesc);
    }
    metaDesc.setAttribute('content', description || "Trouvez des services académiques, numériques et d'aide à domicile réalisés par les meilleurs étudiants du Cameroun.");

    // Meta keywords
    let metaKeywords = document.querySelector('meta[name="keywords"]');
    if (!metaKeywords) {
      metaKeywords = document.createElement('meta');
      metaKeywords.setAttribute('name', 'keywords');
      document.head.appendChild(metaKeywords);
    }
    metaKeywords.setAttribute('content', keywords || "freelance, étudiant, cameroun, dschang, yaoundé, douala, service académique, aide à domicile");

    // Open Graph
    const setOG = (property: string, content: string) => {
      let og = document.querySelector(`meta[property="${property}"]`);
      if (!og) {
        og = document.createElement('meta');
        og.setAttribute('property', property);
        document.head.appendChild(og);
      }
      og.setAttribute('content', content);
    };

    setOG('og:title', title || baseTitle);
    setOG('og:description', description || "Connectez-vous avec les étudiants talentueux pour vos besoins quotidiens.");
    setOG('og:locale', locale === 'fr' ? 'fr_FR' : 'en_US');

  }, [title, description, keywords, locale]);

  return null;
};

export default SEO;
