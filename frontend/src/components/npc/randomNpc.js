// Gerador de NPC aleatório (só no cliente: NPC é do Mestre, não precisa de rolagem auditada).
import { ATTRS, BODY_TYPES } from '../sheet/sheetRules';

const NAMES = [
  // japoneses
  'Akira', 'Haruto', 'Ren', 'Sora', 'Yui', 'Hina', 'Kenji', 'Takeshi', 'Ayame', 'Kaede', 'Daichi', 'Mei',
  // chineses e coreanos
  'Li Wei', 'Mei Lin', 'Zhao Yun', 'Xiu Ying', 'Jin-woo', 'Min-ji', 'Seo-yeon', 'Hyun',
  // latinos e ibéricos
  'Mateo', 'Valentina', 'Joaquim', 'Isabela', 'Diego', 'Lucía', 'Rafael', 'Inês', 'Tiago', 'Carmen',
  // anglo-saxões e germânicos
  'Edgar', 'Mildred', 'Wulfric', 'Rowena', 'Gunther', 'Hilde', 'Oswin', 'Greta',
  // nórdicos
  'Bjorn', 'Astrid', 'Leif', 'Sigrid', 'Ragnar', 'Freya',
  // eslavos
  'Dmitri', 'Anya', 'Bogdan', 'Milena', 'Yaroslav', 'Zora',
  // árabes e persas
  'Karim', 'Layla', 'Rashid', 'Yasmin', 'Darius', 'Shirin', 'Omar', 'Zahra',
  // africanos
  'Kwame', 'Amara', 'Chidi', 'Nia', 'Tendai', 'Zuri', 'Kofi', 'Ayo',
  // indianos
  'Arjun', 'Priya', 'Ravi', 'Ananya', 'Vikram', 'Devi',
  // indígenas e polinésios
  'Tupã', 'Jaci', 'Moana', 'Kai', 'Aiyana', 'Tahu',
  // gregos e latinos clássicos
  'Theron', 'Callista', 'Lysander', 'Daphne', 'Cassius', 'Livia',
];

const die = (n) => 1 + Math.floor(Math.random() * n);
const pick = (xs) => xs[Math.floor(Math.random() * xs.length)];

// Espelho de BuildRules.rarityFor (método Clássico, 100 não dá Divino).
export function rarityFor(d100) {
  if (d100 <= 40) return 'COMMON';
  if (d100 <= 70) return 'RARE';
  if (d100 <= 90) return 'EPIC';
  return 'LEGENDARY';
}

/** 1d4 traços: d100 raridade + d20 na tabela. ponytail: slot sem traço no catálogo é descartado, não fica pendente. */
export function randomTraits(catalog) {
  const out = [];
  for (let i = die(4); i > 0; i--) {
    const rarity = rarityFor(die(100));
    const roll = die(20);
    if (rarity === 'LEGENDARY' && roll === 20) continue;
    const t = catalog.find((c) => c.rarity === rarity && c.rollNumber === roll);
    if (t && !out.includes(t)) out.push(t);
  }
  return out;
}

function dominance() {
  const r = die(100);
  return r <= 80 ? 'RIGHT' : r <= 95 ? 'LEFT' : 'AMBIDEXTROUS';
}

/** Campos da ficha (formato de initialSheetFields) + traços do catálogo. 7d12 em ordem nos atributos. */
export function randomNpc(catalog = []) {
  const name = pick(NAMES);
  return {
    name,
    sheet: {
      name,
      bodyType: pick(BODY_TYPES).value,
      dominance: dominance(),
      heightM: (1.5 + Math.random() * 0.5).toFixed(2),
      weightKg: '',
      ...Object.fromEntries(ATTRS.map((a) => [a.key, die(12)])),
    },
    traits: randomTraits(catalog),
  };
}
