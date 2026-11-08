import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import Icon from '../ui/Icon';

/**
 * Menu de configuração de uma ação (modal no quadro da tela de reação, por cima do pop-up de ações, z 58).
 * Esc fecha só este menu (captura antes do ActionOverlay); o foco vai para o cartão e volta ao fechar.
 * Portal em document.body: o ancestral `.reaction-card` anima com transform e quebraria o `position: fixed`.
 * `reason` desabilita o Confirmar e aparece embaixo; `color` é a cor do grupo da ação (--act-*).
 */
export default function ActionMenu({ title, color = 'var(--act-minor)', confirmLabel = 'Confirmar', reason, onConfirm, onCancel, children }) {
  const id = useId();
  const card = useRef(null);
  const cancel = useRef(onCancel);
  cancel.current = onCancel; // onCancel inline não pode refazer o efeito (roubaria o foco do campo)
  useEffect(() => {
    const prev = document.activeElement;
    card.current?.focus();
    const onKey = (e) => { if (e.key === 'Escape') { e.stopPropagation(); cancel.current(); } };
    window.addEventListener('keydown', onKey, true);
    return () => { window.removeEventListener('keydown', onKey, true); prev?.focus?.(); };
  }, []);

  return createPortal(
    <div className="reaction-overlay" style={{ zIndex: 58 }} role="dialog" aria-modal="true" aria-labelledby={id}>
      <div className="relative w-full max-w-md">
        <div className="reaction-card" ref={card} tabIndex={-1} style={{ '--act': color }}>
          <form onSubmit={(e) => { e.preventDefault(); if (!reason) onConfirm(); }}>
            <h2 id={id} className="act-group-title panel-title mb-3 text-2xl">{title}</h2>
            <div className="space-y-3">{children}</div>
            {reason && <p className="mt-3 text-xs font-bold">{reason}</p>}
            <div className="mt-4 flex flex-wrap items-center justify-end gap-2">
              <button type="button" className="btn-ghost text-xs" onClick={onCancel}>Cancelar (Esc)</button>
              <button type="submit" className="btn-primary flex items-center gap-2" disabled={!!reason}>
                <Icon name="check" size={16} />{confirmLabel}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>,
    document.body,
  );
}

/** Opção selecionável da lista de um menu (visual do `.reaction-option`); `selected` destaca em amarelo. */
export function OptionButton({ selected, disabled, title, detail, tag, onClick }) {
  return (
    <button type="button" className="reaction-option w-full" disabled={disabled} aria-pressed={!!selected} onClick={onClick}
      style={selected ? { borderColor: 'var(--highlight)', boxShadow: '4px 4px 0 var(--highlight)',
        background: 'color-mix(in srgb, var(--highlight) 22%, var(--panel-bg))' } : undefined}>
      <span className="flex items-center justify-between gap-2">
        <span className="panel-title text-lg">{title}</span>
        {tag && <span className="tag">{tag}</span>}
      </span>
      {detail && <span className="text-xs">{detail}</span>}
    </button>
  );
}
