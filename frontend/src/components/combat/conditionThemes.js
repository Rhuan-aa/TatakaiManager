// Temas visuais das condições (ui/ConditionFx). Poucos temas; cada chave de condição aponta para um deles.
// Chaves: backend/src/main/resources/catalog/conditions.json + constantes de VitalService/AttackService.
export const THEMES = {
  FOGO: { color: 'var(--fx-fire)', icon: 'flame', word: 'FWOOSH!', motion: 'rise', shape: 'burst' },
  GELO: { color: 'var(--fx-ice)', icon: 'snow', word: 'KRAK!', motion: 'freeze', shape: 'shard' },
  VENENO: { color: 'var(--fx-poison)', icon: 'drop', word: 'GLUB…', motion: 'squeeze', shape: 'blob' },
  SUFOCO: { color: 'var(--fx-smoke)', icon: 'wind', word: 'GHK!', motion: 'squeeze', shape: 'blob' },
  CHOQUE: { color: 'var(--fx-shock)', icon: 'bolt', word: 'ZZT!', motion: 'jitter', shape: 'burst' },
  TONTURA: { color: 'var(--fx-dizzy)', icon: 'spiral', word: 'TOIN!', motion: 'spin', shape: 'burst' },
  QUEDA: { color: 'var(--fx-fall)', icon: 'back', word: 'TOMBO!', motion: 'drop', shape: 'shard', rotate: true },
  DESMAIO: { color: 'var(--fx-faint)', icon: 'moon', word: 'APAGOU!', motion: 'drop', shape: 'blob' },
  SANGUE: { color: 'var(--blood)', icon: 'drop', word: 'SANGRA!', motion: 'pop', shape: 'blob' },
  AGARRE: { color: 'var(--fx-grab)', icon: 'hand', word: 'GRAB!', motion: 'squeeze', shape: 'burst' },
  FOCO: { color: 'var(--fx-boost)', icon: 'star', word: 'HAA!', motion: 'rise', shape: 'burst' },
  GENERICO: { color: 'var(--accent)', icon: 'circle', word: null, motion: 'pop', shape: 'burst' },
};

const KEY_THEME = {
  QUEIMANDO: 'FOGO', AREA_EM_CHAMAS: 'FOGO',
  CONGELANDO: 'GELO',
  ENVENENADO: 'VENENO',
  SUFOCANDO: 'SUFOCO', ESTRANGULADO: 'SUFOCO', NA_FUMACA: 'SUFOCO',
  PARALISADO: 'CHOQUE',
  ATORDOADO: 'TONTURA', DESORIENTADO: 'TONTURA', OLHO_IRRITADO: 'TONTURA', MEIO_CEGO: 'TONTURA', CEGO: 'TONTURA',
  OUVIDO_DANIFICADO: 'TONTURA', PANICO: 'TONTURA',
  DERRUBADO: 'QUEDA', DESEQUILIBRADO: 'QUEDA', QUEBRA_DE_BASE: 'QUEDA',
  INCONSCIENTE: 'DESMAIO', EXAUSTO: 'DESMAIO', DISSOCIADO: 'DESMAIO', BEIRA_DA_MORTE: 'DESMAIO', MORTO: 'DESMAIO',
  DANO_PERSISTENTE: 'SANGUE', ALEIJADO: 'SANGUE', ENFRAQUECIDO: 'SANGUE', FERIDA: 'SANGUE',
  AGARRADO: 'AGARRE', IMOBILIZADO: 'AGARRE', CLINCH: 'AGARRE', PRESO: 'AGARRE', SUPRIMIDO: 'AGARRE', PESO_DO_ARCO: 'AGARRE',
  PREPARADO: 'FOCO', ESTIMULADO: 'FOCO', GIZ_MAGNESIO: 'FOCO', AMONIA_USADA: 'FOCO', ESTABILIZADO: 'FOCO', FURTIVO: 'FOCO',
};

// Palavra própria por chave (substitui a do tema)
const WORD = {
  INCONSCIENTE: 'APAGOU!', EXAUSTO: 'ARF… ARF', MORTO: 'FIM.', BEIRA_DA_MORTE: 'TUM… TUM', DISSOCIADO: '…!?',
  CEGO: '?!', PANICO: 'AAAH!', QUEBRA_DE_BASE: 'CRAC!', ESTABILIZADO: 'UFA…', FURTIVO: 'SHH…',
  VULNERAVEL: 'BRECHA!', MARCADO: 'MIRA!',
};

export function themeFor(key, name) {
  const t = THEMES[KEY_THEME[key]] ?? THEMES.GENERICO;
  return { ...t, word: WORD[key] ?? t.word ?? `${name}!` };
}
