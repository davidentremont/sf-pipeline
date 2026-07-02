import React from 'react'

export default function ErrorPanel({ errors, loading, onRetry, onClear, onRefresh, disabled }) {
  if (errors.length === 0 && !loading) return null

  return (
    <div className="card border-red-200">
      <div className="card-header flex items-center justify-between bg-red-50 border-b border-red-200">
        <span className="flex items-center gap-2">
          Failed Records
          <span className="bg-red-500 text-white text-xs font-bold px-1.5 py-0.5 rounded-full">
            {errors.length}
          </span>
        </span>
        <div className="flex gap-2">
          <button className="text-xs text-sf-blue hover:underline" onClick={onRefresh} disabled={loading}>
            Refresh
          </button>
          <button
            className="btn-primary text-xs px-3 py-1"
            onClick={onRetry}
            disabled={disabled || errors.length === 0}
          >
            ↩ Retry Failed
          </button>
          <button
            className="btn-secondary text-xs px-3 py-1"
            onClick={onClear}
            disabled={disabled}
          >
            Clear
          </button>
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
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
      </div>
    </div>
  )
}
