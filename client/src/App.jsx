import React, { useState, useEffect } from 'react'
import { usePipeline } from './hooks/usePipeline.js'
import { useJobs } from './hooks/useJobs.js'
import { useProgress } from './hooks/useProgress.js'
import { useErrors } from './hooks/useErrors.js'
import { useOrgConnection } from './hooks/useOrgConnection.js'
import WorkerMonitor from './components/WorkerMonitor.jsx'
import EventLog from './components/EventLog.jsx'
import ConnectionPanel from './components/ConnectionPanel.jsx'
import ErrorPanel from './components/ErrorPanel.jsx'

function StatusDot({ connected }) {
  return (
    <span className="flex items-center gap-1.5 text-xs">
      <span className={`w-2 h-2 rounded-full ${connected ? 'bg-green-400' : 'bg-red-400'}`} />
      {connected ? 'Server' : 'Disconnected'}
    </span>
  )
}

function OrgDot({ orgStatus }) {
  if (orgStatus.status === 'idle') return null
  const dot = {
    verifying: 'bg-yellow-400 animate-pulse',
    verified:  'bg-green-400',
    error:     'bg-red-400',
  }[orgStatus.status]
  const label = orgStatus.status === 'verified'
    ? (orgStatus.username || 'Org verified')
    : orgStatus.status === 'verifying'
    ? 'Verifying…'
    : 'Auth error'
  return (
    <span className="flex items-center gap-1.5 text-xs">
      <span className={`w-2 h-2 rounded-full ${dot}`} />
      {label}
    </span>
  )
}

const STATUS_BADGE = {
  running:   'bg-yellow-100 text-yellow-800 border-yellow-300',
  stopped:   'bg-orange-100 text-orange-800 border-orange-300',
  error:     'bg-red-100 text-red-800 border-red-300',
  completed: 'bg-green-100 text-green-800 border-green-300',
}

function fmtDuration(startedAt, finishedAt) {
  if (!startedAt) return null
  const ms = (finishedAt ? new Date(finishedAt) : new Date()) - new Date(startedAt)
  const s = Math.round(ms / 1000)
  if (s < 60) return `${s}s`
  if (s < 3600) return `${Math.floor(s / 60)}m ${s % 60}s`
  return `${Math.floor(s / 3600)}h ${Math.floor((s % 3600) / 60)}m`
}

function ResumeBar({ prog, jobName, onResume, onFresh, disabled }) {
  if (!prog) return null

  const canResume = prog.lastId && prog.status !== 'completed'
  const badgeCls  = STATUS_BADGE[prog.status] || STATUS_BADGE.stopped
  const duration  = fmtDuration(prog.startedAt, prog.finishedAt)

  return (
    <div className="rounded border border-sf-border bg-gray-50 px-4 py-3 flex flex-col sm:flex-row sm:items-center gap-3">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className={`text-xs font-medium px-2 py-0.5 rounded border ${badgeCls}`}>
            {prog.status}
          </span>
          <span className="text-sm font-medium text-gray-700">
            Previous run{jobName ? ` · ${jobName}` : ''}
          </span>
          <span className="text-xs text-gray-500">
            {prog.totalCount > 0
              ? `${prog.totalProcessed.toLocaleString()} / ${prog.totalCount.toLocaleString()} (${((prog.totalProcessed / prog.totalCount) * 100).toFixed(1)}%)`
              : `${prog.totalProcessed.toLocaleString()} records`
            } · Batch {prog.batchNum}
          </span>
          {duration && (
            <span className="text-xs text-gray-400">· {duration}</span>
          )}
        </div>
        <div className="mt-1 flex flex-wrap gap-x-3 text-xs text-gray-400">
          {prog.startedAt && (
            <span>Started {new Date(prog.startedAt).toLocaleString()}</span>
          )}
          {prog.finishedAt && (
            <span>Finished {new Date(prog.finishedAt).toLocaleString()}</span>
          )}
        </div>
        {prog.totalCount > 0 && (
          <div className="mt-2 w-full h-1.5 bg-gray-200 rounded-full overflow-hidden">
            <div
              className="h-full bg-sf-blue rounded-full"
              style={{ width: `${Math.min(100, (prog.totalProcessed / prog.totalCount) * 100)}%` }}
            />
          </div>
        )}
        {prog.lastId && (
          <div className="mt-1 text-xs text-gray-500 font-mono truncate">
            Last ID: {prog.lastId}
          </div>
        )}
      </div>
      <div className="flex gap-2 shrink-0">
        {canResume && (
          <button
            className="btn-primary text-xs px-3 py-1"
            onClick={onResume}
            disabled={disabled}
          >
            ↩ Resume
          </button>
        )}
        <button
          className="btn-secondary text-xs px-3 py-1"
          onClick={onFresh}
          disabled={disabled}
        >
          ↺ Start Fresh
        </button>
      </div>
    </div>
  )
}

