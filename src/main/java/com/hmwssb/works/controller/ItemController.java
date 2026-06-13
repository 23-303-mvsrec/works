package com.hmwssb.works.controller;

import com.hmwssb.works.model.Item;
import com.hmwssb.works.repository.ItemRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
// import java.util.Map;

/**
 * REST controller for item master data.
 *
 * Endpoint:
 * GET /api/items/search?q={query}&limit={limit}
 *
 * @CrossOrigin allows the AngularJS frontend (served on a different
 *              port during development) to call this API.
 *              In production, lock origins down to your actual domain.
 */
@RestController
@RequestMapping("/api/items")
@CrossOrigin(origins = "*") // TODO: restrict to your domain in production
public class ItemController {

    private final ItemRepository itemRepository;

    public ItemController(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Search items by description using Excel-style substring matching.
     *
     * The query is matched anywhere inside item_description, case-insensitively.
     * This behaves like Excel's Find/Search feature when searching cell text.
     *
     * @param q     Search query string
     * @param limit Maximum number of results to return (default 10)
     * @return JSON array of matching Item objects
     *
     *         Example response:
     *         [
     *         {
     *         "slno": 1,
     *         "itemDescription": "Lowering C.I. / D.I. Pipes...",
     *         "unit": "Meter",
     *         "rate": 53.5
     *         },
     *         ...
     *         ]
     */
    @GetMapping("/search")
    public ResponseEntity<List<Item>> search(
            @RequestParam(name = "q", defaultValue = "") String q) {

        String query = q.strip();
        if (query.isBlank()) {
            return ResponseEntity.ok(List.of());
        }

        String[] words = query.split("\\s+");
        Specification<Item> spec = (root, query1, cb) -> cb.conjunction();
        for (String word : words) {
            if (!word.isBlank()) {
                final String lowerWord = "%" + word.toLowerCase() + "%";
                spec = spec.and((root, query1, criteriaBuilder) -> criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("itemDescription")),
                        lowerWord));
            }
        }

        List<Item> results = itemRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "slno"));
        return ResponseEntity.ok(results);
    }
}
