// Geometria do boneco de ferimentos (§53.3). viewBox 200x400, visto de frente:
// o lado DIREITO do personagem fica à esquerda de quem olha (x menor).
// Membros são segmentos afilados entre articulações; o contorno único sai de desenhar todas as
// partes com traço grosso por baixo e o preenchimento por cima (ver BodyMap).

// Hematoma: vermelho vivo -> roxo -> roxo escuro -> quase preto, conforme a gravidade
export const BRUISE = { MINOR: '#d9607e', MODERATE: '#9b4f96', SEVERE: '#5e2a6e', LETHAL: '#2b1033' }

export const VIEW_W = 200
export const VIEW_H = 400

// x desenhado para o lado RIGHT (à esquerda de quem olha); LEFT é o espelho
const mx = (s, x) => (s === 'RIGHT' ? x : 200 - x)
const out = (s) => (s === 'RIGHT' ? -1 : 1) // sentido "para fora do corpo" no eixo x

/** Segmento afilado (cápsula) de a até b, com raios ra e rb. */
export function taper([ax, ay], [bx, by], ra, rb) {
  const dx = bx - ax
  const dy = by - ay
  const len = Math.hypot(dx, dy) || 1
  const nx = -dy / len
  const ny = dx / len
  const f = (n) => n.toFixed(1)
  const p1 = [ax + nx * ra, ay + ny * ra]
  const p2 = [bx + nx * rb, by + ny * rb]
  const p3 = [bx - nx * rb, by - ny * rb]
  const p4 = [ax - nx * ra, ay - ny * ra]
  const d = `M${f(p1[0])} ${f(p1[1])} L${f(p2[0])} ${f(p2[1])} A${rb} ${rb} 0 0 0 ${f(p3[0])} ${f(p3[1])} `
    + `L${f(p4[0])} ${f(p4[1])} A${ra} ${ra} 0 0 0 ${f(p1[0])} ${f(p1[1])} Z`
  const xs = [ax - ra, ax + ra, bx - rb, bx + rb]
  const ys = [ay - ra, ay + ra, by - rb, by + rb]
  return { d, box: [Math.min(...xs), Math.min(...ys), Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys)] }
}

export const circle = ([cx, cy], r) => ({
  d: `M${cx - r} ${cy} A${r} ${r} 0 1 0 ${cx + r} ${cy} A${r} ${r} 0 1 0 ${cx - r} ${cy} Z`,
  box: [cx - r, cy - r, 2 * r, 2 * r],
})

const path = (d, box) => ({ d, box })

// Articulações do lado RIGHT (espelhadas para LEFT).
const J = {
  shoulder: [67, 82], elbow: [55, 150], wrist: [47, 210], knuckle: [44, 232], tip: [42, 252],
  hip: [85, 218], knee: [86, 294], ankle: [86, 364], toe: [80, 386],
}
const at = (s, k) => [mx(s, J[k][0]), J[k][1]]

function hand(s) {
  return taper(at(s, 'wrist'), at(s, 'tip'), 7, 7.5)
}
function palm(s) {
  return taper(at(s, 'wrist'), at(s, 'knuckle'), 7, 8)
}
/** Dedo n (1 = polegar … 5 = mínimo), só desenhado quando a mão aparece aberta. */
function finger(s, n) {
  const [kx, ky] = at(s, 'knuckle')
  if (n === 1) { // palmas para a frente: o polegar fica do lado de fora
    const base = [kx + out(s) * 7, ky - 12]
    return taper(base, [base[0] + out(s) * 7, base[1] + 14], 2.8, 2.4)
  }
  const off = (n - 3.5) * 4.6 // indicador perto do polegar, mínimo do lado de dentro
  const base = [kx - out(s) * off, ky + 2]
  return taper(base, [base[0] - out(s) * off * 0.25, base[1] + 20 - Math.abs(n - 3) * 2.5], 2.4, 2.1)
}
function foot(s) {
  return taper(at(s, 'ankle'), at(s, 'toe'), 6.5, 9)
}
function toe(s, n) {
  const [tx, ty] = at(s, 'toe')
  const x = tx - out(s) * (6 - (n - 1) * 3.2) // dedão do lado de dentro
  return circle([x, ty + 6 - Math.abs(n - 2) * 0.6], n === 1 ? 3 : 2.3)
}

