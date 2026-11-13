package com.sis.service;

import com.sis.dto.*;
import com.sis.entity.*;
import com.sis.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ProcurementService {
    private final SupplierRepository suppliers;
    private final ProcurementRequestRepository requests;
    private final AssetRepository assets;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;

    public ProcurementService(SupplierRepository suppliers, ProcurementRequestRepository requests, AssetRepository assets,
                              UserRepository users, AuditLogRepository auditLogs) {
        this.suppliers = suppliers;
        this.requests = requests;
        this.assets = assets;
        this.users = users;
        this.auditLogs = auditLogs;
    }

    public Supplier createSupplier(CreateSupplierRequest request, String createdBy) {
        Supplier supplier = new Supplier();
        supplier.setSupplierCode(request.getSupplierCode());
        supplier.setName(request.getName());
        supplier.setEmail(request.getEmail());
        supplier.setPhone(request.getPhone());
        Supplier saved = suppliers.save(supplier);
        audit(createdBy, "PROCUREMENT_SUPPLIER_CREATED",
                "Supplier #" + saved.getId() + " code " + saved.getSupplierCode() + " name " + saved.getName());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Supplier> suppliers() { return suppliers.findByActiveTrueOrderByNameAsc(); }

    public ProcurementRequest createRequest(CreateProcurementRequest request, String username) {
        ProcurementRequest item = new ProcurementRequest();
        item.setRequesterId(users.findByUsernameNormalized(username).orElseThrow(() -> new IllegalArgumentException("Requester account not found")).getId());
        item.setDescription(request.getDescription());
        item.setEstimatedAmount(request.getEstimatedAmount());
        if (request.getSupplierId() != null) item.setSupplier(suppliers.findById(request.getSupplierId()).orElseThrow(() -> new IllegalArgumentException("Supplier not found")));
        ProcurementRequest saved = requests.save(item);
        audit(username, "PROCUREMENT_REQUEST_CREATED", "Request #" + saved.getId() + " amount "
            + saved.getEstimatedAmount() + " description " + saved.getDescription());
        return requests.findByIdWithSupplier(saved.getId())
                .orElseThrow(() -> new IllegalStateException("Saved procurement request could not be reloaded"));
    }

    @Transactional(readOnly = true)
    public List<ProcurementRequest> pendingRequests() { return requests.findByStatusOrderByCreatedAtAsc(ProcurementRequestStatus.SUBMITTED); }

    public ProcurementRequest review(Long id, ReviewProcurementRequest request, String reviewer) {
        if (request.getStatus() != ProcurementRequestStatus.APPROVED && request.getStatus() != ProcurementRequestStatus.REJECTED) throw new IllegalArgumentException("Review must be APPROVED or REJECTED");
        ProcurementRequest item = requests.findByIdWithSupplier(id)
                .orElseThrow(() -> new IllegalArgumentException("Procurement request not found"));
        if (item.getStatus() != ProcurementRequestStatus.SUBMITTED) throw new IllegalStateException("Procurement request is already finalized");
        item.setStatus(request.getStatus());
        item.setReviewedBy(reviewer);
        item.setReviewedAt(LocalDateTime.now());
        ProcurementRequest saved = requests.save(item);
        Long reviewerId = users.findByUsernameNormalized(reviewer)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + reviewer))
            .getId();
        auditLogs.save(new AuditLog(reviewerId, "PROCUREMENT_REQUEST_REVIEWED",
            "Procurement request #" + saved.getId() + " " + saved.getStatus()
                + " at estimated amount " + saved.getEstimatedAmount()));
        return saved;
    }

    public Asset createAsset(CreateAssetRequest request, String createdBy) {
        Asset asset = new Asset();
        asset.setAssetTag(request.getAssetTag());
        asset.setName(request.getName());
        asset.setDescription(request.getDescription());
        asset.setPurchaseValue(request.getPurchaseValue());
        asset.setPurchaseDate(request.getPurchaseDate());
        asset.setAssignedToUserId(request.getAssignedToUserId());
        Asset saved = assets.save(asset);
        audit(createdBy, "PROCUREMENT_ASSET_CREATED", "Asset #" + saved.getId() + " tag "
            + saved.getAssetTag() + " name " + saved.getName());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Asset> assets() { return assets.findByActiveTrueOrderByNameAsc(); }

    private void audit(String username, String action, String details) {
        Long userId = users.findByUsernameNormalized(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username))
                .getId();
        auditLogs.save(new AuditLog(userId, action, details));
    }
}