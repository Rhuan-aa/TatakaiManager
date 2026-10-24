import { useEffect, useState } from 'react';
import { listInstincts, listMilestones } from '../../api/progression';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { KEIKEN, KEIKEN_FLAVOR } from '../sheet/sheetRules';
import Collapse from '../ui/Collapse';

const CATEGORY = { PRESSURE: 'Marcos de Pressão', DISADVANTAGE: 'Marcos de Desvantagem' };

function Item({ name, tags, description }) {
  const [open, setOpen] = useState(false);
  return (
    <li className="card-round flex flex-col gap-1 text-sm">
      <button type="button" className="flex flex-wrap items-center gap-2 text-left" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <strong className="panel-title flex-1 text-base">{name}</strong>
        {tags}
        <span aria-hidden="true" className="text-xs">{open ? '▲' : '▼'}</span>
      </button>
      {open && description && <p className="text-sm">{description}</p>}
    </li>
  );
}

/** Instintos (por patente) e Marcos (por categoria) do catálogo — edição por importação. */
export default function ProgressionCatalog({ campaignId, kind, refreshKey }) {
  const toast = useToast();
  const [items, setItems] = useState(null);

  useEffect(() => {
    let active = true;
    (kind === 'instinct' ? listInstincts : listMilestones)(campaignId)
      .then((d) => active && setItems(d))
      .catch((err) => active && toast(parseApiError(err).message, 'error'));
    return () => { active = false; };
  }, [campaignId, kind, refreshKey, toast]);

  if (!items) return <div className="skeleton h-24 w-full" />;
  if (items.length === 0) return <p className="text-fine">Nada no catálogo — use Importar.</p>;
  const groups = kind === 'instinct'
    ? Object.keys(KEIKEN).map((k) => [k, KEIKEN[k], KEIKEN_FLAVOR[k], items.filter((i) => i.rank === k)])
    : Object.keys(CATEGORY).map((k) => [k, CATEGORY[k], null, items.filter((i) => i.category === k)]);
  const scopeTag = (i) => i.campaignId && <span className="tag">Campanha</span>;
  return (
    <div className="flex flex-col gap-4">
      {groups.filter(([, , , list]) => list.length > 0).map(([k, title, flavor, list]) => (
        <Collapse key={k} title={<>{title}{flavor && <span className="text-fine ml-2 font-sans normal-case opacity-70">{flavor}</span>}</>}>
          <ul className="flex flex-col gap-2">
            {list.map((i) => (
              <Item key={i.id} name={i.name} description={i.description} tags={<>
                {scopeTag(i)}
                {i.autoTrigger && <span className="tag">Sugerido pela ficha</span>}
                {i.seriousOnly && <span className="tag">Só Real/Letal</span>}
                {i.minOtherDisadvantage && <span className="tag">Exige {i.minOtherDisadvantage} outros</span>}
                {i.grantsArtPoint && <span className="tag">+1 ponto à arte</span>}
              </>} />
            ))}
          </ul>
        </Collapse>
      ))}
    </div>
  );
}
