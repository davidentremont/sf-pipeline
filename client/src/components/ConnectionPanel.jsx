import React, { useState } from 'react'

export default function ConnectionPanel({ instanceUrl, setInstanceUrl, accessToken, setAccessToken, isRunning }) {
  const [open, setOpen] = useState(!instanceUrl || !accessToken)
  const [showToken, setShowToken] = useState(false)

  const domain = instanceUrl ? (() => { try { return new URL(instanceUrl).hostname } catch { return instanceUrl } })() : null

  return (
    <div className="card">
      <button
        type="button"
        className="card-header w-full flex items-center justify-between cursor-pointer select-none"
        onClick={() => setOpen(v => !v)}
      >
        <span>Salesforce Connection</span>
        <div className="flex items-center gap-3">
          {!open && domain && (
            <span className="text-xs font-normal normal-case tracking-normal text-gray-500 font-mono">
              {domain}
            </span>
          )}
          {!open && accessToken && (
            <span className="text-xs font-normal normal-case tracking-normal text-gray-400">token saved</span>
          )}
          {(!instanceUrl || !accessToken) && (
            <span className="text-xs font-normal normal-case tracking-normal text-red-400">required</span>
          )}
          <span className="text-gray-400 text-sm">{open ? '▲' : '▼'}</span>
        </div>
      </button>

      {open && (
        <div className="p-4">
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
        </div>
      )}
    </div>
  )
}
