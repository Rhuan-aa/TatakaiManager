package com.tatakai.manager.repository;

import com.tatakai.manager.entity.TimeSkipDay;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface TimeSkipDayRepository extends JpaRepository<TimeSkipDay, UUID> {

    /**
     * Trava a linha do dia (SELECT … FOR UPDATE) até o fim da transação do agendamento: reservas do mesmo dia passam
     * uma de cada vez, então a checagem de sobreposição de faixas não tem corrida (o unique só cobre o slot inicial).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TimeSkipDay> findByTimeSkipIdAndDayNumber(UUID timeSkipId, short dayNumber);
}
