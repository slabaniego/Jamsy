package ca.sheridancollege.jamsy.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ca.sheridancollege.jamsy.models.SongAction;

@Repository
public interface SongActionRepository extends JpaRepository<SongAction, Long> {

}
