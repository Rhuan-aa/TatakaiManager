import { useRef } from 'react';

/** Abas WAI-ARIA. tabs: [{ id, label }]; o painel é renderizado pelo pai com id `${idBase}-panel`. */
export default function SheetTabs({ tabs, active, onChange, idBase = 'sheet' }) {
  const refs = useRef({});
  function onKeyDown(e) {
    const i = tabs.findIndex((t) => t.id === active);
    let n = null;
    if (e.key === 'ArrowRight') n = (i + 1) % tabs.length;
    else if (e.key === 'ArrowLeft') n = (i - 1 + tabs.length) % tabs.length;
    else if (e.key === 'Home') n = 0;
    else if (e.key === 'End') n = tabs.length - 1;
    if (n == null) return;
    e.preventDefault();
    onChange(tabs[n].id);
    refs.current[tabs[n].id]?.focus();
  }
  return (
    <div className="sheet-tabs no-print" role="tablist" aria-label="Seções da ficha" onKeyDown={onKeyDown}>
      {tabs.map((t) => (
        <button key={t.id} type="button" role="tab" id={`${idBase}-tab-${t.id}`} ref={(el) => { refs.current[t.id] = el; }}
          aria-selected={t.id === active} aria-controls={`${idBase}-panel`} tabIndex={t.id === active ? 0 : -1}
          className="sheet-tab" onClick={() => onChange(t.id)}>
          {t.label}
        </button>
      ))}
    </div>
  );
}
