import React from 'react'

function formatEta(seconds) {
  if (seconds < 60) return `${Math.round(seconds)}s`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${Math.round(seconds % 60)}s`
  const h = Math.floor(seconds / 3600)
  return `${h}h ${Math.floor((seconds % 3600) / 60)}m`
}

const STATUS_RING = {
  waiting: 'bg-gray-200 border-gray-300',
  running: 'bg-green-100 border-green-400',
}

const STATUS_DOT = {
  waiting: 'bg-gray-300',
  running: 'bg-green-500 animate-ping',
}

const STATUS_TEXT = {
  waiting: 'text-gray-400',
  running: 'text-green-800',
}

export default function WorkerMonitor({ workers, progress }) {
  const runningCount = workers.length
  const { processed, batch, totalCount, sessionStart, sessionBaseProcessed } = progress

  const pct = totalCount > 0
    ? Math.min(100, (processed / totalCount) * 100)
    : null

  const statsLabel = totalCount > 0
    ? `${processed.toLocaleString()} / ${totalCount.toLocaleString()} (${pct.toFixed(1)}%)`
    : processed > 0
      ? `${processed.toLocaleString()} records`
      : null

  const elapsedSec = sessionStart ? (Date.now() - sessionStart) / 1000 : 0
  const processedThisRun = processed - (sessionBaseProcessed || 0)
  const rate = elapsedSec > 15 && processedThisRun > 0 ? processedThisRun / elapsedSec : null
  const etaSeconds = rate && totalCount > 0 && processed < totalCount
    ? (totalCount - processed) / rate
    : null

  return (
    <div className="card">
      <div className="card-header flex items-center justify-between">
        <span>Live Thread Monitoring</span>
        <div className="flex items-center gap-3 font-normal normal-case tracking-normal text-xs text-gray-500">
          {runningCount > 0 && (
            <span className="text-green-600">{runningCount} running</span>
          )}
          {batch > 0 && (
            <span>Batch {batch}</span>
          )}
          {statsLabel && (
            <span className="font-medium text-gray-700">{statsLabel}</span>
          )}
        </div>
      </div>

      {processed > 0 && (
        <div className="px-4 pt-3 space-y-1">
          <div className="w-full h-2 bg-gray-200 rounded-full overflow-hidden">
            {pct != null ? (
              <div
                className="h-full bg-sf-blue rounded-full transition-all duration-500"
                style={{ width: `${pct}%` }}
              />
            ) : (
              // Indeterminate stripe when no total is known
              <div className="h-full bg-sf-blue rounded-full opacity-40 w-full" />
            )}
          </div>
          {pct != null && (
            <div className="flex justify-between text-xs text-gray-400">
              <span>
                {processed.toLocaleString()} processed
                {rate != null && (
                  <span className="ml-2 text-gray-400">· ~{rate < 10 ? rate.toFixed(1) : Math.round(rate)} rec/s</span>
                )}
              </span>
              <span>
                {etaSeconds != null
                  ? `ETA ${formatEta(etaSeconds)}`
                  : `${(totalCount - processed).toLocaleString()} remaining`}
              </span>
            </div>
          )}
        </div>
      )}

      <div className="px-4 py-3">
        {workers.length === 0 ? (
          <div className="text-sm text-gray-400 italic">Workers will appear here when the pipeline starts</div>
        ) : (
          <div className="flex flex-wrap gap-1 max-h-36 overflow-y-auto pr-1">
            {workers.map(w => (
              <div
                key={w.id}
                className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded border text-xs font-mono select-none ${STATUS_RING[w.status] || STATUS_RING.waiting} ${STATUS_TEXT[w.status] || STATUS_TEXT.waiting}`}
                title={[
                  `Worker ${w.id}`,
                  w.status,
                  w.currentPlugin,
                  `${w.records ?? 0} records`,
                ].filter(Boolean).join(' · ')}
              >
                <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${STATUS_DOT[w.status] || STATUS_DOT.waiting}`} />
                {w.id}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
