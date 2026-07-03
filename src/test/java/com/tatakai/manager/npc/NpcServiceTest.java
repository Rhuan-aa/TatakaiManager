package com.tatakai.manager.npc;

import com.tatakai.manager.dto.request.*;
import com.tatakai.manager.dto.response.CampaignNpcResponse;
import com.tatakai.manager.dto.response.NpcResponse;
import com.tatakai.manager.dto.response.NpcSummaryResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidImageException;
import com.tatakai.manager.exception.NpcNotFoundException;
import com.tatakai.manager.repository.*;
import com.tatakai.manager.service.NpcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NpcService — Sprint 3 + acervo (US-06, US-07, US-08, US-20)")
class NpcServiceTest {

    @Mock private NpcRepository npcRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignNpcRepository campaignNpcRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private NpcImageRepository npcImageRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private InteractionLogRepository logRepository;

    private NpcService service;

    private User master;
    private User player;

    @BeforeEach
    void setUp() {
        service = new NpcService(npcRepository, campaignRepository,
                campaignNpcRepository, memberRepository, userRepository, npcImageRepository,
                bookingRepository, logRepository);
        master = User.builder().id(UUID.randomUUID()).name("Mestre").email("mestre@rpg.com").build();
        player = User.builder().id(UUID.randomUUID()).name("Player").email("player@rpg.com").build();
    }

    private Npc npcOwnedByMaster() {
        return Npc.builder()
                .id(UUID.randomUUID())
                .name("Aldric")
                .ownerId(master.getId())
                .attributes(NpcAttributes.builder().forca((short) 14).destreza((short) 16).build())
                .interactions(new java.util.ArrayList<>(List.of(
                        new NpcInteraction("Treino", "Treino", "Aprende esgrima", (short) 2))))
                .build();
    }

