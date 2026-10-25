import { queueSlot } from '../rolls/revealQueue';

// Animação de um ataque resolvido sobre os cartões do rastreador ([data-participant-id]), depois das rolagens.
const FX_MS = 900;
const WORD = { HIT: 'POW!', MISS: 'ERROU!', DODGED: 'ESQUIVOU!', DEFENDED: 'BLOQUEIO!', COUNTERED: 'CONTRA!' };
const TARGET_FRAMES = {
  HIT: [{ transform: 'none' }, { transform: 'translateX(-10px) rotate(-1deg)' }, { transform: 'translateX(10px) rotate(1deg)' },
    { transform: 'translateX(-6px)' }, { transform: 'none' }],
  DODGED: [{ transform: 'none' }, { transform: 'translateX(40px) skewX(-8deg)', opacity: 0.6 }, { transform: 'none' }],
  DEFENDED: [{ transform: 'none' }, { transform: 'scale(0.97)' }, { transform: 'none' }],
};

const card = (id) => document.querySelector(`[data-participant-id="${id}"]`);

function burst(el, text, accent) {
  const r = el.getBoundingClientRect();
  if (!r.width) return;
  const s = document.createElement('span');
  s.className = 'onomatopoeia attack-fx';
  s.textContent = text;
  s.style.left = `${r.left + r.width / 2}px`;
  s.style.top = `${r.top + Math.min(r.height / 2, 60)}px`;
  if (accent) s.style.color = 'var(--accent)';
  document.body.appendChild(s);
  s.animate([{ opacity: 1 }, { opacity: 1, offset: 0.7 }, { opacity: 0 }], { duration: FX_MS }).onfinish = () => s.remove();
}

export function playAttack(a) {
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
  setTimeout(() => {
    const att = card(a.attackerId);
    const first = a.targets[0] && card(a.targets[0].targetId);
    if (att?.animate && first) {
      // investida: o cartão do atacante avança na direção do primeiro alvo
      const ar = att.getBoundingClientRect();
      const tr = first.getBoundingClientRect();
      const dx = Math.sign(tr.left - ar.left) * 24;
      const dy = Math.sign(tr.top - ar.top) * 24;
      att.animate([{ transform: 'none' }, { transform: `translate(${dx}px, ${dy}px) scale(1.02)` }, { transform: 'none' }],
        { duration: 320, easing: 'ease-in-out' });
    }
    a.targets.forEach((t) => {
      const el = card(t.targetId);
      if (!el?.animate) return;
      setTimeout(() => {
        if (TARGET_FRAMES[t.outcome]) el.animate(TARGET_FRAMES[t.outcome], { duration: 380 });
        if (t.outcome === 'COUNTERED' && att) att.animate(TARGET_FRAMES.HIT, { duration: 380 });
        if (WORD[t.outcome]) burst(el, t.damage > 0 ? `${WORD[t.outcome]} −${t.damage}` : WORD[t.outcome], t.outcome === 'HIT');
      }, 200);
    });
  }, queueSlot(FX_MS));
}
