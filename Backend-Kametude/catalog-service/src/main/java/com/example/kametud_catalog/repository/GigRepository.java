package com.example.kametud_catalog.repository;

import com.example.kametud_catalog.entity.Gig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface GigRepository extends JpaRepository<Gig, UUID> {

    @Query("""
            select g
            from Gig g
            where g.published = true
              and g.active = true
              and (:query = ''
                   or lower(g.title) like lower(concat('%', :query, '%'))
                   or lower(g.description) like lower(concat('%', :query, '%'))
                   or lower(g.category) like lower(concat('%', :query, '%')))
              and (:category = '' or lower(g.category) = lower(:category))
              and (:location = '' or lower(g.location) like lower(concat('%', :location, '%')))
            order by g.createdAt desc
            """)
    List<Gig> searchPublished(
            @Param("query") String query,
            @Param("category") String category,
            @Param("location") String location
    );

    /* Ancienne requete : les filtres absents etaient transmis avec null puis
       testes par `:param is null`. Avec Hibernate 7/PostgreSQL, ces parametres
       etaient lies en bytea et provoquaient `function lower(bytea) does not
       exist`. La requete ci-dessus emploie maintenant une chaine vide typee. */

    List<Gig> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    Optional<Gig> findByIdAndPublishedTrueAndActiveTrue(UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Gig g set g.active = false, g.published = false where g.studentId = :studentId and (g.active = true or g.published = true)")
    int deactivateAllByStudentId(@Param("studentId") UUID studentId);
}
