import React, { useRef, useEffect } from 'react'

const LEVEL_STYLE = {
  info:    'text-gray-700',
  success: 'text-green-700 font-medium',
  error:   'text-red-600 font-medium',
}

export default function EventLog({ events }) {
  const bottomRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [events.length])

  return (
    <div className="card">
      <div className="card-header">Event Log</div>
      <div className="p-3 h-48 overflow-y-auto font-mono text-xs space-y-0.5 bg-gray-50 rounded-b-lg">
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
    </div>
  )
}
