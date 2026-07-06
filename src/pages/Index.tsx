import Layout from "@/components/layout/Layout";
import HeroSection from "@/components/home/HeroSection";
import CategoriesSection from "@/components/home/CategoriesSection";
import HowItWorksSection from "@/components/home/HowItWorksSection";
import TrustSection from "@/components/home/TrustSection";
import CTASection from "@/components/home/CTASection";
import SEO from "@/components/seo/SEO";

const Index = () => {
  return (
    <Layout>
      <SEO />
      <HeroSection />
      <CategoriesSection />
      <HowItWorksSection />
      <TrustSection />
      <CTASection />
    </Layout>
  );
};

export default Index;
