import React, { useState } from 'react'

export default function ConnectionPanel({
  instanceUrl, setInstanceUrl,
  accessToken, setAccessToken,
  isRunning,
  orgs, cliAvailable, orgsLoading, onReloadOrgs,
  onLoadOrgToken,
  orgStatus, onVerify,
}) {
  const [open, setOpen] = useState(!instanceUrl || !accessToken)
  const [mode, setMode] = useState('manual')
  const [showToken, setShowToken] = useState(false)
  const [loadingToken, setLoadingToken] = useState(false)
  const [tokenError, setTokenError] = useState(null)

  const domain = instanceUrl
    ? (() => { try { return new URL(instanceUrl).hostname } catch { return instanceUrl } })()
    : null

  async function handleOrgSelect(e) {
    const aliasOrUsername = e.target.value
    if (!aliasOrUsername) return
    setLoadingToken(true)
    setTokenError(null)
    try {
      const data = await onLoadOrgToken(aliasOrUsername)
      setInstanceUrl(data.instanceUrl)
      setAccessToken(data.accessToken)
      localStorage.setItem('sf_instanceUrl', data.instanceUrl)
      localStorage.setItem('sf_accessToken', data.accessToken)
    } catch (err) {
      setTokenError(err.message)
    } finally {
      setLoadingToken(false)
    }
  }

  const verifyLabel = {
    idle:      'Verify Connection',
    verifying: 'Verifying…',
    verified:  '✓ Verified',
    error:     '✗ Retry Verify',
  }[orgStatus.status] ?? 'Verify Connection'

  const verifyClass = {
    idle:      'btn-secondary',
    verifying: 'btn-secondary opacity-60 cursor-not-allowed',
    verified:  'btn-secondary border-green-400 text-green-700',
    error:     'btn-secondary border-red-400 text-red-600',
  }[orgStatus.status] ?? 'btn-secondary'

  return (
    <div className="card">
      <button
        type="button"
        className="card-header w-full flex items-center justify-between cursor-pointer select-none"
        onClick={() => setOpen(v => !v)}
      >
        <span>Salesforce Connection</span>
        <div className="flex items-center gap-3">
          {!open && orgStatus.status === 'verified' && (
            <span className="text-xs font-normal normal-case tracking-normal text-green-600">
              ✓ {orgStatus.username || domain}
            </span>
          )}
          {!open && orgStatus.status !== 'verified' && domain && (
            <span className="text-xs font-normal normal-case tracking-normal text-gray-500 font-mono">{domain}</span>
          )}
          {!open && accessToken && orgStatus.status !== 'verified' && (
            <span className="text-xs font-normal normal-case tracking-normal text-gray-400">token saved</span>
          )}
          {(!instanceUrl || !accessToken) && (
            <span className="text-xs font-normal normal-case tracking-normal text-red-400">required</span>
          )}
          <span className="text-gray-400 text-sm">{open ? '▲' : '▼'}</span>
        </div>
      </button>

      {open && (
        <div className="p-4 space-y-4">

          {/* Mode tabs */}
          <div className="flex gap-1 bg-gray-100 rounded p-0.5 w-fit">
            <button
              type="button"
              onClick={() => setMode('manual')}
              className={`text-xs px-3 py-1 rounded transition-colors ${
                mode === 'manual' ? 'bg-white shadow text-sf-dark font-medium' : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              Manual Entry
            </button>
            <button
              type="button"
              onClick={() => setMode('cli')}
              className={`text-xs px-3 py-1 rounded transition-colors ${
                mode === 'cli' ? 'bg-white shadow text-sf-dark font-medium' : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              SF CLI Orgs
            </button>
          </div>

          {mode === 'cli' && (
            <div className="space-y-2">
              {!cliAvailable && !orgsLoading && (
                <div className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded px-3 py-2">
                  SF CLI not found — install the Salesforce CLI and authenticate with <code>sf org login web</code>.
                </div>
              )}
              <div className="flex gap-2 items-end">
                <div className="flex-1">
                  <label className="label">Connected Org</label>
                  <select
                    className="input"
                    onChange={handleOrgSelect}
                    defaultValue=""
                    disabled={isRunning || loadingToken || !cliAvailable}
                  >
                    <option value="" disabled>
                      {orgsLoading ? 'Loading orgs…' : cliAvailable ? `Select org (${orgs.length} found)` : 'SF CLI unavailable'}
                    </option>
                    {orgs.map(org => (
                      <option key={org.username} value={org.alias || org.username}>
                        {org.alias ? `${org.alias} — ` : ''}{org.username}
                        {org.defaultUsername ? ' (default)' : ''}
                        {org.connectedStatus && org.connectedStatus !== 'Unknown' ? ` [${org.connectedStatus}]` : ''}
                      </option>
                    ))}
                  </select>
                </div>
                <button
                  type="button"
                  className="btn-secondary text-xs px-3 py-2 whitespace-nowrap"
                  onClick={onReloadOrgs}
                  disabled={orgsLoading}
                >
                  {orgsLoading ? '…' : '⟳ Refresh'}
                </button>
              </div>
              {loadingToken && <div className="text-xs text-gray-500">Loading token…</div>}
              {tokenError && <div className="text-xs text-red-600">{tokenError}</div>}
              {instanceUrl && accessToken && (
                <div className="text-xs text-gray-500 font-mono truncate">
                  {instanceUrl}
                </div>
              )}
            </div>
          )}

          {mode === 'manual' && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <div>
                <label className="label">Instance URL</label>
                <input
                  className="input"
                  type="text"
                  value={instanceUrl}
                  onChange={e => { setInstanceUrl(e.target.value); localStorage.setItem('sf_instanceUrl', e.target.value) }}
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
                    onChange={e => { setAccessToken(e.target.value); localStorage.setItem('sf_accessToken', e.target.value) }}
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
          )}

          {/* Verify row */}
          <div className="flex items-center gap-3">
            <button
              type="button"
              className={`text-xs px-4 py-1.5 ${verifyClass}`}
              onClick={() => onVerify(instanceUrl, accessToken)}
              disabled={!instanceUrl || !accessToken || orgStatus.status === 'verifying'}
            >
              {verifyLabel}
            </button>
            {orgStatus.status === 'verified' && (
              <span className="text-xs text-green-700">
                {orgStatus.username}
              </span>
            )}
            {orgStatus.status === 'error' && (
              <span className="text-xs text-red-600 truncate max-w-xs" title={orgStatus.error}>
                {orgStatus.error}
              </span>
            )}
          </div>

        </div>
      )}
    </div>
  )
}
