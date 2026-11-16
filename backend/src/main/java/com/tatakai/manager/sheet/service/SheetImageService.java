package com.tatakai.manager.sheet.service;

import com.tatakai.manager.campaign.entity.CampaignMember;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.npc.exception.InvalidImageException;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.SheetImage;
import com.tatakai.manager.sheet.entity.SheetImageKind;
import com.tatakai.manager.sheet.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.sheet.repository.CharacterSheetRepository;
import com.tatakai.manager.sheet.repository.SheetImageRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Retrato e imagem de corpo inteiro da ficha. */
@Service
public class SheetImageService {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private final SheetImageRepository imageRepository;
    private final CharacterSheetRepository sheetRepository;
    private final CampaignMemberRepository memberRepository;

    public SheetImageService(SheetImageRepository imageRepository, CharacterSheetRepository sheetRepository,
                             CampaignMemberRepository memberRepository) {
        this.imageRepository = imageRepository;
        this.sheetRepository = sheetRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public void set(UUID sheetId, SheetImageKind kind, UUID userId, byte[] bytes, String contentType) {
        CharacterSheet sheet = load(sheetId);
        requireWriter(sheet, userId);
        if (bytes == null || bytes.length == 0) throw new InvalidImageException("O arquivo de imagem está vazio");
        if (bytes.length > MAX_BYTES) throw new InvalidImageException("A imagem excede o limite de 5 MB");
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidImageException("O arquivo enviado não é uma imagem");
        }
        SheetImage image = imageRepository.findBySheetIdAndKind(sheetId, kind)
                .orElseGet(() -> SheetImage.builder().sheetId(sheetId).kind(kind).build());
        image.setContentType(contentType);
        image.setData(bytes);
        imageRepository.save(image);
    }

    @Transactional
    public void delete(UUID sheetId, SheetImageKind kind, UUID userId) {
        requireWriter(load(sheetId), userId);
        imageRepository.deleteBySheetIdAndKind(sheetId, kind);
    }

    @Transactional(readOnly = true)
    public SheetImage get(UUID sheetId, SheetImageKind kind, UUID userId) {
        CharacterSheet sheet = load(sheetId);
        if (sheet.isNpcSheet()) {
            if (!userId.equals(sheet.getNpcOwnerId())) throw new CharacterSheetNotFoundException();
        } else {
            member(sheet, userId);
        }
        return imageRepository.findBySheetIdAndKind(sheetId, kind)
                .orElseThrow(CharacterSheetNotFoundException::new);
    }

    private CharacterSheet load(UUID sheetId) {
        return sheetRepository.findById(sheetId).orElseThrow(CharacterSheetNotFoundException::new);
    }

    private CampaignMember member(CharacterSheet sheet, UUID userId) {
        return memberRepository.findByCampaignIdAndUserId(sheet.getCampaignMember().getCampaign().getId(), userId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
    }

    private void requireWriter(CharacterSheet sheet, UUID userId) {
        if (sheet.isNpcSheet()) {
            if (!userId.equals(sheet.getNpcOwnerId())) throw new CharacterSheetNotFoundException();
            return;
        }
        CampaignMember m = member(sheet, userId);
        boolean owner = sheet.getCampaignMember().getUser().getId().equals(userId);
        if (m.getRole() != Role.MASTER && !owner) {
            throw new AccessDeniedException("Apenas o dono da ficha ou o Mestre pode alterar a imagem");
        }
    }
}
