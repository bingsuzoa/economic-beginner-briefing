import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { briefingParagraphs } from '../frontend/src/components/briefingText.js'

assert.deepEqual(briefingParagraphs(null), [])
assert.deepEqual(briefingParagraphs('첫 문단\n이어지는 문장\n\n둘째 문단'), [
  { heading: '', body: '첫 문단\n이어지는 문장' }, { heading: '', body: '둘째 문단' },
])
assert.deepEqual(briefingParagraphs('**달러 의존을 줄이려 해요.**\r\n이유와 조건이에요.\r\n\r\n**관세는 경고 단계예요.**\n시점을 구별해요.'), [
  { heading: '달러 의존을 줄이려 해요.', body: '이유와 조건이에요.' },
  { heading: '관세는 경고 단계예요.', body: '시점을 구별해요.' },
])
for (const body of ['**제목만 있어요.**', '**제목이 닫히지 않았어요.\n본문', '** **\n본문']) {
  assert.deepEqual(briefingParagraphs(body), [{ heading: '', body }])
}
assert.deepEqual(briefingParagraphs('**<img src=x>**\n<script>text</script>'), [
  { heading: '<img src=x>', body: '<script>text</script>' },
])
console.log('Briefing paragraphs: new headings, old text and malformed markers passed.')

for (const file of process.argv.slice(2)) {
  const data = JSON.parse(readFileSync(file, 'utf8'))
  const flows = (data.result || data).flows
  assert.ok(flows?.length, `${file}: no generated flows`)
  let count = 0
  for (const flow of flows) {
    for (const text of [flow.explanation, ...flow.questions.map(q => q.answer)]) {
      const paragraphs = briefingParagraphs(text)
      assert.ok(paragraphs.length && paragraphs.every(p => p.heading && p.body), `${file}: ${flow.title}`)
      count += paragraphs.length
    }
  }
  console.log(`${file}: ${count} generated paragraphs have headings and body text.`)
}
