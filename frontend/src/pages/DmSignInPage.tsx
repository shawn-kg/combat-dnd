import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import type { DmLinkInfo } from '../api/campaign'
import { api, ApiError } from '../api/client'
import './pages.css'

/** The DM opens their DM sign-in link on another device (or after clearing cookies) and becomes the DM here too. */
export function DmSignInPage() {
  const { code = '' } = useParams()
  const navigate = useNavigate()
  const info = useQuery({ queryKey: ['dm-sign-in', code], queryFn: () => api<DmLinkInfo>('GET', `/dm-sign-in/${code}`), retry: false })
  const signIn = useMutation({
    mutationFn: () => api<DmLinkInfo>('POST', `/dm-sign-in/${code}`),
    onSuccess: (r) => navigate(`/c/${r.campaignId}`, { replace: true }),
  })

  if (info.isPending) return <main className="page muted">Opening the link…</main>
  if (info.error)
    return (
      <main className="page stack">
        <h1 className="title">DM sign-in</h1>
        <div className="notice">{(info.error as ApiError).message}</div>
        <p className="muted small">On a device where you’re still the DM, open the campaign and make a new DM sign-in link.</p>
      </main>
    )

  return (
    <main className="page stack">
      <h1 className="title">Sign in as the DM</h1>
      <section className="card">
        <h3>{info.data.campaignName}</h3>
        <p className="muted small">This browser will be signed in as the DM of {info.data.campaignName}, alongside your other devices.</p>
        <div className="lrow">
          <span className="spacer" />
          <button className="btn primary" disabled={signIn.isPending} onClick={() => signIn.mutate()}>
            Sign in as DM
          </button>
        </div>
        {signIn.error && <div className="err">{(signIn.error as ApiError).message}</div>}
      </section>
    </main>
  )
}
