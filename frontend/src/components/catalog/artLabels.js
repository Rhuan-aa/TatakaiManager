// Rótulos pt-BR das artes marciais (enums do backend).
export const GRADS = [
  { value: 'BEGINNER', label: 'Iniciante' }, { value: 'INTERMEDIATE', label: 'Intermediário' },
  { value: 'MASTER', label: 'Mestre' }, { value: 'GENERIC', label: 'Genérica' },
];
export const ART_CATEGORIES = [
  { value: 'CLASSIC', label: 'Clássica' }, { value: 'CREATED', label: 'Criada' }, { value: 'LOST', label: 'Perdida' },
  { value: 'CLAN', label: 'Clã' }, { value: 'VARIATION', label: 'Variação' },
];
export const CORES = [
  { value: 'STRIKE', label: 'Golpe' }, { value: 'CONTROL', label: 'Controle' },
  { value: 'REACTION', label: 'Reação' }, { value: 'PREPARATION', label: 'Preparação' },
];
export const BASE_FUNCTIONS = ['Postura', 'Deslocamento', 'Transferência de Peso', 'Respiração'];
export const ART_STATUS = { DRAFT: 'Rascunho', PENDING_APPROVAL: 'Aguardando aprovação', APPROVED: 'Aprovada' };

export const lab = (list, v) => list.find((i) => i.value === v)?.label ?? v ?? '—';
