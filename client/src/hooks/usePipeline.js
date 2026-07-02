import { useReducer, useRef, useEffect, useCallback } from 'react'

const WS_URL = `ws://${window.location.host}/ws`

const INITIAL = {
  connected: false,
  status: 'idle',
  workers: [],
  progress: { processed: 0, batch: 0, totalCount: 0, sessionStart: null, sessionBaseProcessed: 0 },
  events: [],
  error: null,
  currentQuery: null,
}

function reducer(state, action) {
  const addEvent = (msg, level = 'info') => ({
    ...state,
    events: [{ id: Date.now() + Math.random(), msg, level, ts: new Date().toLocaleTimeString() },
             ...state.events].slice(0, 200)
  })

  switch (action.type) {
    case 'CONNECTED':
      return { ...state, connected: true, status: action.running ? 'running' : 'idle' }
    case 'DISCONNECTED':
      return { ...state, connected: false }

    case 'STARTED':
      return {
        ...addEvent(`Pipeline started — job: ${action.job}, instance: ${action.instanceUrl}, batch: ${action.batchSize}, threads: ${action.threads}`),
        status: 'running',
        workers: [],
        progress: {
          processed: 0, batch: 0, totalCount: 0,
          sessionStart: action._ts,
          sessionBaseProcessed: action.initialProcessed || 0,
        },
        error: null,
      }
    case 'TOTAL_COUNT':
      return {
        ...addEvent(`${action.objectType}: ${action.totalCount.toLocaleString()} total records`),
        progress: { ...state.progress, totalCount: action.totalCount },
      }
    case 'QUERYING':
      return {
        ...addEvent(`Batch ${action.batch}: ${action.query}`),
        currentQuery: action.query,
        progress: { ...state.progress, batch: action.batch },
      }
    case 'QUERY_COMPLETE':
      return addEvent(`Batch ${action.batch}: ${action.count} records fetched. LastId: ${action.lastId}`)

    case 'WORKERS_INIT':
      return {
        ...state,
        workers: [
          ...state.workers,
          ...action.workers.map(w => ({ ...w, status: 'waiting', currentPlugin: null }))
        ]
      }
    case 'WORKER_START':
      return {
        ...state,
        workers: state.workers.map(w =>
          w.id === action.workerId ? { ...w, status: 'running' } : w)
      }
    case 'WORKER_PLUGIN':
      return {
        ...addEvent(`Worker ${action.workerId}: running plugin ${action.plugin}`),
        workers: state.workers.map(w =>
          w.id === action.workerId ? { ...w, status: 'running', currentPlugin: action.plugin } : w)
      }
    case 'WORKER_LOG':
      return addEvent(`[W${action.workerId}] ${action.message}`)
    case 'WORKER_DONE':
      return {
        ...state,
        workers: state.workers.filter(w => w.id !== action.workerId),
        ...(action.totalProcessed != null
          ? { progress: { ...state.progress, processed: action.totalProcessed } }
          : {}),
      }
    case 'WORKER_ERROR':
      return {
        ...addEvent(`Worker ${action.workerId} error in ${action.plugin}: ${action.error}`, 'error'),
        workers: state.workers.filter(w => w.id !== action.workerId)
      }

    case 'BATCH_COMPLETE':
      return {
        ...addEvent(`Batch ${action.batch} complete. Total processed: ${action.totalProcessed}`),
        progress: { ...state.progress, processed: action.totalProcessed },
      }
    case 'COMPLETE':
      return {
        ...addEvent(`All done! Total processed: ${action.totalProcessed}`, 'success'),
        status: 'completed',
        progress: { ...state.progress, processed: action.totalProcessed },
      }
    case 'STOPPING':
      return { ...addEvent('Stop requested…'), status: 'stopping' }
    case 'STOPPED':
      return {
        ...addEvent(`Pipeline stopped. Total processed: ${action.totalProcessed}`),
        status: 'idle',
        workers: state.workers.map(w => ({ ...w, status: w.status === 'running' ? 'stopped' : w.status })),
      }
    case 'ERROR':
      return {
        ...addEvent(`Error: ${action.message}`, 'error'),
        status: 'idle',
        error: action.message,
      }

    default:
      return state
  }
}

export function usePipeline() {
  const [state, dispatch] = useReducer(reducer, INITIAL)
  const wsRef = useRef(null)
  const reconnectTimer = useRef(null)

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return

    const ws = new WebSocket(WS_URL)
    wsRef.current = ws

    ws.onopen = () => clearTimeout(reconnectTimer.current)

    ws.onmessage = (e) => {
      try { dispatch({ ...JSON.parse(e.data), _ts: Date.now() }) } catch {}
    }

    ws.onclose = () => {
      dispatch({ type: 'DISCONNECTED' })
      reconnectTimer.current = setTimeout(connect, 3000)
    }

    ws.onerror = () => ws.close()
  }, [])

  useEffect(() => {
    connect()
    return () => {
      clearTimeout(reconnectTimer.current)
      wsRef.current?.close()
    }
  }, [connect])

  const send = useCallback((msg) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(msg))
    }
  }, [])

  const startPipeline = useCallback((jobId, instanceUrl, accessToken, batchSize, threads, params, fresh = false) => {
    send({ type: 'START', jobId, instanceUrl, accessToken, batchSize: Number(batchSize), threads: Number(threads), params: params || {}, fresh: !!fresh })
  }, [send])

  const retryErrors = useCallback((jobId, instanceUrl, accessToken, threads, params) => {
    send({ type: 'RETRY', jobId, instanceUrl, accessToken, threads: Number(threads), params: params || {} })
  }, [send])

  const stopPipeline = useCallback(() => send({ type: 'STOP' }), [send])

  return { state, startPipeline, retryErrors, stopPipeline }
}
