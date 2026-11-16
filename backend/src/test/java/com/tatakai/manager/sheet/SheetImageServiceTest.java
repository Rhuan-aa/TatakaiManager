package com.tatakai.manager.sheet;

import com.tatakai.manager.auth.entity.User;
import com.tatakai.manager.campaign.entity.Campaign;
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
import com.tatakai.manager.sheet.service.SheetImageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Imagens da ficha")
class SheetImageServiceTest {

    @Mock private SheetImageRepository imageRepository;
    @Mock private CharacterSheetRepository sheetRepository;
    @Mock private CampaignMemberRepository memberRepository;

    private SheetImageService service;
    private Campaign campaign;
    private User master, owner, other;
    private CharacterSheet sheet;
    private static final byte[] PNG = {1, 2, 3};

    @BeforeEach
    void setUp() {
        service = new SheetImageService(imageRepository, sheetRepository, memberRepository);
        campaign = Campaign.builder().id(UUID.randomUUID()).build();
        master = user();
        owner = user();
        other = user();
        member(master, Role.MASTER);
        CampaignMember ownerMember = member(owner, Role.PLAYER);
        member(other, Role.PLAYER);
        sheet = CharacterSheet.builder().id(UUID.randomUUID()).campaignMember(ownerMember).build();
        lenient().when(sheetRepository.findById(sheet.getId())).thenReturn(Optional.of(sheet));
    }

    private User user() {
        return User.builder().id(UUID.randomUUID()).name("u").build();
    }

    private CampaignMember member(User u, Role r) {
        CampaignMember m = CampaignMember.builder().id(UUID.randomUUID()).campaign(campaign).user(u).role(r).build();
        lenient().when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), u.getId()))
                .thenReturn(Optional.of(m));
        return m;
    }

    @Test
    void donoEMestreEnviam() {
        service.set(sheet.getId(), SheetImageKind.PORTRAIT, owner.getId(), PNG, "image/png");
        service.set(sheet.getId(), SheetImageKind.FULL_BODY, master.getId(), PNG, "image/png");
        verify(imageRepository, times(2)).save(any(SheetImage.class));
    }

    @Test
    void outroJogadorNaoEnvia() {
        assertThrows(AccessDeniedException.class,
                () -> service.set(sheet.getId(), SheetImageKind.PORTRAIT, other.getId(), PNG, "image/png"));
        assertThrows(AccessDeniedException.class,
                () -> service.delete(sheet.getId(), SheetImageKind.PORTRAIT, other.getId()));
    }

    @Test
    void validaArquivo() {
        UUID id = sheet.getId();
        UUID u = owner.getId();
        assertThrows(InvalidImageException.class, () -> service.set(id, SheetImageKind.PORTRAIT, u, new byte[0], "image/png"));
        assertThrows(InvalidImageException.class,
                () -> service.set(id, SheetImageKind.PORTRAIT, u, new byte[5 * 1024 * 1024 + 1], "image/png"));
        assertThrows(InvalidImageException.class, () -> service.set(id, SheetImageKind.PORTRAIT, u, PNG, "text/plain"));
        verify(imageRepository, never()).save(any());
    }

    @Test
    void donoApaga() {
        service.delete(sheet.getId(), SheetImageKind.PORTRAIT, owner.getId());
        verify(imageRepository).deleteBySheetIdAndKind(sheet.getId(), SheetImageKind.PORTRAIT);
    }

    @Test
    void fichaDeNpcSoDoDono() {
        UUID npcOwner = UUID.randomUUID();
        CharacterSheet npc = CharacterSheet.builder().id(UUID.randomUUID()).npcId(UUID.randomUUID())
                .npcOwnerId(npcOwner).build();
        when(sheetRepository.findById(npc.getId())).thenReturn(Optional.of(npc));
        service.set(npc.getId(), SheetImageKind.PORTRAIT, npcOwner, PNG, "image/png");
        UUID outro = master.getId();
        assertThrows(CharacterSheetNotFoundException.class,
                () -> service.set(npc.getId(), SheetImageKind.PORTRAIT, outro, PNG, "image/png"));
        assertThrows(CharacterSheetNotFoundException.class,
                () -> service.get(npc.getId(), SheetImageKind.PORTRAIT, outro));
    }

    @Test
    void qualquerMembroLe() {
        SheetImage img = SheetImage.builder().contentType("image/png").data(PNG).build();
        when(imageRepository.findBySheetIdAndKind(sheet.getId(), SheetImageKind.PORTRAIT)).thenReturn(Optional.of(img));
        assertSame(img, service.get(sheet.getId(), SheetImageKind.PORTRAIT, other.getId()));
    }

    @Test
    void semImagem404EForaDaCampanha403() {
        assertThrows(CharacterSheetNotFoundException.class,
                () -> service.get(sheet.getId(), SheetImageKind.FULL_BODY, owner.getId()));
        assertThrows(AccessDeniedException.class,
                () -> service.get(sheet.getId(), SheetImageKind.FULL_BODY, UUID.randomUUID()));
    }
}
