import Layout from "@/components/layout/Layout";
import { useLanguage } from "@/contexts/LanguageContext";

const Terms = () => {
  const { t, locale } = useLanguage();
  return (
    <Layout>
      <div className="container mx-auto px-4 py-12 max-w-3xl prose dark:prose-invert">
        <h1 className="text-3xl font-display font-bold mb-6">{t.tm_title}</h1>
        <p className="text-muted-foreground">{t.pr_updated} : {new Date().toLocaleDateString(locale)}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.tm_h1}</h2>
        <p>{t.tm_p1}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.tm_h2}</h2>
        <p>{t.tm_p2}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.tm_h3}</h2>
        <p>{t.tm_p3}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.tm_h4}</h2>
        <p>{t.tm_p4}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.tm_h5}</h2>
        <p>{t.tm_p5}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.tm_h6}</h2>
        <p>{t.tm_p6}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.tm_h7}</h2>
        <p>{t.tm_p7}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.tm_h8}</h2>
        <p>{t.tm_p8}</p>
      </div>
    </Layout>
  );
};

export default Terms;