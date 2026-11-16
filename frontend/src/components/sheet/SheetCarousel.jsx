import { useEffect, useMemo, useRef, useState } from 'react';
import { getSheetImageUrl } from '../../api/characterSheets';
import FighterPlaceholder from './FighterPlaceholder';

const STATUS = { DRAFT: 'Rascunho', PENDING_APPROVAL: 'Aguardando aprovação', COMPLETED: 'Aprovada', IN_BUILD: 'Em construção' };

const Lock = () => (
  <svg viewBox="0 0 24 24" width="100%" height="100%" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <rect x="5" y="11" width="14" height="10" /><path d="M8 11V7a4 4 0 0 1 8 0v4" />
  </svg>
);
const Arrow = ({ dir }) => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d={dir < 0 ? 'M15 5l-7 7 7 7' : 'M9 5l7 7-7 7'} />
  </svg>
);
const pad = (n) => String(n).padStart(2, '0');

/** Seleção de fichas no estilo "seleção de personagem": arte grande ao centro, nome carimbado à direita, recortes inclinados embaixo. */
export default function SheetCarousel({ sheets, userId, isMaster, onOpen }) {
  const [sel, setSel] = useState(() => Math.max(0, sheets.findIndex((s) => s.ownerUserId === userId)));
  const [urls, setUrls] = useState({});
  const cache = useRef(new Map());
  const rail = useRef(null);
  const idx = Math.min(sel, sheets.length - 1);
  const cur = sheets[idx];
  const locked = (s) => !isMaster && s.ownerUserId !== userId;

  // carrega só quem tem corpo inteiro, uma vez por ficha
  const keys = useMemo(() => sheets.filter((s) => s.hasFullBody).map((s) => s.id).join(','), [sheets]);
  useEffect(() => {
    let active = true;
    keys.split(',').filter(Boolean).forEach((id) => {
      if (cache.current.has(id)) return;
      cache.current.set(id, null);
      getSheetImageUrl(id, 'FULL_BODY')
        .then((u) => {
          if (!active) { URL.revokeObjectURL(u); cache.current.delete(id); return; }
          cache.current.set(id, u);
          setUrls((p) => ({ ...p, [id]: u }));
        })
        .catch(() => cache.current.delete(id));
    });
    return () => { active = false; };
  }, [keys]);
  useEffect(() => {
    const c = cache.current;
    return () => { c.forEach((u) => u && URL.revokeObjectURL(u)); c.clear(); };
  }, []);

  // centraliza o recorte no trilho sem rolar a página
  function center(n, behavior) {
    const r = rail.current;
    const el = r?.children[n];
    if (!el) return;
    const left = el.getBoundingClientRect().left - r.getBoundingClientRect().left + r.scrollLeft;
    r.scrollTo({ left: left - (r.clientWidth - el.offsetWidth) / 2, behavior });
  }
  useEffect(() => { center(idx, 'auto'); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function go(i) {
    const n = Math.max(0, Math.min(sheets.length - 1, i));
    setSel(n);
    center(n, 'smooth');
  }
  function onKey(e) {
    if (e.key === 'ArrowRight') { e.preventDefault(); go(idx + 1); }
    if (e.key === 'ArrowLeft') { e.preventDefault(); go(idx - 1); }
  }
  if (!cur) return null;
  const lk = locked(cur);
  const img = (s) => urls[s.id];
  const next = sheets[idx + 1];
  // o nome encolhe pela maior palavra para caber na coluna
  const chars = Math.max(4, ...cur.name.split(/\s+/).map((w) => w.length));
  const vitals = cur.effectiveHpMax == null ? [] : [
    ['HP', cur.currentHp, cur.effectiveHpMax], ['SP', cur.currentSp, cur.spMax], ['PQ', cur.currentKp, cur.kpMax],
  ];

  return (
    <section className={`sc${lk ? ' is-locked' : ''}`} aria-label="Fichas da campanha">
      <div className="sc-burst" aria-hidden="true" />
      <div className="sc-art" key={cur.id}>
        {img(cur) ? <img src={img(cur)} alt="" /> : <FighterPlaceholder className="sc-ph" />}
      </div>

      <div className="sc-info" aria-live="polite">
        <p className="sc-eyebrow">Lutadores <strong>— {pad(idx + 1)} / {pad(sheets.length)}</strong></p>
        <h3 className="sc-name" style={{ '--chars': chars }}>{cur.name}</h3>
        {vitals.length > 0 && (
          <ul className="sc-chips" aria-label="Recursos">
            {vitals.map(([k, v, m]) => (
              <li key={k} className="sc-chip"><b>{k}</b><span>{v}/{m}</span></li>
            ))}
          </ul>
        )}
        <p className="sc-owner">Jogador · {cur.ownerName}</p>
        <div className="sc-tags">
          <span className="tag">{STATUS[cur.buildStatus]}</span>
          {cur.criticalState && <span className="tag-accent tag">Crítico</span>}
          {lk && <span className="tag">Bloqueada</span>}
        </div>
      </div>

      <ul className="sc-rail" ref={rail} onKeyDown={onKey}>
        {sheets.map((s, i) => {
          const l = locked(s);
          const on = i === idx;
          return (
            <li key={s.id} className={on ? 'is-sel' : undefined}>
              <button type="button" onClick={() => go(i)} aria-current={on ? 'true' : undefined}
                aria-label={`${s.name} — ${s.ownerName}${l ? ' (bloqueada)' : ''}`}
                className={`sc-card${l ? ' is-locked' : ''}`}>
                <span className="sc-thumb">
                  {img(s) ? <img src={img(s)} alt="" /> : <FighterPlaceholder className="sc-ph" />}
                </span>
                {l && <span className="sc-lock" aria-hidden="true"><Lock /></span>}
                <span className="sc-cap">{s.name}</span>
              </button>
              {on && (
                <button type="button" className="sc-open" onClick={() => onOpen(s.id)}>
                  {l ? 'Ver ficha' : 'Abrir ficha'}
                </button>
              )}
            </li>
          );
        })}
      </ul>
      {lk && <p className="sc-note">Somente leitura: você vê apenas as seções liberadas.</p>}

      <div className="sc-nav">
        <div className="sc-arrows">
          <button type="button" className="sc-arrow" aria-label="Ficha anterior" disabled={idx === 0} onClick={() => go(idx - 1)}><Arrow dir={-1} /></button>
          <button type="button" className="sc-arrow sc-arrow-next" aria-label="Próxima ficha" disabled={!next} onClick={() => go(idx + 1)}><Arrow dir={1} /></button>
        </div>
        {next && (
          <div className="sc-next" aria-hidden="true">
            <span className="sc-thumb">
              {img(next) ? <img src={img(next)} alt="" /> : <FighterPlaceholder className="sc-ph" />}
            </span>
            <span className="sc-next-cap">A seguir · {next.name}</span>
          </div>
        )}
      </div>
    </section>
  );
}
