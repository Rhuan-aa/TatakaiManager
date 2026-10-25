export const EXPIRES_LABEL = {
  START_OF_TARGET_TURN: 'até o início do próximo turno',
  END_OF_TARGET_TURN: 'até o fim do turno',
  END_OF_ROUND: 'até o fim da rodada',
  MANUAL: 'até ser removida',
  END_OF_COMBAT: 'até o fim do combate',
};

export function expiresLabel(c) {
  if (c.expiresAt === 'COUNTER') {
    const n = c.turnsRemaining ?? 0;
    return `${n} ${n === 1 ? 'turno' : 'turnos'}`;
  }
  return EXPIRES_LABEL[c.expiresAt] ?? c.expiresAt;
}

// RN-ACAO-02: custos de PA; sp = usa SP (spCost padrão 1); cat = grupo no HUD
export const ACTIONS = [
  { action: 'MAJOR', label: 'Ação maior', pa: 3, sp: true, ask: true, cat: 'MAJOR' },
  { action: 'PREPARED', label: 'Preparada', pa: 3, sp: true, ask: true, cat: 'MAJOR' },
  { action: 'BREATHE', label: 'Respirar', pa: 4, cat: 'MAJOR' },
  { action: 'MINOR', label: 'Ação menor', pa: 1, cat: 'MINOR' },
  { action: 'MINOR_OFFENSIVE', label: 'Menor ofensiva', pa: 2, sp: true, cat: 'MINOR' },
  { action: 'SHOVE', label: 'Atravessar', pa: 1, cat: 'MINOR' },
  { action: 'MOVE', label: 'Mover', pa: 1, cat: 'MOVE' },
  { action: 'DISENGAGE', label: 'Desengajar', pa: 2, cat: 'MOVE' },
  { action: 'STAND_UP', label: 'Levantar', pa: 2, cat: 'MOVE' },
  { action: 'FREE', label: 'Livre', pa: 0, cat: 'FREE' },
];

// Grupos do HUD, na ordem do menu; a cor vem do token --act-*
export const ACTION_CATEGORIES = [
  { key: 'MAJOR', label: 'Ação Maior', color: 'var(--act-major)' },
  { key: 'MINOR', label: 'Ação Menor', color: 'var(--act-minor)' },
  { key: 'MOVE', label: 'Movimento', color: 'var(--act-move)' },
  { key: 'FREE', label: 'Livre', color: 'var(--act-free)' },
];

export const STATUS_LABEL = { SETUP: 'Preparação', ACTIVE: 'Em andamento', ENDED: 'Encerrado' };

export const OUTCOME_LABEL = {
  HIT: 'Acertou', MISS: 'Errou (falha crítica)', DODGED: 'Esquivou', DEFENDED: 'Defendeu', COUNTERED: 'Contra-atacou',
};
export const REACTION_LABEL = { DEFEND: 'Defender', DODGE: 'Esquiva', COUNTER: 'Contra-ataque', NONE: 'Sem reação' };
// Resumo das regras de AttackRules.resolve para a tela de reação
export const REACTION_HINT = {
  DEFEND: { attr: 'CON', cost: 'Grátis', text: 'Empatou ou superou: mitiga 50/75/100% do dano. Falhou: ainda mitiga 25%.' },
  DODGE: { attr: 'DES', cost: 'Grátis', text: 'Precisa superar o ataque: evita todo o dano. Senão, leva o golpe inteiro.' },
  COUNTER: { attr: 'DES', cost: '1 SP', text: 'Superou: revida com o seu Soco. Com 1,5× o ataque, não recebe dano.' },
  NONE: { attr: null, cost: 'Grátis', text: 'Recebe o golpe sem teste.' },
};

export const ACTION_STANCH = { action: 'STANCH', label: 'Estancar', pa: 3 };
export const CLASS_LABEL = { EASY: 'Fácil', REAL: 'Real', LETHAL: 'Letal' };
export const HYSTERIA = {
  FURY: { label: 'Fúria', text: '+4 acerto, +1d6 dano, -2 reações' },
  WALL: { label: 'Muralha', text: '+4 reações e Vontade, -1 PQ por golpe' },
  LIGHTNING: { label: 'Relâmpago', text: '+2 acerto e reações, +1 PA no turno' },
};
export const hasCondition = (p, key) => p.conditions.some((c) => c.key === key);
export const conditionNote = (p, key) => p.conditions.find((c) => c.key === key)?.note;

export const MARTIAL_TYPE_LABEL = { STRIKING: 'Trocação', TAKEDOWN: 'Derrubada', SWEEP: 'Raspagem', GENERAL: 'Geral' };
export const GUARD_LABEL = { NATURAL: 'Natural', INVERTED: 'Invertida', FRONTAL: 'Frontal' };
export const LEAD_FOOT_LABEL = { LEFT: 'pé esquerdo à frente', RIGHT: 'pé direito à frente' };
export const STRIKE_SIDE_LABEL = { FRONT: 'Frente', BACK: 'Trás' };
export const SIDED_BASICS = ['SOCO', 'CHUTE', 'COTOVELADA', 'JOELHADA'];
export const LATE_COUNTER_LABEL = {
  SUCCESS: 'Contra tardio acertou',
  FAIL: 'Contra tardio falhou — reação virou falha crítica',
  SAFE: 'Contra tardio falhou (Resposta Segura)',
};
