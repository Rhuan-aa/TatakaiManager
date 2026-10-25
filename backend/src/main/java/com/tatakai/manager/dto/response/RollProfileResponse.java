package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.BasicAttack;
import com.tatakai.manager.entity.RollPart;
import com.tatakai.manager.entity.RollSourceType;

import java.util.List;
import java.util.UUID;

/** Perfil calculado sob demanda. hit/damage = parcelas fixas (sem o d20 nem os dados). */
public record RollProfileResponse(RollSourceType sourceType, UUID sourceId, String name, String variation,
        BasicAttack basicAttack, int spCost, int critMargin, int advantage, List<RollPart> hit,
        DamageDice damageDice, List<RollPart> damage, String hitFormula, String damageFormula) {

    public record DamageDice(int count, int sides, String source) {}
}
