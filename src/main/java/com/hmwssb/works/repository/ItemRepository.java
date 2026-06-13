package com.hmwssb.works.repository;

import com.hmwssb.works.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<Item, Integer>, JpaSpecificationExecutor<Item> {

    /**
     * Case-insensitive full-text search on item_description.
     * Uses PostgreSQL ILIKE for partial matching anywhere in the string.
     *
     * Example: search("pipe", 10) returns first 10 items whose
     * description contains "pipe" (case-insensitive).
     */
    @Query(value = """
            SELECT * FROM public.itemlist
            WHERE item_description ILIKE CONCAT('%', :query, '%')
            ORDER BY slno
            """, nativeQuery = true)
    List<Item> searchByDescription(@Param("query") String query);
}
