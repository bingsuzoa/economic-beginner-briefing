import { useEffect, useMemo, useRef, useState } from 'react'
import ForceGraph3D from 'react-force-graph-3d'
import s from './EconomicNetwork.module.css'
import { apiFetch } from '../api'

const nodeId = (value) => String(typeof value === 'object' ? value.id : value)

function webGlAvailable() {
  try {
    const canvas = document.createElement('canvas')
    return Boolean(canvas.getContext('webgl2') || canvas.getContext('webgl'))
  } catch {
    return false
  }
}

export default function EconomicNetwork() {
  const graphRef = useRef()
  const containerRef = useRef()
  const [selected, setSelected] = useState(null)
  const [graph, setGraph] = useState({ nodes: [], links: [] })
  const [size, setSize] = useState({ width: 800, height: 560 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [limited, setLimited] = useState(false)
  const [hasWebGl] = useState(webGlAvailable)

  useEffect(() => {
    const observer = new ResizeObserver(([entry]) => setSize({
      width: Math.floor(entry.contentRect.width),
      height: Math.floor(entry.contentRect.height),
    }))
    if (containerRef.current) observer.observe(containerRef.current)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    apiFetch('/api/economic-network/overview')
      .then((response) => response.ok ? response.json() : Promise.reject())
      .then((body) => {
        setGraph({
          nodes: body.data.nodes.map((node) => ({ ...node, id: String(node.id) })),
          links: body.data.links.map((link) => ({
            ...link, source: String(link.source), target: String(link.target),
          })),
        })
        setLimited(body.data.limited)
      })
      .catch(() => setError('경제망을 불러오지 못했어요. 잠시 후 다시 시도해주세요.'))
      .finally(() => setLoading(false))
  }, [])

  const connected = useMemo(() => {
    if (!selected) return new Set()
    const ids = new Set([String(selected.id)])
    graph.links.forEach((link) => {
      if (nodeId(link.source) === String(selected.id)) ids.add(nodeId(link.target))
      if (nodeId(link.target) === String(selected.id)) ids.add(nodeId(link.source))
    })
    return ids
  }, [graph.links, selected])

  const relations = useMemo(() => {
    if (!selected) return { incoming: [], outgoing: [] }
    const byId = new Map(graph.nodes.map((node) => [String(node.id), node]))
    return graph.links.reduce((result, link) => {
      if (nodeId(link.target) === String(selected.id)) {
        result.incoming.push({ node: byId.get(nodeId(link.source)), type: link.relationType })
      }
      if (nodeId(link.source) === String(selected.id)) {
        result.outgoing.push({ node: byId.get(nodeId(link.target)), type: link.relationType })
      }
      return result
    }, { incoming: [], outgoing: [] })
  }, [graph, selected])

  function focusNode(node) {
    setSelected(node)
    if (!graphRef.current || node.x == null) return
    const distance = 75
    const length = Math.hypot(node.x, node.y, node.z) || 1
    const ratio = 1 + distance / length
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    graphRef.current.cameraPosition(
      { x: node.x * ratio, y: node.y * ratio, z: node.z * ratio },
      { x: node.x, y: node.y, z: node.z },
      reducedMotion ? 0 : 650,
    )
  }

  function showOverview() {
    setSelected(null)
    graphRef.current?.zoomToFit(
      window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 650,
      60,
    )
  }

  return (
    <section className={s.section}>
      <header>
        <p className={s.eyebrow}>THOTH ECONOMIC NETWORK</p>
        <h1>토트 경제망</h1>
        <p>경제 세계를 돌려보고, 궁금한 지점을 누르면 연결된 원인과 결과를 가까이 볼 수 있어요.</p>
      </header>

      <div className={`${s.workspace} ${selected ? s.withDetail : ''}`}>
        <div className={s.graph} ref={containerRef}>
          {!hasWebGl && <div className={s.empty}>이 기기에서는 3D 그래픽을 사용할 수 없어요.</div>}
          {loading && <div className={s.message}>전체 경제망을 만들고 있어요...</div>}
          {error && <div className={s.message}>{error}</div>}
          {!loading && !error && graph.nodes.length === 0 &&
            <div className={s.empty}>아직 저장된 경제 흐름이 없어요.</div>}
          {hasWebGl && !loading && !error && graph.nodes.length > 0 && <ForceGraph3D
            ref={graphRef} width={size.width} height={size.height} graphData={graph}
            backgroundColor="#111827" showNavInfo={false} controlType="orbit"
            enableNavigationControls cooldownTicks={100} warmupTicks={20}
            nodeLabel="label" nodeRelSize={5}
            nodeVal={(node) => String(node.id) === String(selected?.id) ? 2.2 : 1}
            nodeColor={(node) => !selected || connected.has(String(node.id))
              ? String(node.id) === String(selected?.id) ? '#FFD43B' : '#68D8D6'
              : '#26364D'}
            linkColor={(link) => selected && (nodeId(link.source) === String(selected.id)
              || nodeId(link.target) === String(selected.id)) ? '#FFD43B' : '#334155'}
            linkWidth={(link) => selected && (nodeId(link.source) === String(selected.id)
              || nodeId(link.target) === String(selected.id)) ? 2 : .7}
            linkDirectionalArrowLength={4} linkDirectionalArrowRelPos={1}
            onNodeClick={focusNode} onBackgroundClick={showOverview}
            onEngineStop={() => !selected && graphRef.current?.zoomToFit(500, 60)}
          />}
        </div>

        {selected && <aside className={s.detail}>
          <p className={s.detailLabel}>선택한 경제 현상</p>
          <h2>{selected.label}</h2>
          <RelationList title="원인" arrow="←" items={relations.incoming} />
          <RelationList title="결과" arrow="→" items={relations.outgoing} />
          <button className={s.recenter} onClick={showOverview}>전체 경제망으로 돌아가기</button>
        </aside>}
      </div>
      <p className={s.guide}>드래그해서 회전 · 두 손가락으로 확대/축소 · 노드를 눌러 가까이 보기</p>
      {limited && <p className={s.limit}>화면 성능을 위해 최근 경제 노드 50개를 표시하고 있어요.</p>}
    </section>
  )
}

function RelationList({ title, arrow, items }) {
  return <div className={s.relations}>
    <h3>{title}</h3>
    {items.length === 0 ? <p className={s.none}>직접 연결된 {title}가 없어요.</p> : items.map((item, index) =>
      <p key={`${item.node?.id}-${item.type}-${index}`}><span>{arrow}</span> {item.node?.label}
        <small>{item.type}</small></p>)}
  </div>
}
