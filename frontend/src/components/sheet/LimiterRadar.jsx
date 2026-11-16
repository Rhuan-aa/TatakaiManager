// Radar de limitadores: o anel externo e 100% do limitador; quem passa sai do anel (ate 125%).
const VB = 400
const C = VB / 2
const R = 105
const MAX = 1.25

export default function LimiterRadar({ axes = [], size = 320 }) {
  const n = axes.length
  if (n < 3) return null
  const ang = (i) => -Math.PI / 2 + (2 * Math.PI * i) / n
  const pt = (i, f) => [C + Math.cos(ang(i)) * R * f, C + Math.sin(ang(i)) * R * f]
  const ring = (f) => axes.map((_, i) => pt(i, f).join(',')).join(' ')
  const frac = (a) => (a.limit > 0 ? Math.min(Math.max(a.value, 0) / a.limit, MAX) : 0)
  const over = (a) => a.limit > 0 ? a.value > a.limit : a.value > 0
  const poly = axes.map((a, i) => pt(i, frac(a)).join(',')).join(' ')
  const nOver = axes.filter(over).length
  const summary = `Radar de limitadores: ${n} eixos, ${nOver} acima do limitador`

  return (
    <div style={{ width: '100%', maxWidth: size }}>
      <svg viewBox={`0 0 ${VB} ${VB}`} role="img" aria-label={summary} style={{ width: '100%', height: 'auto', display: 'block' }}>
        {[0.25, 0.5, 0.75].map((f) => (
          <polygon key={f} points={ring(f)} fill="none" stroke="var(--line)" strokeOpacity="0.3" strokeWidth="1" />
        ))}
        {axes.map((a, i) => {
          const [x, y] = pt(i, 1)
          return <line key={a.key} x1={C} y1={C} x2={x} y2={y} stroke="var(--line)" strokeOpacity="0.3" strokeWidth="1" />
        })}
        <polygon points={ring(1)} fill="none" stroke="var(--line)" strokeWidth="4" strokeLinejoin="round" />
        <polygon points={poly} fill="var(--text)" fillOpacity="0.18" stroke="var(--text)" strokeWidth="3" strokeLinejoin="round" />
        {axes.map((a, i) => {
          if (!over(a)) return null
          const [x0, y0] = pt(i, 1)
          const [x1, y1] = pt(i, frac(a))
          return <line key={a.key} x1={x0} y1={y0} x2={x1} y2={y1} stroke="var(--accent)" strokeWidth="5" />
        })}
        {axes.map((a, i) => {
          const [x, y] = pt(i, frac(a))
          return over(a) ? (
            <polygon key={a.key} points={`${x},${y - 8} ${x + 8},${y} ${x},${y + 8} ${x - 8},${y}`} fill="var(--accent)" stroke="var(--line)" strokeWidth="2" />
          ) : (
            <circle key={a.key} cx={x} cy={y} r="5" fill="var(--text)" stroke="var(--paper)" strokeWidth="1.5" />
          )
        })}
        {axes.map((a, i) => {
          const cos = Math.cos(ang(i))
          const sin = Math.sin(ang(i))
          const lx = C + cos * (R * MAX + 14)
          const ly = C + sin * (R * MAX + 14)
          const anchor = cos > 0.3 ? 'start' : cos < -0.3 ? 'end' : 'middle'
          const dy = sin < -0.5 ? -16 : sin > 0.5 ? 12 : -2
          return (
            <text key={a.key} x={lx} y={ly + dy} textAnchor={anchor} fill={over(a) ? 'var(--accent)' : 'var(--text)'}>
              <tspan x={lx} fontSize="15" fontWeight="700" style={{ textTransform: 'uppercase' }}>
                {over(a) ? '! ' : ''}{a.label}
              </tspan>
              <tspan x={lx} dy="15" fontSize="12" opacity="0.8">{a.value}/{a.limit}</tspan>
            </text>
          )
        })}
      </svg>
      <table className="sr-only">
        <thead>
          <tr><th>Eixo</th><th>Valor</th><th>Limitador</th><th>Acima do limitador</th></tr>
        </thead>
        <tbody>
          {axes.map((a) => (
            <tr key={a.key}><td>{a.label}</td><td>{a.value}</td><td>{a.limit}</td><td>{over(a) ? 'sim' : 'não'}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
