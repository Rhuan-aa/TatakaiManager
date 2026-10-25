package com.tatakai.manager.entity;

/** Ações de combate (RN-ACAO-02). */
public enum CombatAction {
    MAJOR("Ação Maior"), PREPARED("Ação Preparada"), BREATHE("Recuperar Fôlego"), MINOR("Ação Menor"),
    MINOR_OFFENSIVE("Ação Menor Ofensiva"), DISENGAGE("Desengajar"), MOVE("Mover"),
    STAND_UP("Levantar-se"), SHOVE("Atravessar"), FREE("Ação Livre"), STANCH("Estancar"),
    CHANGE_GUARD("Trocar guarda"), CHANGE_BASE("Trocar base");

    private final String label;

    CombatAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
