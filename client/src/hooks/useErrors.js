import { useState, useCallback } from 'react'

export function useErrors() {
  const [errors, setErrors] = useState([])
  const [loading, setLoading] = useState(false)

  const fetchErrors = useCallback(async (jobId, instanceUrl) => {
    if (!jobId || !instanceUrl) { setErrors([]); return }
    setLoading(true)
    try {
      const res = await fetch(`/api/errors?jobId=${encodeURIComponent(jobId)}&instanceUrl=${encodeURIComponent(instanceUrl)}`)
      if (res.ok) setErrors(await res.json())
    } catch {}
    finally { setLoading(false) }
  }, [])

  const clearErrors = useCallback(async (jobId, instanceUrl) => {
    await fetch(`/api/errors?jobId=${encodeURIComponent(jobId)}&instanceUrl=${encodeURIComponent(instanceUrl)}`,
      { method: 'DELETE' })
    setErrors([])
  }, [])

  return { errors, loading, fetchErrors, clearErrors }
}
