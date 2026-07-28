package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.LinkType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LinkTypeRepository extends JpaRepository<LinkType, String> {

    List<LinkType> findAllByOrderByNameAsc();

}
