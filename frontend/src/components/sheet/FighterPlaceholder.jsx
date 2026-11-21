import { circle, taper } from './bodyGeometry';

// Lutador em guarda, sem rosto (viewBox 120x200), feito dos mesmos segmentos do boneco de ferimentos.
// Contorno único: tudo com traço grosso por baixo, preenchimento (currentColor) por cima.
const PARTS = [
  circle([62, 24], 13),
  { d: 'M54 34 L70 34 L72 46 L52 46 Z' },
  { d: 'M42 48 C50 42 74 42 82 48 C84 66 80 86 76 104 L48 104 C44 86 40 66 42 48 Z' },
  { d: 'M47 102 L77 102 C80 110 80 116 76 120 L48 120 C44 116 44 110 47 102 Z' },
  taper([46, 52], [32, 76], 7, 5.5), taper([32, 76], [50, 60], 5.5, 4.5), circle([52, 57], 6.5),
  taper([78, 52], [92, 72], 7, 5.5), taper([92, 72], [80, 46], 5.5, 4.5), circle([79, 43], 6.5),
  taper([52, 116], [40, 150], 9, 6.5), taper([40, 150], [30, 186], 6.5, 4.5), taper([30, 186], [18, 192], 4.5, 4.5),
  taper([72, 116], [86, 148], 9, 6.5), taper([86, 148], [94, 186], 6.5, 4.5), taper([94, 186], [106, 190], 4.5, 4.5),
];

/** Silhueta de lutador em guarda, sem rosto. Preenche com currentColor. */
export default function FighterPlaceholder({ className = '' }) {
  return (
    <svg viewBox="0 0 120 200" className={className} role="img" aria-label="Sem imagem do personagem">
      <g fill="var(--ink)" stroke="var(--ink)" strokeWidth="5" strokeLinejoin="round">
        {PARTS.map((p, i) => <path key={i} d={p.d} />)}
      </g>
      <g fill="currentColor">
        {PARTS.map((p, i) => <path key={i} d={p.d} />)}
      </g>
    </svg>
  );
}
