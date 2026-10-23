package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.*;
import com.tatakai.manager.dto.response.*;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class CharacterBuildController {

    private final CharacterBuildService build;

    public CharacterBuildController(CharacterBuildService build) {
        this.build = build;
    }

    @PostMapping("/campaigns/{campaignId}/character-sheets/build")
    public ResponseEntity<CharacterBuildResponse> start(@PathVariable UUID campaignId,
                                                        @Valid @RequestBody StartBuildRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(build.start(campaignId, request, AuthenticatedUser.id()));
    }

    @GetMapping("/character-sheets/{sheetId}/build")
    public ResponseEntity<CharacterBuildResponse> get(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(build.get(sheetId, AuthenticatedUser.id()));
    }

    @PutMapping("/character-sheets/{sheetId}/build/identity")
    public ResponseEntity<CharacterBuildResponse> setIdentity(@PathVariable UUID sheetId,
                                                              @Valid @RequestBody BuildIdentityRequest request) {
        return ResponseEntity.ok(build.setIdentity(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/build/traits/roll")
    public ResponseEntity<CharacterBuildResponse> rollTraits(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(build.rollTraits(sheetId, AuthenticatedUser.id()));
    }

    @PutMapping("/character-sheets/{sheetId}/build/traits/choice")
    public ResponseEntity<CharacterBuildResponse> chooseTraits(@PathVariable UUID sheetId,
                                                               @Valid @RequestBody TraitChoiceRequest request) {
        return ResponseEntity.ok(build.chooseTraits(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/build/traits/slots/{index}/resolve")
    public ResponseEntity<CharacterBuildResponse> resolveTraitSlot(@PathVariable UUID sheetId,
                                                                   @PathVariable int index,
                                                                   @Valid @RequestBody ResolveTraitSlotRequest request) {
        return ResponseEntity.ok(build.resolveTraitSlot(sheetId, index, request, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/build/blood-test/roll")
    public ResponseEntity<CharacterBuildResponse> rollBloodTest(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(build.rollBloodTest(sheetId, AuthenticatedUser.id()));
    }

    @PutMapping("/character-sheets/{sheetId}/build/clans")
    public ResponseEntity<CharacterBuildResponse> chooseClans(@PathVariable UUID sheetId,
                                                              @Valid @RequestBody ClanChoiceRequest request) {
        return ResponseEntity.ok(build.chooseClans(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/build/clans/roll")
    public ResponseEntity<CharacterBuildResponse> rollClans(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(build.rollClans(sheetId, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/build/attributes/roll")
    public ResponseEntity<CharacterBuildResponse> rollAttributes(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(build.rollAttributes(sheetId, AuthenticatedUser.id()));
    }

    @PutMapping("/character-sheets/{sheetId}/build/attributes/assignment")
    public ResponseEntity<CharacterBuildResponse> assignAttributes(@PathVariable UUID sheetId,
                                                                   @Valid @RequestBody AttributeAssignmentRequest request) {
        return ResponseEntity.ok(build.assignAttributes(sheetId, request, AuthenticatedUser.id()));
    }

    @PutMapping("/character-sheets/{sheetId}/build/free-points")
    public ResponseEntity<CharacterBuildResponse> setFreePoints(@PathVariable UUID sheetId,
                                                                @Valid @RequestBody FreePointsRequest request) {
        return ResponseEntity.ok(build.setFreePoints(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/build/steps/{step}/confirm")
    public ResponseEntity<CharacterBuildResponse> confirmStep(@PathVariable UUID sheetId, @PathVariable BuildStep step) {
        return ResponseEntity.ok(build.confirmStep(sheetId, step, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/build/back")
    public ResponseEntity<CharacterBuildResponse> back(@PathVariable UUID sheetId,
                                                       @Valid @RequestBody BuildBackRequest request) {
        return ResponseEntity.ok(build.back(sheetId, request, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/build/submit")
    public ResponseEntity<CharacterSheetResponse> submit(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(build.submit(sheetId, AuthenticatedUser.id()));
    }

    @PostMapping("/character-sheets/{sheetId}/build/reopen")
    public ResponseEntity<CharacterBuildResponse> reopen(@PathVariable UUID sheetId,
                                                         @Valid @RequestBody ReopenBuildRequest request) {
        return ResponseEntity.ok(build.reopen(sheetId, request, AuthenticatedUser.id()));
    }
}
