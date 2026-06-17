import React from 'react'

const STATUS_STYLES = {
  waiting: 'bg-gray-100 border-gray-300 text-gray-500',
  running: 'worker-running border-green-400 text-green-800',
  done:    'bg-green-100 border-green-500 text-green-800',
  error:   'bg-red-100 border-red-400 text-red-800',
  stopped: 'bg-yellow-100 border-yellow-400 text-yellow-800',
}

const STATUS_DOT = {
  waiting: 'bg-gray-300',
  running: 'bg-green-500 animate-ping',
  done:    'bg-green-500',
  error:   'bg-red-500',
  stopped: 'bg-yellow-500',
}

function WorkerCard({ worker }) {
  const style = STATUS_STYLES[worker.status] || STATUS_STYLES.waiting
  const dot   = STATUS_DOT[worker.status]   || STATUS_DOT.waiting

  return (
    <div className={`rounded-lg border-2 p-3 min-w-[120px] flex flex-col gap-1 ${style}`}>
      <div className="flex items-center gap-2">
        <span className={`w-2.5 h-2.5 rounded-full ${dot} shrink-0`} />
        <span className="font-semibold text-sm">Worker {worker.id}</span>
      </div>
      <div className="text-xs capitalize">{worker.status}</div>
      {worker.currentPlugin && (
        <div className="text-xs truncate opacity-75" title={worker.currentPlugin}>
          ↳ {worker.currentPlugin}
        </div>
      )}
      <div className="text-xs opacity-60">{worker.records} records</div>
    </div>
  )
}

export default function WorkerMonitor({ workers, progress }) {
  const pct = progress.processed > 0
    ? Math.min(100, Math.round((progress.processed / (progress.processed + 1)) * 100))
    : 0

  return (
    <div className="card">
      <div className="card-header flex items-center justify-between">
        <span>Live Thread Monitoring</span>
        {progress.batch > 0 && (
          <span className="text-gray-500 font-normal normal-case tracking-normal">
            Batch {progress.batch} · {progress.processed.toLocaleString()} records processed
          </span>
        )}
      </div>

      {progress.processed > 0 && (
        <div className="px-4 pt-3">
          <div className="flex items-center justify-between text-xs text-gray-600 mb-1">
            <span>Progress</span>
            <span>{progress.processed.toLocaleString()} records</span>
          </div>
          <div className="w-full h-2 bg-gray-200 rounded-full overflow-hidden">
            <div
              className="h-full bg-sf-blue rounded-full transition-all duration-500"
              style={{ width: `${pct}%` }}
            />
          </div>
        </div>
      )}

      <div className="p-4">
        {workers.length === 0 ? (
          <div className="text-sm text-gray-400 italic">Workers will appear here when the pipeline starts</div>
        ) : (
          <div className="flex flex-wrap gap-3">
            {workers.map(w => <WorkerCard key={w.id} worker={w} />)}
          </div>
        )}
      </div>
    </div>
  )
}