/** Formas das partes. `split` = mão/pé abertos em dedos. */
export function shapesFor(split = {}) {
  const out = {
    HEAD: path('M100 8 C116 8 122 22 121 36 C120 50 111 60 100 60 C89 60 80 50 79 36 C78 22 84 8 100 8 Z', [79, 8, 42, 52]),
    NECK: path('M90 54 C91 62 90 68 86 74 L114 74 C110 68 109 62 110 54 C104 58 96 58 90 54 Z', [86, 54, 28, 20]),
    TORSO: path('M60 80 C66 72 80 70 88 72 C96 76 104 76 112 72 C120 70 134 72 140 80 C139 102 134 122 130 140 L70 140 C66 122 61 102 60 80 Z', [60, 70, 80, 70]),
    ABDOMEN: path('M70 140 L130 140 C129 160 128 178 129 196 L71 196 C72 178 71 160 70 140 Z', [70, 140, 60, 56]),
    PELVIS: path('M71 196 L129 196 C134 206 136 216 132 228 C122 232 110 236 100 244 C90 236 78 232 68 228 C64 216 66 206 71 196 Z', [64, 196, 72, 48]),
  }
  for (const s of ['RIGHT', 'LEFT']) {
    out[`${s}_SHOULDER`] = circle(at(s, 'shoulder'), 13)
    out[`${s}_ARM`] = taper(at(s, 'shoulder'), at(s, 'elbow'), 12, 9)
    out[`${s}_ELBOW`] = circle(at(s, 'elbow'), 9.5)
    out[`${s}_FOREARM`] = taper(at(s, 'elbow'), at(s, 'wrist'), 9, 6.5)
    out[`${s}_HAND`] = split[`${s}_HAND`] ? palm(s) : hand(s)
    if (split[`${s}_HAND`]) for (let n = 1; n <= 5; n++) out[`${s}_FINGER_${n}`] = finger(s, n)
    out[`${s}_THIGH`] = taper(at(s, 'hip'), at(s, 'knee'), 15, 10.5)
    out[`${s}_KNEE`] = circle(at(s, 'knee'), 10.5)
    out[`${s}_LEG`] = taper(at(s, 'knee'), at(s, 'ankle'), 10, 6)
    out[`${s}_FOOT`] = foot(s)
    if (split[`${s}_FOOT`]) for (let n = 1; n <= 5; n++) out[`${s}_TOE_${n}`] = toe(s, n)
  }
  return out
}

/** Ordem de desenho (do fundo para a frente). */
export function drawOrder(shapes) {
  const order = ['NECK', 'HEAD', 'PELVIS', 'ABDOMEN', 'TORSO']
  for (const s of ['RIGHT', 'LEFT']) {
    order.push(`${s}_THIGH`, `${s}_LEG`, `${s}_FOOT`, `${s}_KNEE`)
    for (let n = 1; n <= 5; n++) order.push(`${s}_TOE_${n}`)
    order.push(`${s}_ARM`, `${s}_FOREARM`, `${s}_HAND`)
    for (let n = 1; n <= 5; n++) order.push(`${s}_FINGER_${n}`)
    order.push(`${s}_ELBOW`, `${s}_SHOULDER`)
  }
  return order.filter((p) => shapes[p])
}

/** Parte "mãe": perder a mãe tira junto tudo o que vem depois dela. */
export const PARENT = {}
for (const s of ['RIGHT', 'LEFT']) {
  PARENT[`${s}_ARM`] = `${s}_SHOULDER`
  PARENT[`${s}_ELBOW`] = `${s}_ARM`
  PARENT[`${s}_FOREARM`] = `${s}_ELBOW`
  PARENT[`${s}_HAND`] = `${s}_FOREARM`
  PARENT[`${s}_KNEE`] = `${s}_THIGH`
  PARENT[`${s}_LEG`] = `${s}_KNEE`
  PARENT[`${s}_FOOT`] = `${s}_LEG`
  for (let n = 1; n <= 5; n++) {
    PARENT[`${s}_FINGER_${n}`] = `${s}_HAND`
    PARENT[`${s}_TOE_${n}`] = `${s}_FOOT`
  }
}

/** A parte some porque ela ou uma ancestral foi perdida; devolve a perdida mais alta (ou null). */
export function lostRoot(part, missing) {
  let root = null
  for (let p = part; p; p = PARENT[p]) if (missing.has(p)) root = p
  return root
}

