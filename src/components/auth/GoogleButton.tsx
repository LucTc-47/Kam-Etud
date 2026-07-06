import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { useLanguage } from "@/contexts/LanguageContext";

export function GoogleButton({ label }: { label?: string }) {
  const { toast } = useToast();
  const { t } = useLanguage();
  const finalLabel = label ?? t.au_continue_google;

  const handle = () => {
    toast({
      title: "Connexion Google indisponible",
      description: "Utilisez temporairement votre adresse e-mail. L'OAuth sera ajoute cote backend.",
    });
  };

  return (
    <Button type="button" variant="outline" className="w-full" onClick={handle}>
      <svg className="w-4 h-4" viewBox="0 0 48 48" aria-hidden="true">
        <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.4 29.3 35.5 24 35.5c-6.4 0-11.5-5.1-11.5-11.5S17.6 12.5 24 12.5c2.9 0 5.6 1.1 7.6 2.9l5.7-5.7C33.6 6.3 29 4.5 24 4.5 13.2 4.5 4.5 13.2 4.5 24S13.2 43.5 24 43.5c10.7 0 19.5-8.7 19.5-19.5 0-1.2-.1-2.3-.4-3.5z"/>
        <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 15.6 19 12.5 24 12.5c2.9 0 5.6 1.1 7.6 2.9l5.7-5.7C33.6 6.3 29 4.5 24 4.5c-7.4 0-13.8 4.2-17.7 10.2z"/>
        <path fill="#4CAF50" d="M24 43.5c5 0 9.5-1.7 13-4.7l-6-5.1c-2 1.4-4.4 2.3-7 2.3-5.3 0-9.7-3.1-11.3-7.5l-6.5 5C9.9 39.2 16.4 43.5 24 43.5z"/>
        <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.3 4-4.3 5.2l6 5.1c-.4.4 6.5-4.7 6.5-14.3 0-1.2-.1-2.3-.4-3.5z"/>
      </svg>
      {finalLabel}
    </Button>
  );
}
