package com.economicbriefing.api;

import java.util.List;

import com.economicbriefing.admin.dto.ApiResponse;
import com.economicbriefing.economicflow.EconomicFlowGraphRepository;
import com.economicbriefing.economicflow.repository.EconomicEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/economic-network")
public class EconomicNetworkController {
    private final EconomicEventRepository events;
    private final EconomicFlowGraphRepository graph;

    public EconomicNetworkController(EconomicEventRepository events, EconomicFlowGraphRepository graph) {
        this.events = events;
        this.graph = graph;
    }

    @GetMapping("/nodes/search")
    public ApiResponse<List<NodeResult>> search(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return ApiResponse.ok(List.of());
        return ApiResponse.ok(events.findTop20ByTitleContainingIgnoreCaseOrderByIdDesc(q.trim()).stream()
                .map(event -> new NodeResult(event.getId(), event.getTitle(), event.getEventDate()))
                .toList());
    }

    @GetMapping("/{nodeId}")
    public ResponseEntity<ApiResponse<GraphResult>> graph(
            @PathVariable long nodeId, @RequestParam(defaultValue = "2") int depth) {
        if (depth < 1 || depth > 3) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "INVALID_DEPTH", "depth는 1부터 3까지 지정할 수 있습니다."));
        }
        return graph.loadAround(nodeId, depth)
                .map(batch -> ResponseEntity.ok(ApiResponse.ok(toResult(nodeId, depth, batch))))
                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error(
                        "NODE_NOT_FOUND", "경제 노드를 찾을 수 없습니다.")));
    }

    @GetMapping("/overview")
    public ApiResponse<GraphResult> overview() {
        var batch = graph.loadOverview();
        return ApiResponse.ok(toResult(null, 0, batch));
    }

    private GraphResult toResult(Long centerNodeId, int depth,
            EconomicFlowGraphRepository.GraphBatch batch) {
        return new GraphResult(
                centerNodeId,
                depth,
                batch.nodes().stream()
                        .map(node -> new NodeResult(node.nodeId(), node.title(), node.eventDate()))
                        .toList(),
                batch.edges().stream()
                        .map(edge -> new EdgeResult(
                                edge.fromNodeId() + "-" + edge.toNodeId() + "-" + edge.relationType(),
                                edge.fromNodeId(), edge.toNodeId(), edge.relationType().name()))
                        .toList(),
                batch.nodes().size() >= 50);
    }

    public record NodeResult(Long id, String label, java.time.LocalDate eventDate) {}
    public record EdgeResult(String id, Long source, Long target, String relationType) {}
    public record GraphResult(Long centerNodeId, int depth, List<NodeResult> nodes, List<EdgeResult> links,
            boolean limited) {}
}
