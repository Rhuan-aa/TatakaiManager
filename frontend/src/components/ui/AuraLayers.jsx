// Estado contínuo das condições (§53.4): camadas de borda dentro de um elemento com position:relative
// (classe .cond-host). Só CSS; no máximo 2 auras, pela prioridade abaixo.
const AURA = {
  QUEIMANDO: 'FOGO', AREA_EM_CHAMAS: 'FOGO',
  CONGELANDO: 'GELO',
  ENVENENADO: 'VENENO',
  SUFOCANDO: 'SUFOCO', ESTRANGULADO: 'SUFOCO', NA_FUMACA: 'SUFOCO',
  PARALISADO: 'CHOQUE',
  ATORDOADO: 'TONTURA', DESORIENTADO: 'TONTURA',
  CEGO: 'CEGO', MEIO_CEGO: 'CEGO',
  DERRUBADO: 'QUEDA',
  INCONSCIENTE: 'DESMAIO', BEIRA_DA_MORTE: 'DESMAIO', MORTO: 'DESMAIO',
  AGARRADO: 'AGARRE', IMOBILIZADO: 'AGARRE', PRESO: 'AGARRE', CLINCH: 'AGARRE',
  PREPARADO: 'FOCO', ESTIMULADO: 'FOCO',
};
const PRIORITY = ['DESMAIO', 'FOGO', 'GELO', 'CHOQUE', 'VENENO', 'SUFOCO', 'CEGO', 'AGARRE', 'TONTURA', 'QUEDA', 'FOCO'];

function aurasFor(keys = []) {
  const set = new Set(keys.map((k) => AURA[k]).filter(Boolean));
  return PRIORITY.filter((a) => set.has(a)).slice(0, 2);
}

export default function AuraLayers({ keys }) {
  return aurasFor(keys).map((a) => <span key={a} className={`aura aura-${a} no-print`} aria-hidden="true" />);
}
