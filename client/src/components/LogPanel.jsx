import React, { useState, useEffect, useRef, useReducer } from 'react'

const LEVEL_STYLE = {
  info:    'text-gray-700',
  success: 'text-green-700 font-medium',
  error:   'text-red-600 font-medium',
}

const WORKER_RING = {
  waiting: 'bg-gray-100 border-gray-300 text-gray-400',
  running: 'bg-green-100 border-green-400 text-green-800',
}

const WORKER_DOT = {
  waiting: 'bg-gray-300',
  running: 'bg-green-500 animate-ping',
}

function formatEta(seconds) {
  if (seconds < 60) return `${Math.round(seconds)}s`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${Math.round(seconds % 60)}s`
  return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`
}

function formatElapsed(seconds) {
  if (seconds < 60) return `${Math.floor(seconds)}s`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${Math.floor(seconds % 60)}s`
  return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`
}

export default function LogPanel({
  events,
  errors, errorsLoading,
  onRetry, onClear, onRefresh,
  disabled,
  workers = [],
  progress = {},
  isRunning = false,
}) {
  const [tab, setTab] = useState('workers')
  const bottomRef = useRef(null)
  const prevErrorCount = useRef(errors.length)
  const prevRunning = useRef(isRunning)

  // Switch to Workers when a run starts
  useEffect(() => {
    if (isRunning && !prevRunning.current) {
      setTab('workers')
    }
    prevRunning.current = isRunning
  }, [isRunning])

  // Switch to Errors when new errors arrive
  useEffect(() => {
    if (errors.length > prevErrorCount.current) {
      setTab('errors')
    }
    prevErrorCount.current = errors.length
  }, [errors.length])

  // Auto-scroll event log
  useEffect(() => {
    if (tab === 'events') {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [events.length, tab])

  const [, tick] = useReducer(n => n + 1, 0)
  useEffect(() => {
    if (!isRunning) return
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [isRunning])

  const { processed = 0, batch = 0, totalCount = 0, sessionStart, sessionBaseProcessed = 0 } = progress
  const pct = totalCount > 0 ? Math.min(100, (processed / totalCount) * 100) : null
  const elapsedSec = sessionStart ? (Date.now() - sessionStart) / 1000 : 0
  const processedThisRun = processed - sessionBaseProcessed
  const rate = elapsedSec > 15 && processedThisRun > 0 ? processedThisRun / elapsedSec : null
  const etaSeconds = rate && totalCount > 0 && processed < totalCount ? (totalCount - processed) / rate : null
  const recsPerMin = rate != null ? Math.round(rate * 60) : null

  const errorBadge = errors.length > 0 && (
    <span className="ml-1.5 bg-red-500 text-white text-xs font-bold px-1.5 py-0.5 rounded-full leading-none">
      {errors.length}
    </span>
  )

  const tabCls = (t, color = 'sf-blue') => {
    const active = tab === t
    const colorMap = {
      'sf-blue': active ? 'border-sf-blue text-sf-dark' : 'border-transparent text-gray-500 hover:text-gray-700',
      'red':     active ? 'border-red-400 text-red-700'  : 'border-transparent text-gray-500 hover:text-gray-700',
      'green':   active ? 'border-green-500 text-green-800' : 'border-transparent text-gray-500 hover:text-gray-700',
    }
    return `text-xs px-3 py-2.5 font-medium border-b-2 transition-colors ${colorMap[color]}`
  }

  return (
    <div className="card flex flex-col" style={{ minHeight: '28rem' }}>
      {/* Tab bar */}
      <div className="flex items-center border-b border-sf-border bg-gray-50 rounded-t-lg px-3 gap-0.5 shrink-0">
        <button type="button" onClick={() => setTab('workers')} className={tabCls('workers', 'green')}>
          <span className="flex items-center gap-1.5">
            Workers
            {workers.length > 0 && (
              <span className="bg-green-500 text-white text-xs font-bold px-1.5 py-0.5 rounded-full leading-none">
                {workers.length}
              </span>
            )}
          </span>
        </button>
        <button type="button" onClick={() => setTab('events')} className={tabCls('events')}>
          Event Log
        </button>
        <button type="button" onClick={() => setTab('errors')} className={tabCls('errors', 'red')}>
          <span className="flex items-center">
            Failed Records
            {errorBadge}
          </span>
        </button>

        {tab === 'errors' && (errors.length > 0 || errorsLoading) && (
          <div className="ml-auto flex gap-2 py-1.5">
            <button className="text-xs text-sf-blue hover:underline" onClick={onRefresh} disabled={errorsLoading}>
              Refresh
            </button>
            <button
              className="btn-primary text-xs px-3 py-1"
              onClick={onRetry}
              disabled={disabled || errors.length === 0}
            >
              ↩ Retry Failed
            </button>
            <button className="btn-secondary text-xs px-3 py-1" onClick={onClear} disabled={disabled}>
              Clear
            </button>
          </div>
        )}
      </div>

      {/* Workers tab */}
      {tab === 'workers' && (
        <div className="flex-1 flex flex-col p-4 overflow-y-auto gap-3">
          {/* Progress bar */}
          {processed > 0 && (
            <div className="space-y-1">
              <div className="w-full h-2 bg-gray-200 rounded-full overflow-hidden">
                {pct != null ? (
                  <div
                    className="h-full bg-sf-blue rounded-full transition-all duration-500"
                    style={{ width: `${pct}%` }}
                  />
                ) : (
                  <div className="h-full bg-sf-blue rounded-full opacity-40 w-full" />
                )}
              </div>
              <div className="flex justify-between text-xs text-gray-500">
                <span>
                  {processed.toLocaleString()} processed
                  {batch > 0 && <span className="ml-2 text-gray-400">· Batch {batch}</span>}
                  {recsPerMin != null && (
                    <span className="ml-2 text-gray-400">· {recsPerMin.toLocaleString()} rec/min</span>
                  )}
                </span>
                <span className="flex items-center gap-2">
                  {sessionStart && (
                    <span className="text-gray-400">⏱ {formatElapsed(elapsedSec)}</span>
                  )}
                  {pct != null && (
                    etaSeconds != null
                      ? <span>ETA {formatEta(etaSeconds)}</span>
                      : <span>{(totalCount - processed).toLocaleString()} remaining</span>
                  )}
                </span>
              </div>
            </div>
          )}

          {/* Worker bubbles */}
          {workers.length === 0 ? (
            <div className="text-sm text-gray-400 italic">Workers will appear here when the pipeline starts</div>
          ) : (
            <div className="flex flex-wrap gap-1.5 content-start">
              {workers.map(w => (
                <div
                  key={w.id}
                  className={`inline-flex items-center gap-1.5 px-2 py-1 rounded border text-xs font-mono select-none ${WORKER_RING[w.status] || WORKER_RING.waiting}`}
                  title={[`Worker ${w.id}`, w.status, w.currentPlugin, `${w.records ?? 0} records`].filter(Boolean).join(' · ')}
                >
                  <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${WORKER_DOT[w.status] || WORKER_DOT.waiting}`} />
                  {w.id}
                  {w.records > 0 && <span className="text-gray-400">{w.records}</span>}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Event Log tab */}
      {tab === 'events' && (
        <div className="flex-1 p-3 overflow-y-auto font-mono text-xs space-y-0.5 bg-gray-50 rounded-b-lg">
          {events.length === 0 ? (
            <div className="text-gray-400 italic">No events yet — start the pipeline to see activity</div>
          ) : (
            [...events].reverse().map(e => (
              <div key={e.id} className={`flex gap-2 ${LEVEL_STYLE[e.level] || LEVEL_STYLE.info}`}>
                <span className="text-gray-400 shrink-0">{e.ts}</span>
                <span>{e.msg}</span>
              </div>
            ))
          )}
          <div ref={bottomRef} />
        </div>
      )}

      {/* Failed Records tab */}
      {tab === 'errors' && (
        <div className="flex-1 overflow-auto rounded-b-lg">
          {errors.length === 0 && !errorsLoading ? (
            <div className="p-4 text-sm text-gray-400 italic">No failed records</div>
          ) : (
            <table className="w-full text-xs">
              <thead className="sticky top-0 z-10">
                <tr className="bg-gray-50 border-b border-sf-border text-gray-500 uppercase tracking-wide">
                  <th className="px-3 py-2 text-left font-medium">Record ID</th>
                  <th className="px-3 py-2 text-left font-medium">Plugin</th>
                  <th className="px-3 py-2 text-left font-medium">Error</th>
                  <th className="px-3 py-2 text-left font-medium whitespace-nowrap">Time</th>
                </tr>
              </thead>
              <tbody>
                {errors.map(err => (
                  <tr key={err.id} className="border-b border-sf-border last:border-0 hover:bg-red-50">
                    <td className="px-3 py-2 font-mono text-gray-700 whitespace-nowrap">{err.recordId}</td>
                    <td className="px-3 py-2 text-gray-600 whitespace-nowrap">{err.pluginName}</td>
                    <td className="px-3 py-2 text-red-700 max-w-xs truncate" title={err.errorMessage}>
                      {err.errorMessage}
                    </td>
                    <td className="px-3 py-2 text-gray-400 whitespace-nowrap">
                      {new Date(err.occurredAt).toLocaleTimeString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  )
}
