// Boneco de ferimentos: cada parte colorida pela pior gravidade nao tratada.
export const BODY_PARTS = ['HEAD', 'TORSO', 'LEFT_ARM', 'RIGHT_ARM', 'LEFT_HAND', 'RIGHT_HAND', 'LEFT_LEG', 'RIGHT_LEG', 'LEFT_FOOT', 'RIGHT_FOOT']

export const BODY_PART_LABELS = {
  HEAD: 'Cabeça', TORSO: 'Tronco',
  LEFT_ARM: 'Braço esquerdo', RIGHT_ARM: 'Braço direito',
  LEFT_HAND: 'Mão esquerda', RIGHT_HAND: 'Mão direita',
  LEFT_LEG: 'Perna esquerda', RIGHT_LEG: 'Perna direita',
  LEFT_FOOT: 'Pé esquerdo', RIGHT_FOOT: 'Pé direito',
}

const SEV = ['MINOR', 'MODERATE', 'SEVERE', 'LETHAL']
const SEV_LABEL = { MINOR: 'Leve', MODERATE: 'Moderado', SEVERE: 'Grave', LETHAL: 'Letal' }
// percentual de --accent misturado ao papel
const MIX = [22, 45, 72, 100]
const fillOf = (i) => `color-mix(in srgb, var(--accent) ${MIX[i]}%, var(--paper))`

// Formas (viewBox 200x340). "Esquerdo" do personagem = direita de quem olha (x maior).
const SHAPES = {
  HEAD: <ellipse cx="100" cy="30" rx="22" ry="26" />,
  TORSO: <path d="M68 64 H132 L126 170 H74 Z" />,
  RIGHT_ARM: <path d="M62 66 L34 70 L26 150 L44 152 L56 92 Z" />,
  LEFT_ARM: <path d="M138 66 L166 70 L174 150 L156 152 L144 92 Z" />,
  RIGHT_HAND: <path d="M24 156 H46 L48 192 H22 Z" />,
  LEFT_HAND: <path d="M154 156 H176 L178 192 H152 Z" />,
  RIGHT_LEG: <path d="M74 176 H98 L94 262 H70 Z" />,
  LEFT_LEG: <path d="M102 176 H126 L130 262 H106 Z" />,
  RIGHT_FOOT: <path d="M68 268 H94 L92 308 H56 Z" />,
  LEFT_FOOT: <path d="M106 268 H132 L144 308 H108 Z" />,
}
const CENTER = {
  HEAD: [100, 30], TORSO: [100, 118], RIGHT_ARM: [44, 108], LEFT_ARM: [156, 108], RIGHT_HAND: [36, 174], LEFT_HAND: [164, 174],
  RIGHT_LEG: [84, 220], LEFT_LEG: [116, 220], RIGHT_FOOT: [76, 290], LEFT_FOOT: [124, 290],
}

const plural = (n) => `${n} ferimento${n === 1 ? '' : 's'}`