export default function App() {
  const { state, startPipeline, retryErrors, stopPipeline } = usePipeline()
  const { jobs, selectedJob, selectJob, loading: jobsLoading, reload: reloadJobs } = useJobs()
  const { progress: allProgress, refresh: refreshProgress } = useProgress()
  const { errors, loading: errorsLoading, fetchErrors, clearErrors } = useErrors()
  const { orgs, cliAvailable, orgsLoading, fetchOrgs, loadOrgToken, orgStatus, verify, clearOrgStatus } = useOrgConnection()

  const [instanceUrl, setInstanceUrl]   = useState(() => localStorage.getItem('sf_instanceUrl') || '')
  const [accessToken, setAccessToken]   = useState(() => localStorage.getItem('sf_accessToken') || '')

  function handleSetInstanceUrl(v) { setInstanceUrl(v); clearOrgStatus() }
  function handleSetAccessToken(v) { setAccessToken(v); clearOrgStatus() }
  const [batchSize, setBatchSize]       = useState(1000)
  const [threads, setThreads]           = useState(5)
  const [params, setParams]             = useState({})

  useEffect(() => {
    if (selectedJob) {
      setBatchSize(selectedJob.defaultBatchSize || 1000)
      setThreads(selectedJob.defaultThreads || 5)
      const initial = {}
      for (const rp of (selectedJob.runtimeParams || [])) {
        if (!initial[rp.plugin]) initial[rp.plugin] = {}
        initial[rp.plugin][rp.key] = rp.defaultValue || ''
      }
      setParams(initial)
    }
  }, [selectedJob?.id])

  // Refresh progress and errors from DB after each run finishes
  useEffect(() => {
    if (state.status === 'idle' || state.status === 'completed') {
      refreshProgress()
      if (selectedJob && instanceUrl.trim()) {
        fetchErrors(selectedJob.id, instanceUrl.trim())
      }
    }
  }, [state.status])

  // Reload errors when job or instance changes
  useEffect(() => {
    if (selectedJob && instanceUrl.trim()) {
      fetchErrors(selectedJob.id, instanceUrl.trim())
    }
  }, [selectedJob?.id, instanceUrl])

  const savedProgress = selectedJob && instanceUrl.trim()
    ? allProgress.find(p => p.jobId === selectedJob.id && p.instanceUrl === instanceUrl.trim())
    : null

  const isRunning = state.status === 'running' || state.status === 'stopping'
  const runtimeParamsFilled = (selectedJob?.runtimeParams || [])
    .filter(rp => rp.required)
    .every(rp => params[rp.plugin]?.[rp.key]?.trim())
  const canStart = state.connected && selectedJob && instanceUrl.trim() && accessToken.trim() && runtimeParamsFilled && !isRunning
  const canStop  = state.status === 'running'

  function handleStart(fresh = true) {
    if (!canStart) return
    const objectType = Object.values(params).flatMap(p => Object.values(p))[0] || selectedJob.id
    const action = fresh ? 'Start fresh' : 'Resume'
    const confirmed = window.confirm(`${action} "${selectedJob.name}" on ${objectType}?`)
    if (confirmed) startPipeline(selectedJob.id, instanceUrl.trim(), accessToken.trim(), batchSize, threads, params, fresh)
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
          <OrgDot orgStatus={orgStatus} />
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
              {jobs.map(job => {
                const jobProg = allProgress.find(p => p.jobId === job.id)
                return (
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
                    <div className="text-xs text-gray-500 flex items-center gap-1.5">
                      <span>v{job.version} · {(job.plugins || []).length} plugin(s)</span>
                      {jobProg && (
                        <span className={`px-1.5 py-0.5 rounded border text-xs ${STATUS_BADGE[jobProg.status] || STATUS_BADGE.stopped}`}>
                          {jobProg.totalProcessed.toLocaleString()} rec
                        </span>
                      )}
                    </div>
                  </button>
                )
              })}
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

        {/* Connection details */}
        <ConnectionPanel
          instanceUrl={instanceUrl}
          setInstanceUrl={handleSetInstanceUrl}
          accessToken={accessToken}
          setAccessToken={handleSetAccessToken}
          isRunning={isRunning}
          orgs={orgs}
          cliAvailable={cliAvailable}
          orgsLoading={orgsLoading}
          onReloadOrgs={fetchOrgs}
          onLoadOrgToken={loadOrgToken}
          orgStatus={orgStatus}
          onVerify={verify}
        />

        {/* Pipeline config + controls */}
        <div className="card">
          <div className="card-header">Configuration &amp; Controls</div>
          <div className="p-4 space-y-3">

            {/* Runtime params (job-defined, dynamic) */}
            {(selectedJob?.runtimeParams || []).length > 0 && (
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                {(selectedJob.runtimeParams || []).map(rp => (
                  <div key={`${rp.plugin}.${rp.key}`} className={rp.type === 'textarea' ? 'lg:col-span-2' : ''}>
                    <label className="label">
                      {rp.label}
                      {rp.required && <span className="text-red-400 ml-0.5">*</span>}
                    </label>
                    {rp.type === 'textarea' ? (
                      <textarea
                        className="input font-mono text-xs resize-y"
                        rows={8}
                        value={params[rp.plugin]?.[rp.key] || ''}
                        onChange={e => setParams(prev => ({
                          ...prev,
                          [rp.plugin]: { ...prev[rp.plugin], [rp.key]: e.target.value }
                        }))}
                        placeholder={rp.placeholder || ''}
                        disabled={isRunning}
                        spellCheck={false}
                      />
                    ) : (
                      <input
                        className="input"
                        type={rp.type || 'text'}
                        value={params[rp.plugin]?.[rp.key] || ''}
                        onChange={e => setParams(prev => ({
                          ...prev,
                          [rp.plugin]: { ...prev[rp.plugin], [rp.key]: e.target.value }
                        }))}
                        placeholder={rp.placeholder || ''}
                        disabled={isRunning}
                      />
                    )}
                  </div>
                ))}
              </div>
            )}

            {/* Previous run / resume banner */}
            {savedProgress && (
              <ResumeBar
                prog={savedProgress}
                jobName={selectedJob?.name}
                onResume={() => handleStart(false)}
                onFresh={() => handleStart(true)}
                disabled={!canStart}
              />
            )}

            {/* Row 2: pipeline params + controls */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 items-end">
              <div>
                <label className="label">Page Size (records per batch)</label>
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
                <button
                  className="btn-primary flex-1"
                  onClick={() => handleStart(true)}
                  disabled={!canStart}
                >
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

        <ErrorPanel
          errors={isRunning ? state.recordErrors : errors}
          loading={errorsLoading}
          disabled={isRunning}
          onRetry={() => {
            if (!selectedJob || !instanceUrl.trim() || !accessToken.trim()) return
            if (window.confirm(`Retry ${errors.length} failed record(s) for "${selectedJob.name}"?`)) {
              retryErrors(selectedJob.id, instanceUrl.trim(), accessToken.trim(), threads, params)
            }
          }}
          onClear={() => {
            if (selectedJob && instanceUrl.trim()) clearErrors(selectedJob.id, instanceUrl.trim())
          }}
          onRefresh={() => {
            if (selectedJob && instanceUrl.trim()) fetchErrors(selectedJob.id, instanceUrl.trim())
          }}
        />

        <EventLog events={state.events} />

      </main>

      <footer className="text-center text-xs text-gray-400 py-3 border-t border-sf-border">
        SF Async Data Pipeline
      </footer>
    </div>
  )
}
