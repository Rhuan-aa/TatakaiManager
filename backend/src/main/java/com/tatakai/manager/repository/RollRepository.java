package com.tatakai.manager.repository;

import com.tatakai.manager.entity.Roll;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RollRepository extends JpaRepository<Roll, UUID> {

    /** Feed público: canal PUBLIC (ou antigo, nulo) e privadas já reveladas. */
    @Query("select r from Roll r where r.campaignId = :cid and (r.channel is null "
            + "or r.channel = com.tatakai.manager.entity.RollChannel.PUBLIC or r.revealed = true) "
            + "order by r.createdAt desc")
    List<Roll> findPublic(@Param("cid") UUID campaignId, Pageable pageable);

    @Query("select r from Roll r where r.customChannelId = :chid order by r.createdAt desc")
    List<Roll> findCustom(@Param("chid") UUID customChannelId, Pageable pageable);

    void deleteByCustomChannelId(UUID customChannelId);

    @Query("select r from Roll r where r.campaignId = :cid "
            + "and r.channel = com.tatakai.manager.entity.RollChannel.GM_PRIVATE order by r.createdAt desc")
    List<Roll> findPrivate(@Param("cid") UUID campaignId, Pageable pageable);
}
