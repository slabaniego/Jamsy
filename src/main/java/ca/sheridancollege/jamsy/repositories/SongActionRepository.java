package ca.sheridancollege.jamsy.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ca.sheridancollege.jamsy.models.SongAction;

@Repository
public interface SongActionRepository extends JpaRepository<SongAction, Long> {
	 List<SongAction> findByAction(String action);   
	 List<SongAction> findByActionNot(String action);  
}
