import React from 'react'

const STATUS_RING = {
  waiting: 'bg-gray-200 border-gray-300',
  running: 'bg-green-100 border-green-400',
  done:    'bg-green-50 border-green-400',
  error:   'bg-red-50 border-red-400',
  stopped: 'bg-yellow-50 border-yellow-400',
}

const STATUS_DOT = {
  waiting: 'bg-gray-300',
  running: 'bg-green-500 animate-ping',
  done:    'bg-green-500',
  error:   'bg-red-500',
  stopped: 'bg-yellow-500',
}

const STATUS_TEXT = {
  waiting: 'text-gray-400',
  running: 'text-green-800',
  done:    'text-green-700',
  error:   'text-red-700',
  stopped: 'text-yellow-700',
}

export default function WorkerMonitor({ workers, progress }) {
  const runningCount = workers.filter(w => w.status === 'running').length
  const doneCount    = workers.filter(w => w.status === 'done').length
  const errorCount   = workers.filter(w => w.status === 'error').length

  return (
    <div className="card">
      <div className="card-header flex items-center justify-between">
        <span>Live Thread Monitoring</span>
        <div className="flex items-center gap-3 font-normal normal-case tracking-normal text-xs text-gray-500">
          {workers.length > 0 && (
            <>
              <span className="text-green-600">{runningCount} running</span>
              <span>{doneCount} done</span>
              {errorCount > 0 && <span className="text-red-600">{errorCount} errors</span>}
            </>
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
