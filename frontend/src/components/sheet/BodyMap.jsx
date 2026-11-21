import { useId, useMemo, useRef, useState } from 'react'
import {
  VIEW_W, VIEW_H, shapesFor, drawOrder, lostRoot, isDigit, limbOf, LOCALIZED_POINTS, BODY_PART_LABELS, BRUISE,
} from './bodyGeometry'

// Boneco de ferimentos (§53.3): cada parte tingida pela pior gravidade não tratada, marcas desenhadas
// (mancha, corte, cicatriz, furo), partes perdidas somem (com tudo o que vem depois) e os pontos do
// Ataque Localizado. Com `tool`, o Mestre desenha arrastando o ponteiro sobre uma parte.
export { BODY_PART_LABELS, BODY_PARTS, BRUISE } from './bodyGeometry'

const SEV = ['MINOR', 'MODERATE', 'SEVERE', 'LETHAL']
const SEV_LABEL = { MINOR: 'Leve', MODERATE: 'Moderado', SEVERE: 'Grave', LETHAL: 'Letal' }
const MIX = [22, 45, 72, 100]
const fillOf = (i) => `color-mix(in srgb, var(--accent) ${MIX[i]}%, var(--paper))`
const SPOT = new Set(['IMPACT', 'HOLE'])
const plural = (n) => `${n} ferimento${n === 1 ? '' : 's'}`

const toAbs = (box, [nx, ny]) => [box[0] + nx * box[2], box[1] + ny * box[3]]
const clamp01 = (v) => Math.min(1, Math.max(0, v))
const pts = (list) => list.map((p) => p.map((n) => n.toFixed(1)).join(',')).join(' ')

function Mark({ w, box, uid }) {
  const g = w.geometry
  if (w.type === 'IMPACT') {
    const [cx, cy] = toAbs(box, g.points[0])
    const r = g.r * Math.max(box[2], box[3])
    return <ellipse cx={cx} cy={cy} rx={r} ry={r * 0.82} fill={`url(#${uid}-bruise-${w.severity ?? 'MODERATE'})`} />
  }
  if (w.type === 'HOLE') {
    const [cx, cy] = toAbs(box, g.points[0])
    const r = g.r * Math.max(box[2], box[3])
    return <circle cx={cx} cy={cy} r={r + 1.2} fill="none" stroke="#3a0a0a" strokeWidth="2" />
  }
  const line = pts(g.points.map((p) => toAbs(box, p)))
  if (w.type === 'SCAR') {
    return <polyline points={line} fill="none" stroke="#f1d7cf" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" opacity="0.95" />
  }
  return (
    <g fill="none" strokeLinecap="round" strokeLinejoin="round">
      <polyline points={line} stroke="#5c0000" strokeWidth="3.2" />
      <polyline points={line} stroke="#c3122b" strokeWidth="1.3" />
    </g>
  )
}

