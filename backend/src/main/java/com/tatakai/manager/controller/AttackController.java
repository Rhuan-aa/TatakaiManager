package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.CreateAttackRequest;
import com.tatakai.manager.dto.request.ReactionRequest;
import com.tatakai.manager.dto.response.AttackResponse;
import com.tatakai.manager.entity.AttackStatus;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.AttackService;
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
                                                      @Valid @RequestBody com.tatakai.manager.dto.request.LateCounterRequest r) {
        return ResponseEntity.ok(attacks.lateCounter(aid, tid, r, AuthenticatedUser.id()));
    }

    @PostMapping("/attacks/{aid}/cancel")
    public ResponseEntity<AttackResponse> cancel(@PathVariable UUID aid) {
        return ResponseEntity.ok(attacks.cancel(aid, AuthenticatedUser.id()));
    }
}
