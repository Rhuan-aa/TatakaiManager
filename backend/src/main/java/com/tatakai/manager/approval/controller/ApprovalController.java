package com.tatakai.manager.approval.controller;

import com.tatakai.manager.approval.dto.response.ApprovalItemResponse;
import com.tatakai.manager.approval.service.ApprovalService;
import com.tatakai.manager.shared.security.AuthenticatedUser;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/campaigns/{campaignId}/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public ResponseEntity<List<ApprovalItemResponse>> list(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(approvalService.list(campaignId, AuthenticatedUser.id()));
    }
}
