export function briefingParagraphs(text) {
  return (text || '').replace(/\r\n?/g, '\n').split(/\n\s*\n/).map(value => value.trim()).filter(Boolean).map(body => {
    const match = body.match(/^\*\*([^\n]+)\*\*[ \t]*\n([\s\S]+)$/)
    return match?.[1].trim() ? { heading: match[1].trim(), body: match[2].trim() } : { heading: '', body }
  })
}
