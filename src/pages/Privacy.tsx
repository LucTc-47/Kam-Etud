import Layout from "@/components/layout/Layout";
import { useLanguage } from "@/contexts/LanguageContext";

const Privacy = () => {
  const { t, locale } = useLanguage();
  return (
    <Layout>
      <div className="container mx-auto px-4 py-12 max-w-3xl prose dark:prose-invert">
        <h1 className="text-3xl font-display font-bold mb-6">{t.pr_title}</h1>
        <p className="text-muted-foreground">{t.pr_updated} : {new Date().toLocaleDateString(locale)}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.pr_h1}</h2>
        <p>{t.pr_p1}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.pr_h2}</h2>
        <p>{t.pr_p2}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.pr_h3}</h2>
        <p>{t.pr_p3}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.pr_h4}</h2>
        <p>{t.pr_p4}</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.pr_h5}</h2>
        <p>{t.pr_p5a} <a className="text-primary underline" href="mailto:contact@kam-etud.cm">contact@kam-etud.cm</a>.</p>

        <h2 className="mt-8 text-xl font-display font-semibold">{t.pr_h6}</h2>
        <p>{t.pr_p6}</p>
      </div>
    </Layout>
  );
};

export default Privacy;