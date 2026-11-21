import { useEffect, useState } from 'react';
import { listConsumables, listEquipment, listWeapons } from '../../api/catalog';
import { deleteCatalogItemImage, uploadCatalogItemImage } from '../../api/catalogItemImages';
import ItemImage, { useItemImageIndex } from '../ui/ItemImage';
import Icon from '../ui/Icon';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import Collapse from '../ui/Collapse';

const DAMAGE = { BLUNT: 'Contundente', SLASH: 'Cortante', PIERCE: 'Perfurante', FIRE: 'Flamejante' };
const REACH = { CURTO: 'Curto', MEDIO: 'Médio', LONGO: 'Longo', ARREMESSO: 'Arremesso', DISPARO: 'Disparo' };
const ARMOR = { LIGHT: 'Leve', MEDIUM: 'Média', HEAVY: 'Pesada' };
const REGION = { CABECA: 'Cabeça', TRONCO: 'Tronco', BRACOS: 'Braços', PERNAS: 'Pernas' };
const USE ={ SELF: 'Em si', ALLY: 'Em aliado', OFFENSIVE: 'Ofensivo' };

const KINDS = {
  weapon: {
    label: 'Armas', img: 'WEAPON', path: 'weapons', icon: 'swords', load: listWeapons, groupBy: 'group',
    groups: { MELEE: 'Corpo a corpo', ACADEMY: 'De academia', IMPROVISED: 'Improvisadas', THROWN: 'De arremesso', RANGED: 'De disparo', FIREARM: 'De fogo', AUGMENT: 'Implementos' },
  },
  consumable: {
    label: 'Consumíveis', img: 'CONSUMABLE', path: 'consumables', icon: 'bandage', load: listConsumables, groupBy: 'group',
    groups: { MEDICAL: 'Médicos', STIMULANT: 'Estimulantes', SUPPLEMENT: 'Suplementos', FOOD: 'Comida', TACTICAL: 'Táticos', BOMB: 'Bombas' },
  },
  equipment: {
    label: 'Equipamentos', img: 'EQUIPMENT', path: 'equipment', icon: 'circle', load: listEquipment, groupBy: 'kind',
    groups: { PROTECTION: 'Proteções', COMPETITION: 'De competição', ACADEMY: 'De academia', CLOTHING: 'Roupas', TOOL: 'Ferramentas' },
  },
};

const WEAPON_FLAGS = [
  ['light', 'Leve'], ['twoHands', 'Duas mãos'], ['large', 'Grande'], ['compact', 'Compacta'], ['small', 'Pequena'],
  ['defensive', 'Defensiva'], ['flexible', 'Flexível'], ['fragile', 'Frágil'], ['entangle', 'Enreda'], ['returning', 'Retorna'],
  ['dispersion', 'Dispersão'], ['incendiary', 'Incendiária'], ['smoke', 'Fumaça'], ['slowCock', 'Engatilhar lento'],
  ['throwOnly', 'Só arremesso'], ['selfWound', 'Fere o usuário'], ['hidden', 'Oculta'],
];

const tag = (text) => <span key={text} className="tag">{text}</span>;

function weaponTags(w) {
  return [
    w.dice && tag(w.diceTwoHands ? `${w.dice} / ${w.diceTwoHands} (2 mãos)` : w.dice),
    w.damageType && tag(DAMAGE[w.damageType] ?? w.damageType),
    tag(REACH[w.reach] ?? w.reach),
    w.weaponClass && tag(w.weaponClass),
  ];
}

function weaponDetails(w) {
  const props = WEAPON_FLAGS.filter(([k]) => w[k]).map(([, l]) => l);
  if (w.impact) props.push(`Impacto ${w.impact}`);
  if (w.heavyStr) props.push(`Pesada (FOR ${w.heavyStr})`);
  if (w.rangeM) props.push(`Alcance ${w.rangeM} m`);
  if (w.throwM) props.push(`Arremesso ${w.throwM} m`);
  if (w.capacity) props.push(`Capacidade ${w.capacity}`);
  if (w.reload) props.push(`Recarga ${w.reload}`);
  if (w.burst) props.push(`Rajada ${w.burst}`);
  if (w.unstable) props.push(`Instável ${w.unstable}`);
  if (w.explosiveRadius) props.push(`Explosão ${w.explosiveRadius} m`);
  if (w.bonus) props.push(`Bônus ${w.bonus}`);
  return props.length > 0 ? props.join(' · ') : null;
}

function equipmentTags(e) {
  return [
    e.category && tag(ARMOR[e.category] ?? e.category),
    e.weightKg != null && tag(`${e.weightKg} kg`),
  ];
}

function equipmentDetails(e) {
  const lines = [];
  if (e.protection) {
    lines.push('Redução: ' + Object.entries(e.protection).map(([t, v]) => `${DAMAGE[t] ?? t} ${v}`).join(', '));
  }
  if (e.cover?.length) lines.push('Cobre: ' + e.cover.map((r) => REGION[r] ?? r).join(', '));
  if (e.text) lines.push(e.text);
  return lines.length > 0 ? lines.join(' · ') : null;
}

const consumableTags = (c) => [USE[c.use] && tag(USE[c.use]), c.dose && tag('Dose')];

const VIEW = {
  weapon: [weaponTags, weaponDetails],
  consumable: [consumableTags, (c) => c.text],
  equipment: [equipmentTags, equipmentDetails],
};

const SCOPE_LABEL = { CAMPAIGN: 'Imagem da campanha', GLOBAL: 'Imagem do sistema' };

