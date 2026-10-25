import { useEffect, useState } from 'react';
import { getSheetVisibility, setSheetVisibility } from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';

// Seções que a ficha digital respeita (as demais do enum são das fichas textuais antigas).
const SECTIONS = [
  { section: 'ATTRIBUTES', label: 'Atributos' },
  { section: 'RESOURCES', label: 'Recursos' },
  { section: 'INJURIES', label: 'Ferimentos' },
  { section: 'INVENTORY', label: 'Inventário' },
];

/**
 * O que os outros jogadores veem desta ficha. O Mestre oculta "pelo Mestre"; o dono oculta "por mim".
 * A seção some para os outros se qualquer um dos dois ocultar.
 */
export default function SheetVisibilityPanel({ campaignId, ownerUserId, isMaster }) {
  const toast = useToast();
  const [rows, setRows] = useState(null);

  useEffect(() => {
    let active = true;
    getSheetVisibility(campaignId, ownerUserId)
      .then((list) => active && setRows(Object.fromEntries(list.map((x) => [x.section, x]))))
      .catch((err) => active && toast(parseApiError(err).message, 'error'));
    return () => { active = false; };
  }, [campaignId, ownerUserId, toast]);

  async function toggle(section, hidden) {
    try {
      const r = await setSheetVisibility(campaignId, ownerUserId, section, hidden);
      setRows((p) => ({ ...p, [section]: r }));
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    }
  }

  if (!rows) return <div className="skeleton h-12 w-full" />;
  const mine = isMaster ? 'hiddenByMaster' : 'hiddenBySelf';
  const other = isMaster ? 'hiddenBySelf' : 'hiddenByMaster';
  return (
    <div className="flex flex-col gap-2">
      <p className="caption">
        {isMaster ? 'Oculte seções desta ficha dos outros jogadores.' : 'Oculte seções da sua ficha dos outros jogadores.'}
        {' '}Você e o {isMaster ? 'dono' : 'Mestre'} sempre veem tudo.
      </p>
      <div className="grid gap-2 sm:grid-cols-2">
        {SECTIONS.map(({ section, label }) => {
          const r = rows[section] ?? {};
          return (
            <label key={section} className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={!!r[mine]} onChange={(e) => toggle(section, e.target.checked)} />
              Ocultar {label}
              {r[other] && <span className="tag">oculta pelo {isMaster ? 'dono' : 'Mestre'}</span>}
            </label>
          );
        })}
      </div>
    </div>
  );
}
