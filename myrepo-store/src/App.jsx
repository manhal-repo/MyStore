import { useEffect, useMemo, useState } from 'react'
import { Banner, Interstitial } from './Ads.jsx'

// غيّر الاسم والرابط حسب مشروعك
const STORE_NAME = 'متجر تطبيقاتي'
const REPO_URL = new URL('repo', window.location.href.split('#')[0]).href

const fmtSize = (b) => (b / 1048576).toFixed(1) + ' MB'

function AppCard({ app, version, onDownload }) {
  const [open, setOpen] = useState(false)
  const loc = app.localized?.ar || app.localized?.['en-US'] || {}
  const name = loc.name || app.name
  const summary = loc.summary || app.summary
  const desc = loc.description || app.description
  return (
    <div className="card">
      <div className="row">
        {app.icon && <img src={`repo/icons/${app.icon}`} alt="" onError={(e) => (e.target.style.display = 'none')} />}
        <div className="grow">
          <h3>{name}</h3>
          <p className="muted">{summary}</p>
          <small>{version ? `الإصدار ${version.versionName} · ${fmtSize(version.size)}` : ''}</small>
        </div>
      </div>
      {open && desc && <p className="desc" dangerouslySetInnerHTML={{ __html: desc }} />}
      <div className="actions">
        {version && <button className="btn" onClick={() => onDownload(version.apkName, name)}>تنزيل APK</button>}
        {desc && <button className="btn ghost" onClick={() => setOpen(!open)}>{open ? 'إخفاء' : 'التفاصيل'}</button>}
      </div>
    </div>
  )
}

export default function App() {
  const [data, setData] = useState(null)
  const [err, setErr] = useState(null)
  const [q, setQ] = useState('')
  const [openAd, setOpenAd] = useState(true)   // إعلان منبثق عند كل فتح
  const [dl, setDl] = useState(null)           // تنزيل بانتظار الإعلان

  const startDownload = () => {
    const a = document.createElement('a')
    a.href = `repo/${dl.apk}`; a.download = ''
    document.body.appendChild(a); a.click(); a.remove()
    setDl(null)
  }

  useEffect(() => {
    fetch('repo/index-v1.json')
      .then((r) => { if (!r.ok) throw new Error(r.status); return r.json() })
      .then(setData)
      .catch(() => setErr('تعذّر تحميل فهرس التطبيقات'))
  }, [])

  const list = useMemo(() => {
    if (!data) return []
    return data.apps
      .map((a) => {
        const vs = (data.packages[a.packageName] || []).slice().sort((x, y) => y.versionCode - x.versionCode)
        const v = vs.find((x) => x.versionCode === a.suggestedVersionCode) || vs[0]
        return { app: a, version: v }
      })
      .filter(({ app }) => (app.name + ' ' + (app.summary || '')).toLowerCase().includes(q.toLowerCase()))
  }, [data, q])

  return (
    <div className="wrap">
      <header>
        <h1>{STORE_NAME}</h1>
        <p className="muted">نزّل التطبيقات مباشرة، أو أضف المستودع إلى F-Droid / NetHunter Store.</p>
      </header>

      <section className="card repo">
        <b>رابط المستودع</b>
        <code dir="ltr">{REPO_URL}</code>
        <div className="actions">
          <button className="btn" onClick={() => navigator.clipboard.writeText(REPO_URL)}>نسخ الرابط</button>
          <a className="btn ghost" href={`fdroidrepo://${REPO_URL.replace(/^https?:\/\//, '')}`}>فتح في F-Droid</a>
        </div>
        <small className="muted">في التطبيق: الإعدادات ← المستودعات ← + ← الصق الرابط.</small>
      </section>

      <input className="search" placeholder="ابحث عن تطبيق…" value={q} onChange={(e) => setQ(e.target.value)} />

      {err && <p className="err">{err}</p>}
      {!data && !err && <p className="muted">جارٍ التحميل…</p>}
      <div className="grid">
        {list.map(({ app, version }) => <AppCard key={app.packageName} app={app} version={version} onDownload={(apk, name) => setDl({ apk, name })} />)}
      </div>
      {data && list.length === 0 && <p className="muted">لا توجد تطبيقات.</p>}

      <Banner />
      {openAd && <Interstitial title={STORE_NAME} onDone={() => setOpenAd(false)} />}
      {dl && !openAd && <Interstitial title={`تنزيل ${dl.name}`} onDone={startDownload} onCancel={() => setDl(null)} />}
    </div>
  )
}
