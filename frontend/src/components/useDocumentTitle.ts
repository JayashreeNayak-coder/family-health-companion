import { useEffect } from 'react'

const APP = 'Family Health Companion'

/** Names the browser tab after the page (WCAG 2.4.2), such as "Ravi Demo · Lab results". */
export function useDocumentTitle(...parts: (string | null | undefined)[]) {
  const title = [...parts.filter(Boolean), APP].join(' · ')
  useEffect(() => {
    document.title = title
  }, [title])
}
