import { useEffect, useState } from 'react';
import { getBuild, confirmStep, goBack } from '../../api/characterBuild';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import IdentityStep from './IdentityStep';
import TraitsStep from './TraitsStep';
import BloodTestStep from './BloodTestStep';
import AttributesStep from './AttributesStep';
import FreePointsStep from './FreePointsStep';
import ReviewStep from './ReviewStep';

const STEPS = [
  { key: 'IDENTITY', label: 'Identidade', C: IdentityStep },
  { key: 'TRAITS', label: 'Traços', C: TraitsStep },
  { key: 'BLOOD_TEST', label: 'Teste de Sangue', C: BloodTestStep },
  { key: 'ATTRIBUTES', label: 'Atributos', C: AttributesStep },
  { key: 'FREE_POINTS', label: 'Pontos Livres', C: FreePointsStep },
  { key: 'REVIEW', label: 'Revisão', C: ReviewStep },
];

/** `isOwner` (opcional): dono da ficha; padrão = não é Mestre. Quem não é dono só vê (Mestre ainda resolve traços pendentes). */
export default function BuildWizard({ campaignId, sheetId, isMaster, isOwner = !isMaster, onDone }) {
  const toast = useToast();
  const [build, setBuild] = useState(null);
  const [busy, setBusy] = useState(false);
  const [burst, setBurst] = useState(null);

  useEffect(() => {
    let active = true;
    getBuild(sheetId)
      .then((b) => { if (active) setBuild(b); })
      .catch((e) => { if (active) toast(parseApiError(e).message, 'error'); });
    return () => { active = false; };
  }, [sheetId, toast]);

  if (!build) return <div className="skeleton h-40" aria-busy="true" />;

  const idx = STEPS.findIndex((s) => s.key === build.step);
  const canAct = isOwner && build.buildStatus === 'IN_BUILD';
  const { C } = STEPS[idx];

  async function act(fn, burstText) {
    setBusy(true);
    try {
      const r = await fn();
      setBuild(r);
      if (burstText) setBurst({ text: burstText, k: Date.now() });
      return r;
    } catch (err) {
      toast(parseApiError(err).message, 'error');
      return null;
    } finally {
      setBusy(false);
    }
  }
  // prep opcional (PUT do passo) antes de confirmar; falha em prep aborta.
  const confirm = (prep) => act(async () => {
    if (prep) await prep();
    return confirmStep(sheetId, build.step);
  });
  const back = idx > 0 ? () => act(() => goBack(sheetId, STEPS[idx - 1].key)) : null;

  return (
    <section className="flex flex-col gap-4" aria-label="Construção Inicial">
      <ol className="page-gutter flex flex-wrap" aria-label="Passos">
        {STEPS.map((s, i) => (
          <li key={s.key} className={i === idx ? 'tag-accent tag' : 'tag'} aria-current={i === idx ? 'step' : undefined}
            style={i > idx ? { opacity: 0.5 } : undefined}>
            {i < idx ? '✓ ' : ''}{i + 1}. {s.label}
          </li>
        ))}
      </ol>
      {!isOwner && <p className="caption">Somente leitura{isMaster ? ' — você pode resolver traços pendentes.' : '.'}</p>}
      <div className="panel flex flex-col gap-4 p-4">
        <h2 className="panel-title">{STEPS[idx].label}</h2>
        <C build={build} campaignId={campaignId} canAct={canAct} isMaster={isMaster} busy={busy}
          act={act} confirm={confirm} burst={burst} onBack={back} onDone={onDone} />
      </div>
    </section>
  );
}
