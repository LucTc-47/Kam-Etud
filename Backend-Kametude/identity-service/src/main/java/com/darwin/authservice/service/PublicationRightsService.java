package com.darwin.authservice.service;

import com.darwin.authservice.client.CatalogClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Retrait du droit de publier d'un etudiant.
 *
 * Trois chemins retirent ce droit : le bannissement du compte, le rejet d'un
 * dossier de verification, et la mise a false du drapeau « verified » par un
 * administrateur. Avant, seul le bannissement retirait aussi du catalogue les
 * prestations deja en ligne ; les deux autres laissaient l'etudiant affiche et
 * commandable alors qu'il venait de perdre son droit de publier.
 *
 * Les trois passent desormais par ici, pour qu'un quatrieme chemin ajoute plus
 * tard ait un seul endroit evident ou brancher le retrait.
 */
@Service
@RequiredArgsConstructor
public class PublicationRightsService {

    private final CatalogClient catalogClient;

    /**
     * Desactive toutes les prestations de l'etudiant et renvoie leur nombre.
     *
     * A appeler avant d'enregistrer la perte du droit en base. Les methodes
     * appelantes sont transactionnelles : si Catalog Service est injoignable,
     * l'exception annule l'enregistrement. On ne peut donc jamais annoncer un
     * retrait de droit dont les prestations seraient restees visibles.
     */
    public int revokeFor(UUID studentId) {
        return catalogClient.deactivateStudentGigs(studentId);
    }
}