export default function BodyMap({
  injuries = [], missingParts = [], wounds = [], onSelect, selected, tool = null, severity = 'MODERATE', onDraw, onPoint,
}) {
  const uid = useId().replace(/:/g, '')
  const svgRef = useRef(null)
  const [draft, setDraft] = useState(null)
  const missing = useMemo(() => new Set(missingParts), [missingParts])
  const interactive = typeof onSelect === 'function'

  // mãos/pés abrem em dedos só quando algum dedo tem ferimento, marca, foi perdido ou está selecionado
  const split = {}
  const touched = [...injuries.map((i) => i.bodyPart), ...wounds.map((w) => w.part), ...missingParts, selected].filter(Boolean)
  for (const p of touched) if (isDigit(p)) split[limbOf(p)] = true
  const shapes = shapesFor(split)
  const order = drawOrder(shapes)

  const info = {}
  for (const p of order) info[p] = { count: 0, worst: -1 }
  for (const inj of injuries) {
    const d = info[inj.bodyPart] ?? info[limbOf(inj.bodyPart ?? '')]
    if (!d) continue
    d.count++
    if (!inj.treated) d.worst = Math.max(d.worst, SEV.indexOf(inj.severity))
  }

  const shown = order.filter((p) => !lostRoot(p, missing))
  const ghosts = order.filter((p) => lostRoot(p, missing) === p)
  const marks = wounds.filter((w) => shapes[w.part] && !lostRoot(w.part, missing))
  const holes = marks.filter((w) => w.type === 'HOLE')

  const describe = (p) => {
    if (missing.has(p)) return `${BODY_PART_LABELS[p]}: perdido`
    const d = info[p]
    const n = wounds.filter((w) => w.part === p).length
    const extra = n ? `, ${n} marca${n === 1 ? '' : 's'}` : ''
    if (!d?.count) return `${BODY_PART_LABELS[p]}: sem ferimentos${extra}`
    const worst = d.worst >= 0 ? `, pior ${SEV_LABEL[SEV[d.worst]]}` : ', todos tratados'
    return `${BODY_PART_LABELS[p]}: ${plural(d.count)}${worst}${extra}`
  }
  const summary = `Boneco de ferimentos. ${order.filter((p) => info[p].count || missing.has(p)).map(describe).join('; ') || 'Sem ferimentos.'}`

  // ---- desenho por ponteiro
  function svgPoint(e) {
    const svg = svgRef.current
    const pt = svg.createSVGPoint()
    pt.x = e.clientX
    pt.y = e.clientY
    const p = pt.matrixTransform(svg.getScreenCTM().inverse())
    return [p.x, p.y]
  }
  function down(e, part) {
    if (!tool || !onDraw) return
    e.preventDefault()
    svgRef.current.setPointerCapture?.(e.pointerId)
    const p = svgPoint(e)
    setDraft({ part, pts: [p], r: 0 })
  }
  function move(e) {
    if (!draft) return
    const p = svgPoint(e)
    if (SPOT.has(tool)) {
      setDraft((d) => ({ ...d, r: Math.hypot(p[0] - d.pts[0][0], p[1] - d.pts[0][1]) }))
    } else {
      const last = draft.pts[draft.pts.length - 1]
      if (Math.hypot(p[0] - last[0], p[1] - last[1]) >= 1.5 && draft.pts.length < 200) {
        setDraft((d) => ({ ...d, pts: [...d.pts, p] }))
      }
    }
  }
  function up() {
    if (!draft) return
    const d = draft
    setDraft(null)
    const box = shapes[d.part].box
    const norm = (p) => [clamp01((p[0] - box[0]) / box[2]), clamp01((p[1] - box[1]) / box[3])]
    if (SPOT.has(tool)) {
      const r = Math.min(0.5, Math.max(d.r < 2 ? 6 : d.r, 1.5) / Math.max(box[2], box[3]))
      onDraw(d.part, tool, { points: [norm(d.pts[0])], r: Number(r.toFixed(3)) })
    } else if (d.pts.length >= 2) {
      onDraw(d.part, tool, { points: d.pts.map((p) => norm(p).map((n) => Number(n.toFixed(3)))), r: null })
    }
  }

  const hitProps = (p) => (interactive ? {
    tabIndex: tool ? -1 : 0, role: 'button', 'aria-label': describe(p), 'aria-pressed': selected === p,
    onClick: () => { if (!tool) onSelect(p) },
    onPointerDown: (e) => down(e, p),
    onKeyDown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onSelect(p) } },
    style: { cursor: tool ? 'crosshair' : 'pointer', outline: 'none' },
  } : {})

  return (
    <div className="body-map">
      <svg ref={svgRef} viewBox={`0 0 ${VIEW_W} ${VIEW_H}`} role={interactive ? 'group' : 'img'}
        aria-label={interactive ? 'Boneco de ferimentos' : summary}
        style={{ width: '100%', height: 'auto', display: 'block', touchAction: tool ? 'none' : 'auto' }}
        onPointerMove={move} onPointerUp={up} onPointerCancel={() => setDraft(null)}>
        <defs>
          {SEV.map((s, i) => (
            <pattern key={s} id={`${uid}-h-${s}`} width={10 - i * 2} height={10 - i * 2} patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
              <line x1="0" y1="0" x2="0" y2={10 - i * 2} stroke="var(--line)" strokeWidth={0.8 + i * 0.5} strokeOpacity="0.45" />
            </pattern>
          ))}
          {SEV.map((s) => (
            <radialGradient key={s} id={`${uid}-bruise-${s}`}>
              <stop offset="0%" stopColor={BRUISE[s]} stopOpacity="0.95" />
              <stop offset="55%" stopColor={BRUISE[s]} stopOpacity="0.7" />
              <stop offset="100%" stopColor={BRUISE[s]} stopOpacity="0" />
            </radialGradient>
          ))}
          {shown.map((p) => <clipPath key={p} id={`${uid}-c-${p}`}><path d={shapes[p].d} /></clipPath>)}
          <mask id={`${uid}-holes`} maskUnits="userSpaceOnUse" x="0" y="0" width={VIEW_W} height={VIEW_H}>
            <rect width={VIEW_W} height={VIEW_H} fill="#fff" />
            {holes.map((w) => {
              const box = shapes[w.part].box
              const [cx, cy] = toAbs(box, w.geometry.points[0])
              return <circle key={w.id} cx={cx} cy={cy} r={w.geometry.r * Math.max(box[2], box[3])} fill="#000" />
            })}
          </mask>
        </defs>

        <g mask={`url(#${uid}-holes)`}>
          {/* contorno único: tudo com traço grosso por baixo, preenchimento por cima */}
          <g fill="var(--bm-edge)" stroke="var(--bm-edge)" strokeWidth="6" strokeLinejoin="round">
            {shown.map((p) => <path key={p} d={shapes[p].d} />)}
          </g>
          <g stroke="none">
            {shown.map((p) => {
              const d = info[p]
              return <path key={p} d={shapes[p].d} fill={d.worst >= 0 ? fillOf(d.worst) : 'var(--paper)'} />
            })}
          </g>
          <g fill="none" stroke="var(--bm-edge)" strokeWidth="0.9" strokeOpacity="0.4" pointerEvents="none">
            {shown.map((p) => <path key={p} d={shapes[p].d} />)}
          </g>
          <g pointerEvents="none">
            {shown.filter((p) => info[p].worst >= 0).map((p) => (
              <path key={p} d={shapes[p].d} fill={`url(#${uid}-h-${SEV[info[p].worst]})`} />
            ))}
          </g>
          <g pointerEvents="none">
            {marks.map((w) => (
              <g key={w.id} clipPath={`url(#${uid}-c-${w.part})`}><Mark w={w} box={shapes[w.part].box} uid={uid} /></g>
            ))}
          </g>
        </g>

        {ghosts.map((p) => (
          <path key={p} d={shapes[p].d} fill="none" stroke="var(--accent)" strokeWidth="1.5" strokeDasharray="4 3" pointerEvents="none" />
        ))}

        {LOCALIZED_POINTS.filter((pt) => shapes[pt.part] && !lostRoot(pt.part, missing)).map((pt) => (
          <g key={pt.id} transform={`translate(${pt.at[0]} ${pt.at[1]})`}
            className={onPoint && !tool ? 'bm-point is-live' : 'bm-point'}
            onClick={onPoint && !tool ? () => onPoint(pt) : undefined}>
            <title>{`${pt.label} (${pt.pen})`}</title>
            <rect x="-2.1" y="-2.1" width="4.2" height="4.2" transform="rotate(45)" />
          </g>
        ))}

        {interactive && (
          <g>
            {[...shown, ...ghosts].map((p) => (
              <path key={p} d={shapes[p].d} fill="transparent" stroke="transparent"
                strokeWidth={isDigit(p) ? 2 : 8} strokeLinejoin="round" {...hitProps(p)} />
            ))}
          </g>
        )}

        {selected && shapes[selected] && (
          <path d={shapes[selected].d} fill="none" stroke="var(--highlight)" strokeWidth="3" strokeDasharray="3 2" pointerEvents="none" />
        )}

        {shown.filter((p) => info[p].count > 0 && !isDigit(p)).map((p) => {
          const [x, y, w] = shapes[p].box
          return (
            <g key={p} pointerEvents="none">
              <circle cx={x + w} cy={y + 4} r="7" fill="var(--ink)" stroke="var(--paper)" strokeWidth="1.2" />
              <text x={x + w} y={y + 7.5} textAnchor="middle" fontSize="9.5" fontWeight="700" fill="var(--paper)">{info[p].count}</text>
            </g>
          )
        })}

        {draft && (SPOT.has(tool)
          ? <circle cx={draft.pts[0][0]} cy={draft.pts[0][1]} r={Math.max(draft.r, 2)} fill={tool === 'HOLE' ? 'none' : BRUISE[severity]} fillOpacity="0.55"
              stroke="var(--highlight)" strokeWidth="1" strokeDasharray="2 2" pointerEvents="none" />
          : <polyline points={pts(draft.pts)} fill="none" stroke={tool === 'SCAR' ? '#f1d7cf' : '#c3122b'} strokeWidth="2" strokeLinecap="round" pointerEvents="none" />)}
      </svg>

      <ul className="body-map-legend hint">
        {SEV.map((s, i) => (
          <li key={s}>
            <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
              <rect x="1" y="1" width="12" height="12" fill={fillOf(i)} stroke="var(--line)" strokeWidth="1.5" />
            </svg>
            {SEV_LABEL[s]}
          </li>
        ))}
        <li>
          <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><rect x="1" y="1" width="12" height="12" fill="none" stroke="var(--accent)" strokeWidth="1.2" strokeDasharray="3 2" /></svg>Perdido
        </li>
        <li>
          <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><rect x="4" y="4" width="6" height="6" transform="rotate(45 7 7)" className="bm-point-swatch" /></svg>Ataque localizado
        </li>
      </ul>
    </div>
  )
}
