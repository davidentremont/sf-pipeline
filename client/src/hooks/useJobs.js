import { useState, useEffect, useCallback } from 'react'

export function useJobs() {
  const [jobs, setJobs] = useState([])
  const [selectedJob, setSelectedJob] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const fetchJobs = useCallback(async () => {
    setLoading(true)
    try {
      const res = await fetch('/api/jobs')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      setJobs(data)
      if (data.length > 0 && !selectedJob) {
        setSelectedJob(data[0])
      }
      setError(null)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchJobs() }, [fetchJobs])

  const selectJob = useCallback((jobId) => {
    const job = jobs.find(j => j.id === jobId)
    setSelectedJob(job || null)
  }, [jobs])

  return { jobs, selectedJob, selectJob, loading, error, reload: fetchJobs }
}
