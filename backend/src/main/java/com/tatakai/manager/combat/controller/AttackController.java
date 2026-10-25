package com.tatakai.manager.combat.controller;

import com.tatakai.manager.combat.dto.request.ChainAttackRequest;
import com.tatakai.manager.combat.dto.request.CreateAttackRequest;
import com.tatakai.manager.combat.dto.request.LateCounterRequest;
import com.tatakai.manager.combat.dto.request.ReactionRequest;
import com.tatakai.manager.combat.dto.response.AttackResponse;
import com.tatakai.manager.combat.entity.AttackStatus;
import com.tatakai.manager.combat.service.AttackService;
import com.tatakai.manager.shared.security.AuthenticatedUser;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class AttackController {

    private final AttackService attacks;

    public AttackController(AttackService attacks) {
        this.attacks = attacks;
    }

    @PostMapping("/encounters/{eid}/attacks")
    public ResponseEntity<AttackResponse> declare(@PathVariable UUID eid, @Valid @RequestBody CreateAttackRequest r) {
        return ResponseEntity.status(201).body(attacks.declare(eid, r, AuthenticatedUser.id()));
    }

    @GetMapping("/encounters/{eid}/attacks")
    public ResponseEntity<List<AttackResponse>> list(@PathVariable UUID eid,
                                                     @RequestParam(required = false) AttackStatus status) {
        return ResponseEntity.ok(attacks.list(eid, status, AuthenticatedUser.id()));
    }

    @PostMapping("/attacks/{aid}/targets/{tid}/reaction")
    public ResponseEntity<AttackResponse> respond(@PathVariable UUID aid, @PathVariable UUID tid,
                                                  @Valid @RequestBody ReactionRequest r) {
        return ResponseEntity.ok(attacks.respond(aid, tid, r.reaction(), r.defenderId(), r.techniqueId(), r.variation(), AuthenticatedUser.id()));
    }

    @PostMapping("/attacks/{aid}/targets/{tid}/late-counter")
    public ResponseEntity<AttackResponse> lateCounter(@PathVariable UUID aid, @PathVariable UUID tid,
                                                      @Valid @RequestBody com.tatakai.manager.combat.dto.request.LateCounterRequest r) {
        return ResponseEntity.ok(attacks.lateCounter(aid, tid, r, AuthenticatedUser.id()));
    }

    @PostMapping("/attacks/{aid}/chain")
    public ResponseEntity<AttackResponse> chain(@PathVariable UUID aid,
                                                @Valid @RequestBody com.tatakai.manager.combat.dto.request.ChainAttackRequest r) {
        return ResponseEntity.ok(attacks.chain(aid, r.techniqueId(), r.variation(), AuthenticatedUser.id()));
    }

    @PostMapping("/attacks/{aid}/cancel")
    public ResponseEntity<AttackResponse> cancel(@PathVariable UUID aid) {
        return ResponseEntity.ok(attacks.cancel(aid, AuthenticatedUser.id()));
    }
}
