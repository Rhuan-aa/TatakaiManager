// Espelho de service/rules/SheetRules.java (como SlotGrid espelha o backend).
// Mudou a regra no backend? Mude aqui também.

export const ATTRS = [
  { key: 'str', enumName: 'STR', label: 'FOR' },
  { key: 'dex', enumName: 'DEX', label: 'DES' },
  { key: 'con', enumName: 'CON', label: 'CON' },
  { key: 'intl', enumName: 'INT', label: 'INT' },
  { key: 'cha', enumName: 'CHA', label: 'CAR' },
  { key: 'men', enumName: 'MEN', label: 'MEN' },
  { key: 'tal', enumName: 'TAL', label: 'TAL' },
];

export const BODY_TYPES = [
  { value: 'ENDOMORPH', label: 'Endomorfo' },
  { value: 'MESOMORPH', label: 'Mesomorfo' },
  { value: 'ECTOMORPH', label: 'Ectomorfo' },
];
export const DOMINANCES = [
  { value: 'RIGHT', label: 'Destro' },
  { value: 'LEFT', label: 'Canhoto' },
  { value: 'AMBIDEXTROUS', label: 'Ambidestro' },
];
export const BAND_LABELS = {
  COLLAPSE: 'Colapso',
  INEPT: 'Inepto',
  DEFICIENT: 'Deficiente',
  UNTRAINED: 'Destreinado',
  FUNCTIONAL: 'Funcional',
};
export const FATIGUE_LABELS = {
  RESTED: 'Descansado',
  TIRED: 'Cansado',
  DRAINED: 'Esgotado',
  EXHAUSTED: 'Exaurido',
  COLLAPSE: 'Colapso',
};
export const label = (list, value) => list.find((i) => i.value === value)?.label ?? value;

// Ajustes de tipo corporal (RN-ATR-05), por atributo (enumName) e por recurso.
const BODY_ADJ = {
  ENDOMORPH: { CON: 10, DEX: -5, HP: 10, SP: 0 },
  MESOMORPH: { STR: 5, CON: 5, HP: 5, SP: 5 },
  ECTOMORPH: { DEX: 10, CON: -5, HP: 0, SP: 10 },
};
const adj = (bt, k) => BODY_ADJ[bt]?.[k] ?? 0;

/** null se v == 0 (Estado Crítico). */
export function mod(v) {
  if (v <= 0) return null;
  if (v <= 3) return -4;
  if (v <= 6) return -2;
  if (v <= 9) return 0;
  return Math.floor(v / 10);
}
export const modQ = (v) => Math.max(0, mod(v) ?? 0);
export const half = (m) => (m > 0 ? Math.trunc(m / 2) : m);

export function band(v) {
  if (v <= 0) return 'COLLAPSE';
  if (v <= 3) return 'INEPT';
  if (v <= 6) return 'DEFICIENT';
  if (v <= 9) return 'UNTRAINED';
  return 'FUNCTIONAL';
}

/** TAL não tem limitador (null). */
export function attributeLimiter(enumName, tal, bt, injuryReduction = 0) {
  if (enumName === 'TAL') return null;
  return 40 + Math.floor(tal / 2) + adj(bt, enumName) - injuryReduction;
}
/** resource: 'HP' | 'SP' | 'KP'. */
export function statusLimiter(resource, tal, bt) {
  if (resource === 'KP') return 8;
  return 60 + Math.floor(tal / 2) + adj(bt, resource);
}

export const initialHpMax = (con) => 10 + con;
export const initialSpMax = (con) => 5 + Math.floor(con / 2);
export const initialKpMax = (con) => 3 + modQ(con);

// RN-ATR-08: índices (kg/m²) min–max por tipo; ponytail: ponto médio do intervalo,
// o backend decide o valor real quando weightKg vem vazio.
const BMI_RANGE = { ENDOMORPH: [20, 40], MESOMORPH: [17, 33], ECTOMORPH: [15, 26] };
export function initialWeight(heightM, bt) {
  const [lo, hi] = BMI_RANGE[bt];
  return Math.round(((lo + hi) / 2) * heightM * heightM * 10) / 10;
}

export function fatigueBand(points) {
  if (points <= 2) return 'RESTED';
  if (points <= 5) return 'TIRED';
  if (points <= 8) return 'DRAINED';
  if (points <= 11) return 'EXHAUSTED';
  return 'COLLAPSE';
}

export const DICE_REGEX = /^\d{1,2}d(4|6|8|10|12|20|100)([+-]\d{1,3})?$/;

/** Patentes Keiken (RN-PROG-08). */
export const KEIKEN = {
  NOVICE: 'Novato', SEASONED: 'Calejado', VETERAN: 'Veterano', HISTORIC: 'Histórico', LEGEND: 'Lenda',
  MYTHIC: 'Mitológico', DRAGON_GOD: 'Deus-Dragão',
};

/** Classificação da luta (RN-PROG-07). */
export const CLASSIFICATIONS = { EASY: 'Fácil', REAL: 'Real', LETHAL: 'Letal', SPARRING: 'Sparring' };

/** EL mínimo de cada patente (RN-PROG-08). */
export const KEIKEN_MIN = { NOVICE: 0, SEASONED: 5, VETERAN: 15, HISTORIC: 30, LEGEND: 55, MYTHIC: 80, DRAGON_GOD: 120 };

/** Subtítulo de cada patamar de Instintos. */
export const KEIKEN_FLAVOR = {
  SEASONED: 'O corpo aprende o básico da sobrevivência.',
  VETERAN: 'O lutador já sabe como entrar numa luta.',
  HISTORIC: 'O lutador controla o ritmo do próprio corpo.',
  LEGEND: 'O lutador consegue o que outros precisariam de anos para entender.',
  MYTHIC: 'Ele se tornou uma história que os outros contam.',
  DRAGON_GOD: 'O que poucos ousam ser, e que ninguém ensina.',
};
