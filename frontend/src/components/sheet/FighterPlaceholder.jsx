/** Silhueta de lutador em guarda, sem rosto. Preenche com currentColor. */
export default function FighterPlaceholder({ className = '' }) {
  return (
    <svg viewBox="0 0 120 200" className={className} fill="currentColor" stroke="var(--ink)" strokeWidth="3"
      strokeLinejoin="round" strokeLinecap="round" role="img" aria-label="Sem imagem do personagem">
      <circle cx="60" cy="24" r="14" />
      <path d="M44 44h32l8 40-6 20H42l-6-20Z" />
      <path d="M40 48 22 70l10 22 14-16Zm40 0 20 14-4 22-16 6Z" />
      <path d="M44 104h32l10 44-14 4-8-30-6 30 6 40H46l-8-4 10-38-12-8Z" />
      <path d="M76 104l14 36 18 24-12 6-26-28Z" />
    </svg>
  );
}
