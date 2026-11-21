import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import Icon from './Icon';
import { themeFor } from '../combat/conditionThemes';
import { queueSlot } from '../rolls/revealQueue';
import { fxReduced as reduced } from './fxPref';

function Burst({ b }) {
  const t = themeFor(b.key, b.name);
  return (
    <div className="cfx no-print" aria-hidden="true"
      style={{ left: b.left, top: b.top, '--fx-color': t.color, '--fx-shape': `var(--shape-${t.shape})`, '--fx-ms': `${b.ms}ms` }}>
      <div className={`cfx-body cfx-m-${t.motion}`}>
        <span className="cfx-word">
          <span className={t.rotate ? '-rotate-90' : ''}><Icon name={t.icon} size={26} /></span>{t.word}
        </span>
        <span className="cfx-label">{b.name}</span>
      </div>
    </div>
  );
}

/**
 * Texto de onomatopoeia quando uma condição NOVA aparece (e uma entrada curta ao montar, 1 por cartão, até 3).
 * `entries`: [{ id, anchorId, key, name }]; o texto aparece sobre [data-participant-id=anchorId] ou, sem âncora,
 * na lateral direita da tela. Entra na fila de animações (queueSlot), então não atropela o ataque nem os dados.
 */
export default function ConditionFx({ entries, intro = true }) {
  const prev = useRef(null); // null = primeira renderização
  const [bursts, setBursts] = useState([]);
  const timers = useRef([]); // só limpa ao desmontar: novas condições não cancelam textos já agendados
  useEffect(() => () => timers.current.forEach(clearTimeout), []);
  const sig = entries.map((e) => e.id).join('|');
  useEffect(() => {
    const ids = new Set(entries.map((e) => e.id));
    if (reduced()) { prev.current = ids; return undefined; }
    const first = prev.current === null;
    let list = entries.filter((e) => !(prev.current ?? new Set()).has(e.id));
    if (first) {
      const anchors = new Set();
      list = intro ? list.filter((e) => !anchors.has(e.anchorId) && anchors.add(e.anchorId)) : [];
    }
    prev.current = ids;
    const ms = first ? 700 : 1100;
    list.slice(0, 3).forEach((e) => {
      timers.current.push(setTimeout(() => {
        const el = e.anchorId && document.querySelector(`[data-participant-id="${e.anchorId}"]`);
        const r = el?.getBoundingClientRect();
        const pos = r?.width ? { left: r.left + r.width / 2, top: r.top + Math.min(r.height / 2, 60) }
          : { left: window.innerWidth - 140, top: window.innerHeight * 0.3 };
        const b = { ...e, ...pos, ms, uid: `${e.id}:${Date.now()}` };
        setBursts((x) => [...x, b]);
        timers.current.push(setTimeout(() => setBursts((x) => x.filter((y) => y.uid !== b.uid)), ms));
      }, queueSlot(ms)));
    });
    return undefined;
  }, [sig]); // eslint-disable-line react-hooks/exhaustive-deps
  return createPortal(bursts.map((b) => <Burst key={b.uid} b={b} />), document.body);
}
