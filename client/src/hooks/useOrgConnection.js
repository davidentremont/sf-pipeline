import { useState, useCallback, useEffect } from 'react'

export function useOrgConnection() {
  const [orgs, setOrgs] = useState([])
  const [cliAvailable, setCliAvailable] = useState(false)
  const [orgsLoading, setOrgsLoading] = useState(false)
  const [orgStatus, setOrgStatus] = useState({ status: 'idle', username: null, error: null })

  const fetchOrgs = useCallback(async () => {
    setOrgsLoading(true)
    try {
      const res = await fetch('/api/orgs')
      if (res.ok) {
        const data = await res.json()
        setCliAvailable(data.available)
        setOrgs(data.orgs || [])
      }
    } catch {}
    finally { setOrgsLoading(false) }
  }, [])

  useEffect(() => { fetchOrgs() }, [fetchOrgs])

  const loadOrgToken = useCallback(async (aliasOrUsername) => {
    const res = await fetch('/api/orgs/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ alias: aliasOrUsername }),
    })
    const data = await res.json()
    if (!res.ok || data.error) throw new Error(data.error || 'Failed to load token')
    return data
  }, [])

  const verify = useCallback(async (instanceUrl, accessToken) => {
    setOrgStatus({ status: 'verifying', username: null, error: null })
    try {
      const res = await fetch('/api/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ instanceUrl, accessToken }),
      })
      const data = await res.json()
      if (data.success) {
        setOrgStatus({ status: 'verified', username: data.info?.username || null, error: null })
      } else {
        setOrgStatus({ status: 'error', username: null, error: data.error || 'Verification failed' })
      }
    } catch (e) {
      setOrgStatus({ status: 'error', username: null, error: e.message })
    }
  }, [])

  const clearOrgStatus = useCallback(() => {
    setOrgStatus({ status: 'idle', username: null, error: null })
  }, [])

  return { orgs, cliAvailable, orgsLoading, fetchOrgs, loadOrgToken, orgStatus, verify, clearOrgStatus }
}
