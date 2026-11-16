import { useId, useState } from 'react';
import { uploadSheetImage, deleteSheetImage } from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';

const MAX = 5 * 1024 * 1024;
const FIELDS = [
  { kind: 'PORTRAIT', title: 'Retrato (ficha)', has: 'hasPortrait' },
  { kind: 'FULL_BODY', title: 'Corpo inteiro (carrossel)', has: 'hasFullBody' },
];

export default function SheetImagePicker({ sheet, onChange }) {
  const toast = useToast();
  const uid = useId();
  const [busy, setBusy] = useState(false);

  async function run(fn, kind, ok) {
    setBusy(true);
    try {
      await fn();
      toast(ok);
      onChange?.({ ...sheet, [FIELDS.find((f) => f.kind === kind).has]: ok.startsWith('Imagem enviada') });
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  function pick(kind, e) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) return toast('O arquivo precisa ser uma imagem.', 'error');
    if (file.size > MAX) return toast('A imagem deve ter no máximo 5 MB.', 'error');
    run(() => uploadSheetImage(sheet.id, kind, file), kind, 'Imagem enviada.');
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {FIELDS.map((f) => (
        <div key={f.kind} className="well flex flex-col gap-2 p-3">
          <label className="label" htmlFor={`${uid}-${f.kind}`}>{f.title}</label>
          <input id={`${uid}-${f.kind}`} type="file" accept="image/*" className="field" disabled={busy}
            onChange={(e) => pick(f.kind, e)} />
          <p className="hint">{sheet[f.has] ? 'Há uma imagem; enviar outra a troca.' : 'Sem imagem.'} Até 5 MB.</p>
          {sheet[f.has] && (
            <button type="button" className="btn-secondary self-start" disabled={busy}
              onClick={() => run(() => deleteSheetImage(sheet.id, f.kind), f.kind, 'Imagem removida.')}>Remover</button>
          )}
        </div>
      ))}
    </div>
  );
}
