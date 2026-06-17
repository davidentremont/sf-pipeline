import React, { useState, useEffect } from 'react'
import { usePipeline } from './hooks/usePipeline.js'
import { useJobs } from './hooks/useJobs.js'
import WorkerMonitor from './components/WorkerMonitor.jsx'
import EventLog from './components/EventLog.jsx'

function StatusDot({ connected }) {
  return (
    <span className="flex items-center gap-1.5 text-xs">
      <span className={`w-2 h-2 rounded-full ${connected ? 'bg-green-400' : 'bg-red-400'}`} />
      {connected ? 'Connected' : 'Disconnected'}
    </span>
  )
}

export default function App() {
  const { state, startPipeline, stopPipeline } = usePipeline()
  const { jobs, selectedJob, selectJob, loading: jobsLoading, reload: reloadJobs } = useJobs()

  const [instanceUrl, setInstanceUrl]   = useState('')
  const [accessToken, setAccessToken]   = useState('')
  const [showToken, setShowToken]       = useState(false)
  const [batchSize, setBatchSize]       = useState(1000)
  const [threads, setThreads]           = useState(5)

  useEffect(() => {
    if (selectedJob) {
      setBatchSize(selectedJob.defaultBatchSize || 1000)
      setThreads(selectedJob.defaultThreads || 5)
    }
  }, [selectedJob?.id])

  const isRunning = state.status === 'running' || state.status === 'stopping'
  const canStart  = state.connected && selectedJob && instanceUrl.trim() && accessToken.trim() && !isRunning
  const canStop   = state.status === 'running'

  function handleStart() {
    if (canStart) startPipeline(selectedJob.id, instanceUrl.trim(), accessToken.trim(), batchSize, threads)
  }

  const statusColor = {
    idle:      'text-gray-500',
    running:   'text-green-600 font-semibold',
    stopping:  'text-yellow-600',
    completed: 'text-blue-600',
    error:     'text-red-600',
  }

  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-sf-dark text-white px-6 py-3 flex items-center justify-between shadow-md">
        <span className="text-xl font-bold tracking-tight">SF Async Data Pipeline</span>
        <div className="flex items-center gap-4">
          <span className={`text-sm capitalize ${statusColor[state.status]}`}>{state.status}</span>
          <StatusDot connected={state.connected} />
        </div>
      </header>

      <main className="flex-1 p-5 max-w-7xl mx-auto w-full space-y-4">

        {/* Top row: Job selector + Job details */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">

          <div className="card">
            <div className="card-header flex items-center justify-between">
              <span>Jobs</span>
              <button onClick={reloadJobs} className="text-xs text-sf-blue hover:underline">Reload</button>
            </div>
            <div className="p-3 space-y-1">
              {jobsLoading && <div className="text-sm text-gray-400 italic">Loading…</div>}
              {!jobsLoading && jobs.length === 0 && (
                <div className="text-sm text-gray-400 italic">No jobs found in <code>jobs/</code></div>
              )}
              {jobs.map(job => (
                <button
                  key={job.id}
                  onClick={() => selectJob(job.id)}
                  className={`w-full text-left px-3 py-2 rounded text-sm transition-colors ${
                    selectedJob?.id === job.id
                      ? 'bg-sf-light text-sf-dark font-medium border-l-4 border-sf-blue'
                      : 'hover:bg-gray-50 text-gray-700'
                  }`}
                >
                  <div className="font-medium">{job.name}</div>
                  <div className="text-xs text-gray-500">v{job.version} · {(job.plugins || []).length} plugin(s)</div>
                </button>
              ))}
            </div>
          </div>

          <div className="card lg:col-span-2">
            {selectedJob ? (
              <>
                <div className="card-header flex items-center justify-between">
                  <span>{selectedJob.name}</span>
                  <span className="text-gray-400 font-normal normal-case tracking-normal text-xs">v{selectedJob.version}</span>
                </div>
                <div className="p-4 space-y-3">
                  {selectedJob.description && (
                    <p className="text-sm text-gray-600">{selectedJob.description}</p>
                  )}
                  <div>
                    <div className="label">SOQL Query</div>
                    <pre className="bg-gray-50 border border-sf-border rounded px-3 py-2 text-xs text-gray-700 overflow-x-auto whitespace-pre-wrap">
                      {selectedJob.query}
                    </pre>
                  </div>
                  <div>
                    <div className="label">Plugins ({(selectedJob.plugins || []).length})</div>
                    <div className="flex flex-wrap gap-1.5">
                      {(selectedJob.plugins || []).map((p, i) => (
                        <span key={p} className="bg-sf-light text-sf-dark text-xs px-2 py-0.5 rounded-full font-medium">
                          {i + 1}. {p}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              </>
            ) : (
              <div className="p-8 text-center text-gray-400 italic text-sm">Select a job from the list</div>
            )}
          </div>
        </div>

        {/* Credentials + Pipeline config */}
        <div className="card">
          <div className="card-header">Configuration &amp; Controls</div>
          <div className="p-4 space-y-3">

            {/* Row 1: credentials */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <div>
                <label className="label">Instance URL</label>
                <input
                  className="input"
                  type="text"
                  value={instanceUrl}
                  onChange={e => setInstanceUrl(e.target.value)}
                  placeholder="https://myorg.my.salesforce.com"
                  disabled={isRunning}
                  autoComplete="off"
                />
              </div>
              <div>
                <label className="label">Access Token</label>
                <div className="relative">
                  <input
                    className="input pr-16"
                    type={showToken ? 'text' : 'password'}
                    value={accessToken}
                    onChange={e => setAccessToken(e.target.value)}
                    placeholder="00D…"
                    disabled={isRunning}
                    autoComplete="off"
                  />
                  <button
                    type="button"
                    onClick={() => setShowToken(v => !v)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-gray-400 hover:text-gray-600 px-1"
                  >
                    {showToken ? 'Hide' : 'Show'}
                  </button>
                </div>
              </div>
            </div>

            {/* Row 2: pipeline params + controls */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 items-end">
              <div>
                <label className="label">Batch Size (SOQL LIMIT)</label>
                <input
                  className="input"
                  type="number"
                  min={1}
                  max={50000}
                  value={batchSize}
                  onChange={e => setBatchSize(Number(e.target.value))}
                  disabled={isRunning}
                />
              </div>
              <div>
                <label className="label">Worker Threads</label>
                <input
                  className="input"
                  type="number"
                  min={1}
                  max={50}
                  value={threads}
                  onChange={e => setThreads(Number(e.target.value))}
                  disabled={isRunning}
                />
              </div>
              <div className="flex gap-2">
                <button className="btn-primary flex-1" onClick={handleStart} disabled={!canStart}>
                  ▶ Start
                </button>
                <button className="btn-danger flex-1" onClick={stopPipeline} disabled={!canStop}>
                  ■ Stop
                </button>
              </div>
            </div>

          </div>

          {state.error && (
            <div className="mx-4 mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded text-sm">
              {state.error}
            </div>
          )}
        </div>

        <WorkerMonitor workers={state.workers} progress={state.progress} />
        <EventLog events={state.events} />

      </main>

      <footer className="text-center text-xs text-gray-400 py-3 border-t border-sf-border">
        SF Async Data Pipeline · Spring Boot + React · Salesforce REST API
      </footer>
    </div>
  )
}
