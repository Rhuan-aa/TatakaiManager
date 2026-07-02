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
      <span className="text-sm font-medium text-zinc-400">
        Imagem <span className="text-zinc-600">(opcional)</span>
      </span>
      {shownUrl && (
        <div className="mt-2 w-32 overflow-hidden rounded-lg border border-zinc-800 shadow-md shadow-black/30">
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
        <label className="cursor-pointer rounded-md border border-zinc-700 px-3 py-1.5 text-sm text-zinc-300 hover:bg-zinc-700">
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
            className="text-sm text-zinc-500 hover:text-red-400"
          >
            Remover imagem
          </button>
        )}
        {remove && <span className="text-xs text-red-400">A imagem será removida ao salvar.</span>}
      </div>
    </div>
  );
}
