import { useEffect, useRef, useState } from 'react'
import { ADS } from './ads.config.js'

// وحدة إعلان: تُبلغ بحالة "filled" فقط إذا ظهر إعلان فعلًا
export function AdSlot({ slot, onStatus, style }) {
  const ref = useRef(null)
  useEffect(() => {
    const el = ref.current
    if (ADS.testMode) {
      const t = setTimeout(() => onStatus?.('filled'), 1000)
      return () => clearTimeout(t)
    }
    try { (window.adsbygoogle = window.adsbygoogle || []).push({}) } catch {}
    const check = () => {
      const s = el.getAttribute('data-ad-status')
      if (!s) return
      onStatus?.(s === 'filled' && el.offsetHeight > 0 ? 'filled' : 'unfilled')
    }
    const mo = new MutationObserver(check)
    mo.observe(el, { attributes: true, attributeFilter: ['data-ad-status'] })
    return () => mo.disconnect()
  }, [])
  if (ADS.testMode) return <div className="fakead" style={style}>إعلان تجريبي</div>
  return (
    <ins ref={ref} className="adsbygoogle" style={{ display: 'block', ...style }}
      data-ad-client={ADS.client} data-ad-slot={slot}
      data-ad-format="auto" data-full-width-responsive="true" />
  )
}

export function Banner() {
  return <div className="banner"><AdSlot slot={ADS.bannerSlot} style={{ minHeight: 60 }} /></div>
}

// إعلان منبثق: لا يستدعي onDone إلا بعد: تحميل ← ظهور فعلي ← انتهاء العدّاد ← ضغط "متابعة"
export function Interstitial({ title, onDone, onCancel }) {
  const [state, setState] = useState('loading') // loading | showing | ready | failed
  const [left, setLeft] = useState(ADS.countdown)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (state !== 'loading') return
    const t = setTimeout(() => setState('failed'), ADS.loadTimeout)
    return () => clearTimeout(t)
  }, [state, attempt])

  useEffect(() => {
    if (state !== 'showing') return
    if (left <= 0) { setState('ready'); return }
    const t = setTimeout(() => setLeft((n) => n - 1), 1000)
    return () => clearTimeout(t)
  }, [state, left])

  const onStatus = (s) => setState((cur) => cur !== 'loading' ? cur : s === 'filled' ? 'showing' : 'failed')
  const retry = () => { setLeft(ADS.countdown); setState('loading'); setAttempt((n) => n + 1) }

  return (
    <div className="overlay">
      <div className="modal">
        <h3>{title}</h3>
        <AdSlot key={attempt} slot={ADS.popupSlot} onStatus={onStatus} style={{ minHeight: 250 }} />
        {state === 'loading' && <p className="muted">جارٍ تحميل الإعلان…</p>}
        {state === 'showing' && <p className="muted">يمكنك المتابعة بعد {left} ثانية</p>}
        {state === 'failed' && <p className="err">تعذّر عرض الإعلان (قد يكون مانع الإعلانات مفعّلًا). لا يمكن المتابعة بدونه.</p>}
        <div className="actions">
          {state === 'ready' && <button className="btn" onClick={onDone}>متابعة</button>}
          {state === 'failed' && <button className="btn" onClick={retry}>إعادة المحاولة</button>}
          {onCancel && <button className="btn ghost" onClick={onCancel}>إلغاء</button>}
        </div>
      </div>
    </div>
  )
}
