package com.hmwssb.works.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmwssb.works.model.Estimate;
import com.hmwssb.works.model.EstimateItem;
import com.hmwssb.works.model.EstimateRevision;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EstimateDiffService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateChangeSummary(Estimate oldEst, Estimate newEst) {
        if (oldEst == null && newEst == null) return "No changes";
        if (oldEst == null) return "Initial draft created";

        double oldTotal = oldEst.getGrandTotal() != null ? oldEst.getGrandTotal() : 0.0;
        double newTotal = newEst.getGrandTotal() != null ? newEst.getGrandTotal() : 0.0;
        double delta = newTotal - oldTotal;

        List<EstimateItem> oldItems = oldEst.getItems() != null ? oldEst.getItems() : Collections.emptyList();
        List<EstimateItem> newItems = newEst.getItems() != null ? newEst.getItems() : Collections.emptyList();

        int added = 0;
        int removed = 0;
        int modified = 0;

        int maxLen = Math.max(oldItems.size(), newItems.size());
        for (int i = 0; i < maxLen; i++) {
            if (i >= oldItems.size()) {
                added++;
            } else if (i >= newItems.size()) {
                removed++;
            } else {
                EstimateItem o = oldItems.get(i);
                EstimateItem n = newItems.get(i);
                boolean changed = !Objects.equals(o.getDescription(), n.getDescription()) ||
                                  !Objects.equals(o.getQuantity(), n.getQuantity()) ||
                                  !Objects.equals(o.getRate(), n.getRate()) ||
                                  !Objects.equals(o.getAmount(), n.getAmount()) ||
                                  !Objects.equals(o.getLength(), n.getLength()) ||
                                  !Objects.equals(o.getBreadth(), n.getBreadth()) ||
                                  !Objects.equals(o.getDepth(), n.getDepth());
                if (changed) modified++;
            }
        }

        StringBuilder sb = new StringBuilder();
        if (Math.abs(delta) > 0.001) {
            sb.append(delta > 0 ? "+₹" : "-₹")
              .append(String.format(Locale.US, "%,.2f", Math.abs(delta)))
              .append(" (Total: ₹")
              .append(String.format(Locale.US, "%,.2f", newTotal))
              .append(")");
        } else {
            sb.append("Total unchanged (₹")
              .append(String.format(Locale.US, "%,.2f", newTotal))
              .append(")");
        }

        List<String> changeDetails = new ArrayList<>();
        if (added > 0) changeDetails.add(added + " item" + (added > 1 ? "s" : "") + " added");
        if (removed > 0) changeDetails.add(removed + " item" + (removed > 1 ? "s" : "") + " removed");
        if (modified > 0) changeDetails.add(modified + " item" + (modified > 1 ? "s" : "") + " modified");
        if (!Objects.equals(oldEst.getNameOfWork(), newEst.getNameOfWork())) changeDetails.add("Work title updated");
        if (!Objects.equals(oldEst.getGstPercent(), newEst.getGstPercent())) changeDetails.add("GST adjusted");
        if (!Objects.equals(oldEst.getUnforeseenAmount(), newEst.getUnforeseenAmount())) changeDetails.add("Unforeseen amount adjusted");

        if (!changeDetails.isEmpty()) {
            sb.append(" — ").append(String.join(", ", changeDetails));
        }

        return sb.toString();
    }

    public Map<String, Object> computeDetailedDiff(EstimateRevision revA, EstimateRevision revB) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (revA == null || revB == null) return result;

        try {
            JsonNode rootA = objectMapper.readTree(revA.getSnapshotJson());
            JsonNode rootB = objectMapper.readTree(revB.getSnapshotJson());

            Map<String, Object> metaA = new LinkedHashMap<>();
            metaA.put("revisionNumber", revA.getRevisionNumber());
            metaA.put("revisionType", revA.getRevisionType());
            metaA.put("status", revA.getStatusAtRevision());
            metaA.put("officerName", revA.getOfficerName());
            metaA.put("officerRole", revA.getOfficerRole());
            metaA.put("officerDesignation", revA.getOfficerDesignation());
            metaA.put("grandTotal", revA.getGrandTotal());
            metaA.put("createdAt", revA.getCreatedAt().toString());
            metaA.put("remarks", revA.getRemarks());

            Map<String, Object> metaB = new LinkedHashMap<>();
            metaB.put("revisionNumber", revB.getRevisionNumber());
            metaB.put("revisionType", revB.getRevisionType());
            metaB.put("status", revB.getStatusAtRevision());
            metaB.put("officerName", revB.getOfficerName());
            metaB.put("officerRole", revB.getOfficerRole());
            metaB.put("officerDesignation", revB.getOfficerDesignation());
            metaB.put("grandTotal", revB.getGrandTotal());
            metaB.put("createdAt", revB.getCreatedAt().toString());
            metaB.put("remarks", revB.getRemarks());

            result.put("revisionA", metaA);
            result.put("revisionB", metaB);

            double totalA = revA.getGrandTotal() != null ? revA.getGrandTotal() : 0.0;
            double totalB = revB.getGrandTotal() != null ? revB.getGrandTotal() : 0.0;
            double diff = totalB - totalA;
            double pct = totalA > 0 ? (diff / totalA) * 100.0 : 0.0;

            Map<String, Object> financialDelta = new LinkedHashMap<>();
            financialDelta.put("totalA", totalA);
            financialDelta.put("totalB", totalB);
            financialDelta.put("difference", diff);
            financialDelta.put("percentChange", Math.round(pct * 100.0) / 100.0);
            financialDelta.put("gstPercentA", revA.getGstPercent());
            financialDelta.put("gstPercentB", revB.getGstPercent());
            financialDelta.put("unforeseenA", revA.getUnforeseenAmount());
            financialDelta.put("unforeseenB", revB.getUnforeseenAmount());
            result.put("financialDelta", financialDelta);

            // Item comparison
            List<Map<String, Object>> itemDiffs = new ArrayList<>();
            JsonNode itemsNodeA = rootA.get("items");
            JsonNode itemsNodeB = rootB.get("items");

            List<JsonNode> itemsA = new ArrayList<>();
            if (itemsNodeA != null && itemsNodeA.isArray()) itemsNodeA.forEach(itemsA::add);

            List<JsonNode> itemsB = new ArrayList<>();
            if (itemsNodeB != null && itemsNodeB.isArray()) itemsNodeB.forEach(itemsB::add);

            int max = Math.max(itemsA.size(), itemsB.size());
            for (int i = 0; i < max; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                if (i >= itemsA.size()) {
                    JsonNode b = itemsB.get(i);
                    row.put("changeType", "ADDED");
                    row.put("sno", b.path("sno").asInt(i + 1));
                    row.put("isMaterial", b.path("isMaterial").asText("Yes"));
                    row.put("description", b.path("description").asText());
                    row.put("oldQuantity", null);
                    row.put("newQuantity", b.path("quantity").asDouble(0.0));
                    row.put("oldRate", null);
                    row.put("newRate", b.path("rate").asDouble(0.0));
                    row.put("unit", b.path("unit").asText(""));
                    row.put("oldAmount", null);
                    row.put("newAmount", b.path("amount").asDouble(0.0));
                    row.put("amountDelta", b.path("amount").asDouble(0.0));
                } else if (i >= itemsB.size()) {
                    JsonNode a = itemsA.get(i);
                    row.put("changeType", "REMOVED");
                    row.put("sno", a.path("sno").asInt(i + 1));
                    row.put("isMaterial", a.path("isMaterial").asText("Yes"));
                    row.put("description", a.path("description").asText());
                    row.put("oldQuantity", a.path("quantity").asDouble(0.0));
                    row.put("newQuantity", null);
                    row.put("oldRate", a.path("rate").asDouble(0.0));
                    row.put("newRate", null);
                    row.put("unit", a.path("unit").asText(""));
                    row.put("oldAmount", a.path("amount").asDouble(0.0));
                    row.put("newAmount", null);
                    row.put("amountDelta", -a.path("amount").asDouble(0.0));
                } else {
                    JsonNode a = itemsA.get(i);
                    JsonNode b = itemsB.get(i);

                    double amtA = a.path("amount").asDouble(0.0);
                    double amtB = b.path("amount").asDouble(0.0);
                    double qtyA = a.path("quantity").asDouble(0.0);
                    double qtyB = b.path("quantity").asDouble(0.0);
                    double rateA = a.path("rate").asDouble(0.0);
                    double rateB = b.path("rate").asDouble(0.0);
                    String descA = a.path("description").asText("");
                    String descB = b.path("description").asText("");

                    boolean isMod = !descA.equals(descB) || Math.abs(qtyA - qtyB) > 0.001 ||
                                    Math.abs(rateA - rateB) > 0.001 || Math.abs(amtA - amtB) > 0.001;

                    row.put("changeType", isMod ? "MODIFIED" : "UNCHANGED");
                    row.put("sno", b.path("sno").asInt(i + 1));
                    row.put("isMaterial", b.path("isMaterial").asText("Yes"));
                    row.put("description", descB.isEmpty() ? descA : descB);
                    row.put("oldDescription", descA);
                    row.put("newDescription", descB);
                    row.put("oldQuantity", qtyA);
                    row.put("newQuantity", qtyB);
                    row.put("quantityDelta", qtyB - qtyA);
                    row.put("oldRate", rateA);
                    row.put("newRate", rateB);
                    row.put("rateDelta", rateB - rateA);
                    row.put("unit", b.path("unit").asText(""));
                    row.put("oldAmount", amtA);
                    row.put("newAmount", amtB);
                    row.put("amountDelta", amtB - amtA);
                }
                itemDiffs.add(row);
            }
            result.put("itemDiffs", itemDiffs);

        } catch (Exception e) {
            result.put("error", "Failed to compute diff: " + e.getMessage());
        }

        return result;
    }
}