/** Enviar/remover a imagem do item. target: 'campaign' (Mestre) ou 'global' (editor do sistema). */
function ImageControls({ item, kind, campaignId, targets, scope, onChanged }) {
  const toast = useToast();
  const [target, setTarget] = useState(targets[0]);
  const [busy, setBusy] = useState(false);
  const cid = target === 'campaign' ? campaignId : undefined;
  // só remove o que existe no escopo escolhido (na campanha, a global aparece por fallback)
  const canRemove = scope && (target === 'global' ? scope === 'GLOBAL' || !campaignId : scope === 'CAMPAIGN');

  async function run(fn, ok) {
    setBusy(true);
    try {
      await fn();
      toast(ok);
      onChanged();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="well flex flex-wrap items-end gap-2">
      {targets.length > 1 && (
        <label className="label">
          Onde
          <select className="field mt-1" value={target} onChange={(e) => setTarget(e.target.value)}>
            <option value="campaign">Só nesta campanha</option>
            <option value="global">Sistema (todas)</option>
          </select>
        </label>
      )}
      <label className="label">
        {scope ? 'Trocar imagem' : 'Enviar imagem'}
        <input type="file" accept="image/*" className="field mt-1" disabled={busy}
          onChange={(e) => {
            const file = e.target.files?.[0];
            e.target.value = '';
            if (!file) return;
            if (file.size > 5 * 1024 * 1024) { toast('Imagem acima de 5 MB.', 'error'); return; }
            run(() => uploadCatalogItemImage(KINDS[kind].path, item.key, file, cid), 'Imagem enviada.');
          }} />
      </label>
      {canRemove && (
        <button type="button" className="btn-secondary" disabled={busy}
          onClick={() => run(() => deleteCatalogItemImage(KINDS[kind].path, item.key, cid), 'Imagem removida.')}>
          Remover imagem
        </button>
      )}
      <p className="hint w-full">Opcional. Até 5 MB; sem imagem, o inventário usa o ícone do tipo.</p>
    </div>
  );
}

function Row({ item, kind, campaignId, images, targets }) {
  const [open, setOpen] = useState(false);
  const [tags, details] = VIEW[kind];
  const { img, icon } = KINDS[kind];
  const scope = images.scopeOf(img, item.key);
  const pic = (thumb, cls) => (
    <ItemImage kind={img} itemKey={item.key} campaignId={campaignId} has={!!scope} rev={images.rev} thumb={thumb}
      className={cls} fallback={thumb ? <Icon name={icon} size={22} /> : null} />
  );
  return (
    <li className="card-round flex flex-col gap-1 text-sm">
      <button type="button" className="flex flex-wrap items-center gap-2 text-left" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <span className="item-thumb" aria-hidden="true">{pic(true, '')}</span>
        <strong className="panel-title flex-1 text-base">{item.name}</strong>
        {tags(item)}
        <span aria-hidden="true" className="text-xs">{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        <div className="flex flex-col gap-2">
          {scope && (
            <figure className="flex flex-col gap-1">
              <span className="item-full">{pic(false, '')}</span>
              <figcaption className="text-fine">{SCOPE_LABEL[scope]}</figcaption>
            </figure>
          )}
          {details(item) && <p>{details(item)}</p>}
          <p className="text-fine">Preço {item.price} · Acesso {item.access}</p>
          {targets.length > 0 && (
            <ImageControls item={item} kind={kind} campaignId={campaignId} targets={targets} scope={scope} onChanged={images.reload} />
          )}
        </div>
      )}
    </li>
  );
}

/**
 * Armas, consumíveis e equipamentos — catálogos estáticos do sistema (§42–44), só leitura.
 * Imagem opcional por item: Mestre na campanha, editores do sistema na global (§53.3).
 */
export default function ItemsCatalog({ campaignId, isMaster, globalEditor }) {
  const toast = useToast();
  const images = useItemImageIndex(campaignId);
  const targets = [isMaster && campaignId && 'campaign', globalEditor && 'global'].filter(Boolean);
  const [kind, setKind] = useState('weapon');
  const [data, setData] = useState({});
  const [q, setQ] = useState('');

  useEffect(() => {
    if (data[kind]) return undefined;
    let active = true;
    KINDS[kind].load()
      .then((d) => active && setData((prev) => ({ ...prev, [kind]: d })))
      .catch((err) => active && toast(parseApiError(err).message, 'error'));
    return () => { active = false; };
  }, [kind, data, toast]);

  const { groups, groupBy } = KINDS[kind];
  const term = q.trim().toLowerCase();
  const items = (data[kind] ?? []).filter((i) => !term || i.name.toLowerCase().includes(term));

  return (
    <div className="flex flex-col gap-4">
      <div className="panel p-3 flex flex-wrap gap-3 items-end">
        <div role="tablist" aria-label="Tipo de item" className="flex gap-2">
          {Object.entries(KINDS).map(([k, { label }]) => (
            <button key={k} role="tab" aria-selected={kind === k} className={kind === k ? 'btn-primary' : 'btn-secondary'}
              onClick={() => setKind(k)}>{label}</button>
          ))}
        </div>
        <label className="flex flex-col text-xs uppercase gap-1 ml-auto" role="search">Buscar
          <input className="field" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Nome…" />
        </label>
      </div>

      {!data[kind] ? <div className="skeleton h-24 w-full" /> : items.length === 0 ? <p className="text-fine">Nenhum item encontrado.</p> : (
        Object.entries(groups).map(([g, title]) => {
          const list = items.filter((i) => i[groupBy] === g);
          return list.length > 0 && (
            <Collapse key={g} title={<>{title}<span className="text-fine ml-2 font-sans normal-case opacity-70">{list.length}</span></>}>
              <ul className="flex flex-col gap-2">
                {list.map((i) => <Row key={i.key} item={i} kind={kind} campaignId={campaignId} images={images} targets={targets} />)}
              </ul>
            </Collapse>
          );
        })
      )}
    </div>
  );
}