const SIDE_LABEL = { RIGHT: 'direito', LEFT: 'esquerdo' }
const SIDE_LABEL_F = { RIGHT: 'direita', LEFT: 'esquerda' }
const FINGERS = ['Polegar', 'Indicador', 'Médio', 'Anelar', 'Mínimo']
const TOES = ['Dedão', '2º dedo', '3º dedo', '4º dedo', 'Dedo mínimo']

export const BODY_PART_LABELS = {
  HEAD: 'Cabeça', NECK: 'Pescoço', TORSO: 'Tórax', ABDOMEN: 'Abdômen', PELVIS: 'Pelve',
}
for (const s of ['RIGHT', 'LEFT']) {
  const m = SIDE_LABEL[s]
  const f = SIDE_LABEL_F[s]
  Object.assign(BODY_PART_LABELS, {
    [`${s}_SHOULDER`]: `Ombro ${m}`, [`${s}_ARM`]: `Braço ${m}`, [`${s}_ELBOW`]: `Cotovelo ${m}`,
    [`${s}_FOREARM`]: `Antebraço ${m}`, [`${s}_HAND`]: `Mão ${f}`, [`${s}_THIGH`]: `Coxa ${f}`,
    [`${s}_KNEE`]: `Joelho ${m}`, [`${s}_LEG`]: `Perna ${f}`, [`${s}_FOOT`]: `Pé ${m}`,
  })
  for (let n = 1; n <= 5; n++) {
    BODY_PART_LABELS[`${s}_FINGER_${n}`] = `${FINGERS[n - 1]} (mão ${f})`
    BODY_PART_LABELS[`${s}_TOE_${n}`] = `${TOES[n - 1]} (pé ${m})`
  }
}

/** Todas as partes (inclui dedos), na ordem do enum do servidor não importa: é só para listas. */
export const BODY_PARTS = Object.keys(BODY_PART_LABELS)

export const isDigit = (p) => /_(FINGER|TOE)_\d$/.test(p)
export const digitsOf = (p) => {
  const m = /^(RIGHT|LEFT)_(HAND|FOOT|FINGER_\d|TOE_\d)$/.exec(p)
  if (!m) return []
  const kind = m[2] === 'HAND' || m[2].startsWith('FINGER') ? 'FINGER' : 'TOE'
  return [1, 2, 3, 4, 5].map((n) => `${m[1]}_${kind}_${n}`)
}
export const limbOf = (p) => (p.includes('_FINGER_') ? p.replace(/_FINGER_\d$/, '_HAND') : p.includes('_TOE_') ? p.replace(/_TOE_\d$/, '_FOOT') : p)

/** Pontos do Ataque Localizado (capítulo de Combate) dentro das partes. */
export const LOCALIZED_POINTS = [
  { id: 'olhos', label: 'Olhos', pen: -8, part: 'HEAD', at: [100, 30] },
  { id: 'ouvido', label: 'Ouvido', pen: -6, part: 'HEAD', at: [80, 36] },
  { id: 'tempora', label: 'Têmpora', pen: -5, part: 'HEAD', at: [117, 24] },
  { id: 'queixo', label: 'Queixo', pen: -4, part: 'HEAD', at: [100, 55] },
  { id: 'garganta', label: 'Garganta', pen: -6, part: 'NECK', at: [96, 66] },
  { id: 'nuca', label: 'Nuca (por trás)', pen: -4, part: 'NECK', at: [107, 61] },
  { id: 'diafragma', label: 'Diafragma', pen: -6, part: 'TORSO', at: [100, 132] },
  { id: 'figado', label: 'Fígado', pen: -5, part: 'ABDOMEN', at: [86, 150] },
  { id: 'genitais', label: 'Genitais', pen: -6, part: 'PELVIS', at: [100, 228] },
  ...['RIGHT', 'LEFT'].flatMap((s) => [
    { id: `pulso-${s}`, label: 'Mão ou Pulso', pen: -4, part: `${s}_HAND`, at: [mx(s, 47), 212] },
    { id: `joelho-${s}`, label: 'Joelho', pen: -2, part: `${s}_KNEE`, at: at(s, 'knee') },
    { id: `coxa-${s}`, label: 'Coxa', pen: -2, part: `${s}_THIGH`, at: [mx(s, 85), 252] },
  ]),
]
