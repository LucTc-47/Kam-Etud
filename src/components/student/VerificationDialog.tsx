import { useRef, useState } from "react";
import { Upload, Camera, GraduationCap, Check, Loader2 } from "lucide-react";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { useLanguage } from "@/contexts/LanguageContext";
import { useCreateVerification } from "@/hooks/useBackendData";
import { uploadApiFile } from "@/lib/api";
import { getErrorMessage } from "@/lib/utils";

const UNIVERSITIES = [
  "Université de Yaoundé I", "Université de Yaoundé II", "Université de Douala",
  "Université de Dschang", "Université de Buea", "Université de Bamenda",
  "Université de Maroua", "Université de Ngaoundéré", "Autre",
];

interface VerificationDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Universite deja connue du profil, pre-remplie si disponible. */
  defaultUniversity?: string;
}

/**
 * Depot d'un dossier de verification depuis l'espace etudiant.
 *
 * Reprend le meme parcours que l'etape 3 de l'inscription (televersement des
 * pieces puis POST /api/verifications), mais accessible apres coup : sans lui,
 * un etudiant dont le dossier a echoue ou a ete rejete restait bloque, faute
 * d'ecran pour en soumettre un nouveau.
 */
const VerificationDialog = ({ open, onOpenChange, defaultUniversity }: VerificationDialogProps) => {
  const { t } = useLanguage();
  const { toast } = useToast();
  const createVerification = useCreateVerification();

  const [university, setUniversity] = useState(defaultUniversity ?? "");
  const [idType, setIdType] = useState("");
  const [idFile, setIdFile] = useState<File | null>(null);
  const [selfieFile, setSelfieFile] = useState<File | null>(null);
  const [cardFile, setCardFile] = useState<File | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const idRef = useRef<HTMLInputElement>(null);
  const selfieRef = useRef<HTMLInputElement>(null);
  const cardRef = useRef<HTMLInputElement>(null);

  const canSubmit = !!university && !!idType && !!idFile && !!selfieFile && !isSubmitting;

  const handleSubmit = async () => {
    if (!canSubmit || !idFile || !selfieFile) return;
    setIsSubmitting(true);
    try {
      const idFileUrl = await uploadApiFile(idFile, "private");
      const selfieUrl = await uploadApiFile(selfieFile, "private");
      const studentCardUrl = cardFile ? await uploadApiFile(cardFile, "private") : "";

      await createVerification.mutateAsync({
        university,
        id_type: idType,
        id_file_url: idFileUrl,
        selfie_url: selfieUrl,
        student_card_url: studentCardUrl,
      });

      toast({ title: t.au_sent, description: t.au_sent_d });
      onOpenChange(false);
      setIdFile(null);
      setSelfieFile(null);
      setCardFile(null);
      setIdType("");
    } catch (e: unknown) {
      // L'echec est signale explicitement : pas de fausse confirmation.
      toast({ title: t.c_error, description: getErrorMessage(e), variant: "destructive" });
    } finally {
      setIsSubmitting(false);
    }
  };

  const dropZone = (
    file: File | null,
    onPick: (f: File | null) => void,
    ref: React.RefObject<HTMLInputElement>,
    Icon: typeof Upload,
    placeholder: string,
    capture?: "user",
  ) => (
    <div
      className="border-2 border-dashed border-border rounded-lg p-5 text-center hover:border-primary/50 transition-colors cursor-pointer"
      onClick={() => ref.current?.click()}
    >
      <Icon className="w-7 h-7 text-muted-foreground mx-auto mb-2" />
      <p className="text-sm text-muted-foreground break-all">{file ? file.name : placeholder}</p>
      <input
        ref={ref}
        type="file"
        accept="image/*"
        capture={capture}
        className="hidden"
        onChange={(e) => onPick(e.target.files?.[0] || null)}
      />
    </div>
  );

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!isSubmitting) onOpenChange(o); }}>
      <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="font-display flex items-center gap-2">
            <GraduationCap className="w-5 h-5 text-primary" /> {t.vd_title}
          </DialogTitle>
          <DialogDescription>{t.vd_subtitle}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4 pt-2">
          <div className="space-y-2">
            <Label>{t.au_uni}</Label>
            <Select value={university} onValueChange={setUniversity}>
              <SelectTrigger><SelectValue placeholder={t.au_level_select} /></SelectTrigger>
              <SelectContent>
                {UNIVERSITIES.map((u) => <SelectItem key={u} value={u}>{u}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label>{t.au_doc_type}</Label>
            <Select value={idType} onValueChange={setIdType}>
              <SelectTrigger><SelectValue placeholder={t.au_doc_select} /></SelectTrigger>
              <SelectContent>
                <SelectItem value="cni">CNI</SelectItem>
                <SelectItem value="passport">Passeport</SelectItem>
                <SelectItem value="permis">Permis</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label>{t.au_id_photo}</Label>
            {dropZone(idFile, setIdFile, idRef, Upload, t.au_id_drop)}
          </div>
          <div className="space-y-2">
            <Label>{t.au_selfie}</Label>
            {dropZone(selfieFile, setSelfieFile, selfieRef, Camera, t.au_selfie_hint, "user")}
          </div>
          <div className="space-y-2">
            <Label>{t.au_student_card} <span className="text-muted-foreground text-xs">({t.vd_optional})</span></Label>
            {dropZone(cardFile, setCardFile, cardRef, GraduationCap, t.au_student_card_hint)}
          </div>

          <Button
            className="w-full bg-gradient-hero hover:opacity-90"
            disabled={!canSubmit}
            onClick={handleSubmit}
          >
            {isSubmitting
              ? <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> {t.au_sending}</>
              : <><Check className="w-4 h-4 mr-2" /> {t.vd_submit}</>}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default VerificationDialog;
