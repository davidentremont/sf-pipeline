import React from 'react'

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

  return (
    <div className="card">
      <div className="card-header flex items-center justify-between">
        <span>Live Thread Monitoring</span>
        <div className="flex items-center gap-3 font-normal normal-case tracking-normal text-xs text-gray-500">
          {runningCount > 0 && (
            <span className="text-green-600">{runningCount} running</span>
          )}
          {progress.batch > 0 && (
            <span>Batch {progress.batch} · {progress.processed.toLocaleString()} records</span>
          )}
        </div>
      </div>

      {progress.processed > 0 && (
        <div className="px-4 pt-3">
          <div className="w-full h-1.5 bg-gray-200 rounded-full overflow-hidden">
            <div className="h-full bg-sf-blue rounded-full transition-all duration-500 w-full" />
          </div>
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