    @Test
    @DisplayName("listOwned: retorna todos os NPCs do dono")
    void listOwned_returnsMasterNpcs() {
        Npc npc1 = npcOwnedByMaster();
        Npc npc2 = npcOwnedByMaster();
        when(npcRepository.findByOwnerId(master.getId())).thenReturn(List.of(npc1, npc2));

        List<NpcSummaryResponse> result = service.listOwned(master.getId());

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.interactions().stream()
                .anyMatch(i -> i.name().equals("Treino")));
        verify(npcRepository).findByOwnerId(master.getId());
    }

    @Test
    @DisplayName("listOwned: retorna lista vazia quando o dono não tem NPCs")
    void listOwned_noNpcs_returnsEmpty() {
        when(npcRepository.findByOwnerId(master.getId())).thenReturn(List.of());

        List<NpcSummaryResponse> result = service.listOwned(master.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("US-06: cria NPC com atributos opcionais (alguns nulos) e define o dono")
    void create_withOptionalAttributes_setsOwner() {
        var req = new CreateNpcRequest(
                "Vael", "Mercenário taciturno",
                new NpcAttributesDto((short) 14, (short) 16, null, null, null, null),
                List.of(new NpcDetailDto("Visão nas trevas", "Enxerga no escuro")),
                List.of(new NpcDetailDto("Cicatriz no rosto", "Marca de uma antiga batalha")),
                List.of(new NpcDetailDto("Golpe giratório", "Ataque em área")),
                List.of(new NpcInteractionDto("Treino", "Esgrima", "Sessão de treino", (short) 2)));

        when(userRepository.findById(master.getId())).thenReturn(Optional.of(master));
        when(npcRepository.save(any(Npc.class))).thenAnswer(inv -> {
            Npc n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        NpcResponse res = service.create(master.getId(), req);

        assertThat(res.name()).isEqualTo("Vael");
        assertThat(res.ownerId()).isEqualTo(master.getId());
        assertThat(res.attributes().forca()).isEqualTo((short) 14);
        assertThat(res.attributes().constituicao()).isNull();
        assertThat(res.interactions()).hasSize(1);
        assertThat(res.interactions().get(0).type()).isEqualTo("Treino");
        assertThat(res.interactions().get(0).name()).isEqualTo("Esgrima");
        assertThat(res.interactions().get(0).idlePointCost()).isEqualTo((short) 2);
        assertThat(res.knowledge()).hasSize(1);
        assertThat(res.knowledge().get(0).name()).isEqualTo("Visão nas trevas");
        assertThat(res.traits()).hasSize(1);
        assertThat(res.traits().get(0).name()).isEqualTo("Cicatriz no rosto");
        assertThat(res.specs()).hasSize(1);
        assertThat(res.specs().get(0).name()).isEqualTo("Golpe giratório");
    }

    @Test
    @DisplayName("US-07: dono edita o NPC")
    void update_byOwner_updatesNpc() {
        Npc npc = npcOwnedByMaster();
        when(npcRepository.findById(npc.getId())).thenReturn(Optional.of(npc));
        when(npcRepository.save(any(Npc.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new UpdateNpcRequest("Aldric, o Veterano", "Atualizado",
                new NpcAttributesDto((short) 18, null, null, null, null, null),
                List.of(), List.of(), List.of(), List.of(
                        new NpcInteractionDto("Treino", "Esgrima", null, (short) 1),
                        new NpcInteractionDto("Trabalho", "Ferraria", null, (short) 3)));

        NpcResponse res = service.update(npc.getId(), master.getId(), req);

        assertThat(res.name()).isEqualTo("Aldric, o Veterano");
        assertThat(res.attributes().forca()).isEqualTo((short) 18);
        assertThat(res.interactions()).extracting(NpcInteractionDto::name)
                .containsExactlyInAnyOrder("Esgrima", "Ferraria");
    }

    @Test
    @DisplayName("US-07: não-dono não pode editar o NPC — AccessDenied")
    void update_byNonOwner_throwsAccessDenied() {
        Npc npc = npcOwnedByMaster();
        when(npcRepository.findById(npc.getId())).thenReturn(Optional.of(npc));

        var req = new UpdateNpcRequest("Hack", null, null, List.of(), List.of(), List.of(),
                List.of(new NpcInteractionDto("Treino", "Esgrima", null, (short) 1)));

        assertThatThrownBy(() -> service.update(npc.getId(), player.getId(), req))
                .isInstanceOf(AccessDeniedException.class);

        verify(npcRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-08: dono e Mestre associa NPC a uma campanha (visível por padrão)")
    void associate_byOwnerMaster_createsVisibleAssociation() {
        Npc npc = npcOwnedByMaster();
        Campaign campaign = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();

        when(npcRepository.findById(npc.getId())).thenReturn(Optional.of(npc));
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(
                campaign.getId(), master.getId(), Role.MASTER)).thenReturn(true);
        when(campaignNpcRepository.existsByCampaignIdAndNpcId(campaign.getId(), npc.getId()))
                .thenReturn(false);
        when(campaignNpcRepository.save(any(CampaignNpc.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignNpcResponse res = service.associate(npc.getId(), campaign.getId(), master.getId());

        assertThat(res.visible()).isTrue();
        assertThat(res.npcId()).isEqualTo(npc.getId());
        assertThat(res.campaignId()).isEqualTo(campaign.getId());
    }

    @Test
    @DisplayName("US-08: associar sem ser Mestre da campanha é negado")
    void associate_byNonMaster_throwsAccessDenied() {
        Npc npc = npcOwnedByMaster();
        Campaign campaign = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();

        when(npcRepository.findById(npc.getId())).thenReturn(Optional.of(npc));
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(
                campaign.getId(), master.getId(), Role.MASTER)).thenReturn(false);

        assertThatThrownBy(() -> service.associate(npc.getId(), campaign.getId(), master.getId()))
                .isInstanceOf(AccessDeniedException.class);

        verify(campaignNpcRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-20: Mestre oculta NPC para os jogadores")
    void setVisibility_byMaster_hidesNpc() {
        Npc npc = npcOwnedByMaster();
        Campaign campaign = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();
        CampaignNpc assoc = CampaignNpc.builder()
                .id(UUID.randomUUID()).campaign(campaign).npcId(npc.getId()).visible(true).build();

        when(campaignNpcRepository.findByCampaignIdAndNpcId(campaign.getId(), npc.getId()))
                .thenReturn(Optional.of(assoc));
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(
                campaign.getId(), master.getId(), Role.MASTER)).thenReturn(true);
        when(campaignNpcRepository.save(any(CampaignNpc.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignNpcResponse res = service.setVisibility(
                campaign.getId(), npc.getId(), master.getId(), false);

        assertThat(res.visible()).isFalse();
        assertThat(assoc.isVisible()).isFalse();
    }

    @Test
    @DisplayName("US-20: jogador lista NPCs da campanha e vê apenas os visíveis")
    void listForCampaign_asPlayer_returnsOnlyVisible() {
        Campaign campaign = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();
        Npc visivel = npcOwnedByMaster();
        CampaignNpc assocVisivel = CampaignNpc.builder()
                .campaign(campaign).npcId(visivel.getId()).visible(true).build();

        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(Optional.of(CampaignMember.builder()
                        .campaign(campaign).user(player).role(Role.PLAYER).build()));
        when(campaignNpcRepository.findByCampaignIdAndVisibleTrue(campaign.getId()))
                .thenReturn(List.of(assocVisivel));
        when(npcRepository.findAllById(List.of(visivel.getId()))).thenReturn(List.of(visivel));

        List<NpcSummaryResponse> result = service.listForCampaign(campaign.getId(), player.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).visible()).isTrue();
        // a grade de slots usa as interações aceitas pelo NPC (nome + custo)
        assertThat(result.get(0).interactions()).extracting(NpcInteractionDto::name)
                .containsExactly("Treino");
        // garante que o caminho do jogador NÃO consulta todos os NPCs
        verify(campaignNpcRepository, never()).findByCampaignId(any());
    }

    @Test
    @DisplayName("US-20: jogador que acessa NPC oculto recebe 404 (não revela existência)")
    void getForCampaign_hiddenNpc_asPlayer_throwsNotFound() {
        Campaign campaign = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();
        Npc oculto = npcOwnedByMaster();
        CampaignNpc assoc = CampaignNpc.builder()
                .campaign(campaign).npcId(oculto.getId()).visible(false).build();

        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(Optional.of(CampaignMember.builder()
                        .campaign(campaign).user(player).role(Role.PLAYER).build()));
        when(campaignNpcRepository.findByCampaignIdAndNpcId(campaign.getId(), oculto.getId()))
                .thenReturn(Optional.of(assoc));

        assertThatThrownBy(() ->
                service.getForCampaign(campaign.getId(), oculto.getId(), player.getId()))
                .isInstanceOf(NpcNotFoundException.class);
    }

    // ---------- imagem do NPC ----------

    @Test
    @DisplayName("Imagem: dono envia imagem válida (persistida)")
    void setImage_byOwner_persists() {
        Npc npc = npcOwnedByMaster();
        when(npcRepository.findById(npc.getId())).thenReturn(Optional.of(npc));

        service.setImage(npc.getId(), master.getId(), new byte[] {1, 2, 3}, "image/png");

        var captor = org.mockito.ArgumentCaptor.forClass(NpcImage.class);
        verify(npcImageRepository).save(captor.capture());
        assertThat(captor.getValue().getNpcId()).isEqualTo(npc.getId());
        assertThat(captor.getValue().getContentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Imagem: arquivo que não é imagem é rejeitado (400)")
    void setImage_nonImage_rejected() {
        Npc npc = npcOwnedByMaster();
        when(npcRepository.findById(npc.getId())).thenReturn(Optional.of(npc));

        assertThatThrownBy(() ->
                service.setImage(npc.getId(), master.getId(), new byte[] {1}, "application/pdf"))
                .isInstanceOf(InvalidImageException.class);

        verify(npcImageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Imagem: não-dono não pode enviar imagem — AccessDenied")
    void setImage_byNonOwner_throwsAccessDenied() {
        Npc npc = npcOwnedByMaster();
        when(npcRepository.findById(npc.getId())).thenReturn(Optional.of(npc));

        assertThatThrownBy(() ->
                service.setImage(npc.getId(), player.getId(), new byte[] {1}, "image/png"))
                .isInstanceOf(AccessDeniedException.class);

        verify(npcImageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Imagem: jogador não vê imagem de NPC oculto (404)")
    void getImage_hiddenNpc_asPlayer_throwsNotFound() {
        Campaign campaign = Campaign.builder().id(UUID.randomUUID()).name("Aether").master(master).build();
        Npc oculto = npcOwnedByMaster();
        CampaignNpc assoc = CampaignNpc.builder()
                .campaign(campaign).npcId(oculto.getId()).visible(false).build();

        when(memberRepository.findByCampaignIdAndUserId(campaign.getId(), player.getId()))
                .thenReturn(Optional.of(CampaignMember.builder()
                        .campaign(campaign).user(player).role(Role.PLAYER).build()));
        when(campaignNpcRepository.findByCampaignIdAndNpcId(campaign.getId(), oculto.getId()))
                .thenReturn(Optional.of(assoc));

        assertThatThrownBy(() ->
                service.getImage(campaign.getId(), oculto.getId(), player.getId()))
                .isInstanceOf(NpcNotFoundException.class);
    }

    @Test
    @DisplayName("Remover da campanha: Mestre desassocia e limpa agendamentos+logs do NPC")
    void removeFromCampaign_byMaster_cleansBookingsAndLogs() {
        UUID campaignId = UUID.randomUUID();
        Npc npc = npcOwnedByMaster();
        CampaignNpc assoc = CampaignNpc.builder()
                .id(UUID.randomUUID()).npcId(npc.getId()).visible(true).build();
        Booking b = Booking.builder().id(UUID.randomUUID()).npcId(npc.getId()).npcName(npc.getName()).build();

        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, master.getId(), Role.MASTER))
                .thenReturn(true);
        when(campaignNpcRepository.findByCampaignIdAndNpcId(campaignId, npc.getId()))
                .thenReturn(Optional.of(assoc));
        when(bookingRepository.findByNpcIdAndTimeSkipDay_TimeSkip_CampaignId(npc.getId(), campaignId))
                .thenReturn(List.of(b));

        service.removeFromCampaign(campaignId, npc.getId(), master.getId());

        verify(logRepository).deleteByBookingIn(List.of(b));
        verify(bookingRepository).deleteAll(List.of(b));
        verify(campaignNpcRepository).delete(assoc);
    }

    @Test
    @DisplayName("Remover da campanha: jogador (não-Mestre) é negado — AccessDenied")
    void removeFromCampaign_byNonMaster_throwsAccessDenied() {
        UUID campaignId = UUID.randomUUID();
        UUID npcId = UUID.randomUUID();
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, player.getId(), Role.MASTER))
                .thenReturn(false);

        assertThatThrownBy(() -> service.removeFromCampaign(campaignId, npcId, player.getId()))
                .isInstanceOf(AccessDeniedException.class);

        verify(campaignNpcRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Remover da campanha: NPC não associado resulta em 404")
    void removeFromCampaign_notAssociated_throwsNotFound() {
        UUID campaignId = UUID.randomUUID();
        UUID npcId = UUID.randomUUID();
        when(memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, master.getId(), Role.MASTER))
                .thenReturn(true);
        when(campaignNpcRepository.findByCampaignIdAndNpcId(campaignId, npcId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeFromCampaign(campaignId, npcId, master.getId()))
                .isInstanceOf(NpcNotFoundException.class);
    }
}
