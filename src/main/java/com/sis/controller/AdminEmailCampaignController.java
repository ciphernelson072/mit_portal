package com.sis.controller;

import com.sis.dto.AdminEmailRecipientDto;
import com.sis.dto.BulkEmailRequest;
import com.sis.dto.BulkEmailResponse;
import com.sis.service.AdminEmailCampaignService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/email")
@PreAuthorize("@permissionSecurity.canManageEmailCampaigns()")
public class AdminEmailCampaignController {
    private final AdminEmailCampaignService campaignService;

    public AdminEmailCampaignController(AdminEmailCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping("/recipients")
    public ResponseEntity<List<AdminEmailRecipientDto>> listRecipients() {
        return ResponseEntity.ok(campaignService.listRecipients());
    }

    @PostMapping("/campaign")
    public ResponseEntity<BulkEmailResponse> sendCampaign(@Valid @RequestBody BulkEmailRequest request) {
        return ResponseEntity.ok(campaignService.sendCampaign(request));
    }
}