export default function BodyMap({ injuries = [], missingParts = [], onSelect, selected }) {
  const info = {}
  for (const p of BODY_PARTS) info[p] = { count: 0, worst: -1, untreated: 0 }
  for (const inj of injuries) {
    const d = info[inj.bodyPart]
    if (!d) continue
    d.count++
    if (!inj.treated) {
      d.untreated++
      d.worst = Math.max(d.worst, SEV.indexOf(inj.severity))
    }
  }
  const missing = new Set(missingParts)
  const interactive = typeof onSelect === 'function'

  const describe = (p) => {
    const d = info[p]
    if (missing.has(p)) return `${BODY_PART_LABELS[p]}: perdido`
    if (!d.count) return `${BODY_PART_LABELS[p]}: sem ferimentos`
    const worst = d.worst >= 0 ? `, pior ${SEV_LABEL[SEV[d.worst]]}` : ', todos tratados'
    return `${BODY_PART_LABELS[p]}: ${plural(d.count)}${worst}`
  }
  const summary = `Boneco de ferimentos. ${BODY_PARTS.map(describe).join('; ')}`

  return (
    <div style={{ width: '100%', maxWidth: 260 }}>
      <svg viewBox="0 0 200 320" role={interactive ? 'group' : 'img'} aria-label={interactive ? 'Boneco de ferimentos' : summary} style={{ width: '100%', height: 'auto', display: 'block' }}>
        <defs>
          {SEV.map((s, i) => (
            <pattern key={s} id={`bm-h-${s}`} width={10 - i * 2} height={10 - i * 2} patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
              <line x1="0" y1="0" x2="0" y2={10 - i * 2} stroke="var(--line)" strokeWidth={1 + i * 0.7} strokeOpacity="0.7" />
            </pattern>
          ))}
        </defs>
        {BODY_PARTS.map((p) => {
          const d = info[p]
          const lost = missing.has(p)
          const sel = interactive && selected === p
          const hasOpen = d.worst >= 0
          const fill = lost ? 'none' : hasOpen ? fillOf(d.worst) : 'var(--paper)'
          const [cx, cy] = CENTER[p]
          const shape = SHAPES[p]
          const props = interactive ? {
            tabIndex: 0, role: 'button', 'aria-label': describe(p), 'aria-pressed': sel,
            onClick: () => onSelect(p),
            onKeyDown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onSelect(p) } },
            style: { cursor: 'pointer', outline: 'none' },
          } : {}
          return (
            <g key={p} {...props} className="bm-part">
              <g fill={fill} stroke={sel ? 'var(--highlight)' : 'var(--line)'} strokeWidth={sel ? 5 : lost || (d.count && !hasOpen) ? 1.5 : 3}
                strokeDasharray={lost ? '5 4' : d.count && !hasOpen ? '4 3' : undefined} strokeLinejoin="round">
                {shape}
              </g>
              {hasOpen && !lost && (
                <g fill={`url(#bm-h-${SEV[d.worst]})`} stroke="none" pointerEvents="none">{shape}</g>
              )}
              {lost && (
                <path d={`M${cx - 8} ${cy - 8} L${cx + 8} ${cy + 8} M${cx + 8} ${cy - 8} L${cx - 8} ${cy + 8}`} stroke="var(--accent)" strokeWidth="3" pointerEvents="none" />
              )}
              {d.count > 0 && (
                <g pointerEvents="none">
                  <circle cx={cx + 12} cy={cy - 12} r="9" fill="var(--ink)" stroke="var(--paper)" strokeWidth="1.5" />
                  <text x={cx + 12} y={cy - 8} textAnchor="middle" fontSize="12" fontWeight="700" fill="var(--paper)">{d.count}</text>
                </g>
              )}
              {sel && <g fill="none" stroke="var(--highlight)" strokeWidth="2" strokeDasharray="2 3" pointerEvents="none" style={{ transform: 'none' }}>{shape}</g>}
              {/* area de toque generosa */}
              {interactive && <g fill="transparent" stroke="transparent" strokeWidth="14" strokeLinejoin="round">{shape}</g>}
            </g>
          )
        })}
      </svg>
      <ul className="hint" style={{ display: 'flex', flexWrap: 'wrap', gap: '4px 10px', listStyle: 'none', padding: 0, margin: '6px 0 0', fontSize: 11 }}>
        {SEV.map((s, i) => (
          <li key={s} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
              <rect x="1" y="1" width="12" height="12" fill={fillOf(i)} stroke="var(--line)" strokeWidth="1.5" />
              <rect x="1" y="1" width="12" height="12" fill={`url(#bm-h-${s})`} />
            </svg>
            {SEV_LABEL[s]}
          </li>
        ))}
        <li style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><rect x="1" y="1" width="12" height="12" fill="var(--paper)" stroke="var(--line)" strokeWidth="1.2" strokeDasharray="3 2" /></svg>Tratado
        </li>
        <li style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><rect x="1" y="1" width="12" height="12" fill="none" stroke="var(--line)" strokeWidth="1.2" strokeDasharray="3 2" /><path d="M3 3 L11 11 M11 3 L3 11" stroke="var(--accent)" strokeWidth="1.5" /></svg>Perdido
        </li>
      </ul>
    </div>
  )
}
