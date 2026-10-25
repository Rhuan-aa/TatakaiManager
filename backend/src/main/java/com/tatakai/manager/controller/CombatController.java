package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.AddConditionRequest;
import com.tatakai.manager.dto.request.AddParticipantRequest;
import com.tatakai.manager.dto.request.CombatActionRequest;
import com.tatakai.manager.dto.request.GrappleRequest;
import com.tatakai.manager.dto.request.CreateEncounterRequest;
import com.tatakai.manager.dto.request.SetHysteriaRequest;
import com.tatakai.manager.dto.request.UpdateEncounterRequest;
import com.tatakai.manager.dto.request.UpdateParticipantRequest;
import com.tatakai.manager.dto.request.VitalAdjustRequest;
import com.tatakai.manager.dto.response.CombatEventResponse;
import com.tatakai.manager.dto.response.EncounterResponse;
import com.tatakai.manager.dto.response.EncounterSummaryResponse;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.CombatService;
import com.tatakai.manager.service.ConditionCatalog;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class CombatController {

    private final CombatService combat;
    private final ConditionCatalog conditionCatalog;

    public CombatController(CombatService combat, ConditionCatalog conditionCatalog) {
        this.combat = combat;
        this.conditionCatalog = conditionCatalog;
    }

    @GetMapping("/catalog/conditions")
    public ResponseEntity<List<ConditionCatalog.Def>> conditions() {
        return ResponseEntity.ok(conditionCatalog.all());
    }

    @GetMapping("/campaigns/{cid}/encounters")
    public ResponseEntity<List<EncounterSummaryResponse>> list(@PathVariable UUID cid) {
        return ResponseEntity.ok(combat.list(cid, AuthenticatedUser.id()));
    }

    @PostMapping("/campaigns/{cid}/encounters")
    public ResponseEntity<EncounterResponse> create(@PathVariable UUID cid, @Valid @RequestBody CreateEncounterRequest r) {
        return ResponseEntity.status(201).body(combat.create(cid, r.name(), r.defaultClassification(), AuthenticatedUser.id()));
    }

    @GetMapping("/encounters/{eid}")
    public ResponseEntity<EncounterResponse> get(@PathVariable UUID eid) {
        return ResponseEntity.ok(combat.get(eid, AuthenticatedUser.id()));
    }

    @GetMapping("/encounters/{eid}/events")
    public ResponseEntity<List<CombatEventResponse>> events(@PathVariable UUID eid) {
        return ResponseEntity.ok(combat.events(eid, AuthenticatedUser.id()));
    }

    @PostMapping("/encounters/{eid}/participants")
    public ResponseEntity<EncounterResponse> addParticipant(@PathVariable UUID eid, @Valid @RequestBody AddParticipantRequest r) {
        return ResponseEntity.status(201).body(combat.addParticipant(eid, r.sheetId(), AuthenticatedUser.id()));
    }

    @DeleteMapping("/encounters/{eid}/participants/{pid}")
    public ResponseEntity<EncounterResponse> removeParticipant(@PathVariable UUID eid, @PathVariable UUID pid) {
        return ResponseEntity.ok(combat.removeParticipant(eid, pid, AuthenticatedUser.id()));
    }

    @PatchMapping("/encounters/{eid}/participants/{pid}")
    public ResponseEntity<EncounterResponse> update(@PathVariable UUID eid, @PathVariable UUID pid,
                                                    @Valid @RequestBody UpdateParticipantRequest r) {
        return ResponseEntity.ok(combat.updateParticipant(eid, pid, r, AuthenticatedUser.id()));
    }

    @PostMapping("/encounters/{eid}/participants/{pid}/reroll-initiative")
    public ResponseEntity<EncounterResponse> reroll(@PathVariable UUID eid, @PathVariable UUID pid) {
        return ResponseEntity.ok(combat.rerollInitiative(eid, pid, AuthenticatedUser.id()));
    }

    @PostMapping("/encounters/{eid}/start")
    public ResponseEntity<EncounterResponse> start(@PathVariable UUID eid) {
        return ResponseEntity.ok(combat.start(eid, AuthenticatedUser.id()));
    }

    @PostMapping("/encounters/{eid}/actions")
    public ResponseEntity<EncounterResponse> act(@PathVariable UUID eid, @Valid @RequestBody CombatActionRequest r) {
        return ResponseEntity.ok(combat.act(eid, r, AuthenticatedUser.id()));
    }

    @PostMapping("/encounters/{eid}/grapple")
    public ResponseEntity<EncounterResponse> grapple(@PathVariable UUID eid, @Valid @RequestBody GrappleRequest r) {
        return ResponseEntity.ok(combat.grapple(eid, r, AuthenticatedUser.id()));
    }

    @PostMapping("/encounters/{eid}/next-turn")
    public ResponseEntity<EncounterResponse> nextTurn(@PathVariable UUID eid) {
        return ResponseEntity.ok(combat.nextTurn(eid, AuthenticatedUser.id()));
    }

    @PostMapping("/encounters/{eid}/participants/{pid}/conditions")
    public ResponseEntity<EncounterResponse> addCondition(@PathVariable UUID eid, @PathVariable UUID pid,
                                                          @Valid @RequestBody AddConditionRequest r) {
        return ResponseEntity.status(201).body(combat.addCondition(eid, pid, r, AuthenticatedUser.id()));
    }

    @DeleteMapping("/encounters/{eid}/conditions/{condId}")
    public ResponseEntity<EncounterResponse> removeCondition(@PathVariable UUID eid, @PathVariable UUID condId) {
        return ResponseEntity.ok(combat.removeCondition(eid, condId, AuthenticatedUser.id()));
    }

    @PostMapping("/encounters/{eid}/end")
    public ResponseEntity<EncounterResponse> end(@PathVariable UUID eid) {
        return ResponseEntity.ok(combat.end(eid, AuthenticatedUser.id()));
    }

    @PatchMapping("/encounters/{eid}")
    public ResponseEntity<EncounterResponse> updateEncounter(@PathVariable UUID eid,
                                                             @Valid @RequestBody UpdateEncounterRequest r) {
        return ResponseEntity.ok(combat.updateEncounter(eid, r, AuthenticatedUser.id()));
    }

    @PatchMapping("/encounters/{eid}/participants/{pid}/hysteria")
    public ResponseEntity<EncounterResponse> hysteria(@PathVariable UUID eid, @PathVariable UUID pid,
                                                      @Valid @RequestBody SetHysteriaRequest r) {
        return ResponseEntity.ok(combat.setHysteria(eid, pid, r, AuthenticatedUser.id()));
    }

    @PatchMapping("/encounters/{eid}/counters/{cid}")
    public ResponseEntity<EncounterResponse> counter(@PathVariable UUID eid, @PathVariable UUID cid,
                                                     @Valid @RequestBody com.tatakai.manager.dto.request.UpdateCounterRequest r) {
        return ResponseEntity.ok(combat.patchCounter(eid, cid, r.value(), AuthenticatedUser.id()));
    }

    @PostMapping("/encounters/{eid}/participants/{pid}/vitals")
    public ResponseEntity<EncounterResponse> vitals(@PathVariable UUID eid, @PathVariable UUID pid,
                                                    @Valid @RequestBody VitalAdjustRequest r) {
        return ResponseEntity.ok(combat.adjustVital(eid, pid, r, AuthenticatedUser.id()));
    }
}
