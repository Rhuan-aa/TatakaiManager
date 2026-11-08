package com.tatakai.manager.combat.entity;

/** Ações de combate (RN-ACAO-02). */
public enum CombatAction {
    MAJOR("Ação Maior"), PREPARED("Ação Preparada"), BREATHE("Recuperar Fôlego"), MINOR("Ação Menor"),
    MINOR_OFFENSIVE("Ação Menor Ofensiva"), DISENGAGE("Desengajar"), MOVE("Mover"),
    STAND_UP("Levantar-se"), SHOVE("Atravessar"), FREE("Ação Livre"), STANCH("Estancar"),
    CHANGE_GUARD("Trocar guarda"), CHANGE_BASE("Trocar base"), EXTINGUISH("Apagar chamas"),
    STABILIZE("Estabilizar"), DRAW("Sacar/guardar"), RELOAD("Recarregar"), COCK("Engatilhar"),
    UNJAM("Destravar a arma"), TOGGLE_MODE("Trocar o modo da arma"), USE_ITEM("Usar consumível"), WILL_TEST("Teste de Vontade");

    private final String label;

    CombatAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
