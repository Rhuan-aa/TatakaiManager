// Sangramento na borda direita: uma gota por ferida aberta (até 6). `page` = fixo na borda da página (ficha).
export default function BleedEdge({ wounds, page = false }) {
  if (!wounds) return null;
  const n = Math.min(wounds, 6);
  return (
    <div aria-hidden="true" className={`bleed-edge no-print${page ? ' bleed-edge-page' : ''}`} style={{ '--bleed': n }}>
      {Array.from({ length: n }, (_, i) => <i key={i} style={{ '--i': i }} />)}
    </div>
  );
}
