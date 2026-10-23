import { useEffect, useState } from 'react';
import { fetchNpcImageUrl } from '../../api/npcs';

/**
 * Seletor de imagem do NPC. Reporta ao pai via onChange({ file, remove }):
 *  - file: novo arquivo selecionado (ou null)
 *  - remove: true se o usuário pediu para remover a imagem existente
 * O upload/remoção efetivos ficam a cargo do formulário no submit.
 */
export default function NpcImagePicker({ campaignId, npcId, hasImage, onChange }) {
  const [file, setFile] = useState(null);
  const [remove, setRemove] = useState(false);
  const [existingUrl, setExistingUrl] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);

  // Carrega a imagem atual (quando houver e nenhuma ação pendente)
  useEffect(() => {
    if (!hasImage || !npcId || !campaignId) return undefined;
    let url;
    let active = true;
    fetchNpcImageUrl(campaignId, npcId)
      .then((u) => {
        url = u;
        if (active) setExistingUrl(u);
        else URL.revokeObjectURL(u);
      })
      .catch(() => {});
    return () => {
      active = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [campaignId, npcId, hasImage]);

  function selectFile(f) {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    if (f) {
      setFile(f);
      setRemove(false);
      setPreviewUrl(URL.createObjectURL(f));
      onChange({ file: f, remove: false });
    } else {
      setFile(null);
      setPreviewUrl(null);
      onChange({ file: null, remove });
    }
  }

  function requestRemove() {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setFile(null);
    setPreviewUrl(null);
    setRemove(true);
    onChange({ file: null, remove: true });
  }

  const shownUrl = previewUrl || (remove ? null : existingUrl);

  return (
    <div>
      <span className="text-sm font-medium text-[var(--text)] opacity-70">
        Imagem <span className="text-[var(--text)] opacity-70">(opcional)</span>
      </span>
      {shownUrl && (
        <div className="mt-2 w-32 panel overflow-hidden">
          {/* Mesma proporção 3:4 do card e do modal — uma imagem por NPC. */}
          <div className="aspect-[3/4]">
            <img
              src={shownUrl}
              alt="Retrato do NPC"
              className="h-full w-full object-cover object-top"
            />
          </div>
        </div>
      )}
      <div className="mt-2 flex flex-wrap items-center gap-3">
        <label className="btn-secondary cursor-pointer text-sm">
          {shownUrl ? 'Trocar imagem' : 'Selecionar imagem'}
          <input
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(e) => selectFile(e.target.files?.[0] ?? null)}
          />
        </label>
        {(existingUrl || previewUrl) && !remove && (
          <button
            type="button"
            onClick={requestRemove}
            className="btn-ghost text-sm"
          >
            Remover imagem
          </button>
        )}
        {remove && <span className="text-xs text-[var(--accent)]">A imagem será removida ao salvar.</span>}
      </div>
    </div>
  );
}
