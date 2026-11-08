// Rótulos de `purpose` vêm do AttackService (backend, combat/service): "Ataque: …", "Defender: …",
// "Esquiva: …", "Contra-ataque: …", "Contra-ataque tardio: …", "Resistir a X: …".
const MAJOR = 'var(--act-major)';
const MOVE = 'var(--act-move)';
const BY_SOURCE = { TECHNIQUE: 'TÉCNICA', BASIC_ATTACK: 'ATAQUE BÁSICO', ATTRIBUTE_TEST: 'ATRIBUTO', CUSTOM: 'LIVRE' };

export function rollKind(roll) {
  const head = (roll.purpose ?? '').split(':')[0].trim();
  if (head === 'Contra-ataque tardio') return { label: 'CONTRA TARDIO', color: MOVE };
  if (head === 'Contra-ataque') return { label: 'CONTRA', color: MOVE };
  if (head === 'Ataque') return { label: 'ATAQUE', color: MAJOR };
  if (head === 'Defender') return { label: 'DEFESA', color: MOVE };
  if (head === 'Esquiva') return { label: 'ESQUIVA', color: MOVE };
  if (head.startsWith('Resistir a')) return { label: 'RESISTÊNCIA', color: 'var(--text)' };
  return { label: BY_SOURCE[roll.sourceType] ?? 'ROLAGEM', color: 'var(--text)' };
}
