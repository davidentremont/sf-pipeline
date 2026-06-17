import { useState, useEffect, useCallback } from 'react'

export function useProgress() {
  const [progress, setProgress] = useState([])

  const refresh = useCallback(() => {
    fetch('/api/progress')
      .then(r => r.json())
      .then(setProgress)
      .catch(() => {})
  }, [])

  useEffect(() => { refresh() }, [refresh])

  return { progress, refresh }
}
