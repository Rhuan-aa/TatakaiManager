package com.tatakai.manager.service.rules;

import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.MartialType;

import java.util.List;

/**
 * Agarre (Ataques Básicos: Agarrão, Clinch, Projeção, Constrição, Estrangulamento; plano §39). Puro.
 * O vínculo é uma condição no alvo com `sourceParticipantId` = quem agarra e a nota = ponto/tipo.
 */
public final class GrappleRules {

    public static final String GRABBED = "AGARRADO";
    public static final String RESTRAINED = "IMOBILIZADO";
    public static final String CLINCH = "CLINCH";
    public static final String STRANGLED = "ESTRANGULADO";
    public static final String PANIC = "PANICO";
    /** Nota da Imobilização por Constrição (rústica: escapa sem desvantagem). */
    public static final String CONSTRICTION = "CONSTRICAO";
    /** Nota do Clinch em quem o iniciou (só ele projeta ou estrangula a partir do Clinch). */
    public static final String CLINCH_INIT = "INICIOU";
    public static final List<String> HOLDS = List.of(GRABBED, RESTRAINED, CLINCH, STRANGLED);

    /** Ponto do Agarrão: braço (bloqueia o braço, base de chaves), perna (derrubar), tronco/nuca (Clinch), pescoço. */
    public enum Grip {
        ARM("Braço"), LEG("Perna"), TORSO("Tronco ou nuca"), NECK("Pescoço");
        public final String label;
        Grip(String label) { this.label = label; }
    }

    public enum StrangleType { BLOOD, AIR }

    /** Ponto de tronco/nuca entra em Clinch (mútuo) em vez de Agarrado. */
    public static boolean clinch(Grip g) {
        return g == Grip.TORSO;
    }

    /** Custo de PA para escapar: Agarrado, Clinch e Estrangulado com Ação Menor (1); Imobilizado com Ação Maior (3). */
    public static int escapeCost(String hold) {
        return RESTRAINED.equals(hold) ? 3 : 1;
    }

    /** Desvantagem na fuga: Agarrado, Estrangulado e Imobilizado sim (a Constrição básica, não); Clinch nunca. */
    public static boolean escapeDisadvantage(String hold, String note) {
        if (CLINCH.equals(hold)) return false;
        if (RESTRAINED.equals(hold)) return !CONSTRICTION.equals(note);
        return true;
    }

    /**
     * Atributos permitidos na fuga: Força; artes de Derrubada/Raspagem podem usar Destreza contra Agarrado/Estrangulado;
     * Imobilizado aceita Força ou Destreza; Clinch só Força.
     */
    public static boolean escapeAllows(String hold, Attribute a, MartialType art) {
        if (a == Attribute.STR) return true;
        if (a != Attribute.DEX) return false;
        if (RESTRAINED.equals(hold)) return true;
        if (CLINCH.equals(hold)) return false;
        return art == MartialType.TAKEDOWN || art == MartialType.SWEEP;
    }

    /** Competição: vence quem tirar mais; empate fica com quem resiste (o status quo). */
    public static boolean wins(int challenger, int resister) {
        return challenger > resister;
    }

    /** Estrangulamento respiratório: Mental ND 12, +2 a cada turno seguido. */
    public static int panicNd(int turns) {
        return 12 + 2 * Math.max(0, turns);
    }

    /** Após 3 rodadas estrangulado já desmaiado, o cérebro perde 1/4 do HP máximo por rodada. */
    public static final int BRAIN_DAMAGE_AFTER = 3;

    public static int brainDamage(int hpMax) {
        return Math.max(1, hpMax / 4);
    }

    private GrappleRules() {}
}
