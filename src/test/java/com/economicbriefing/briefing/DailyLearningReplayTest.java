package com.economicbriefing.briefing;

import com.economicbriefing.article.*;
import com.economicbriefing.config.*;
import com.economicbriefing.llm.OpenAiClient;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Fixed-day evaluation in an isolated corpus. Matching model requests reuse recorded responses. */
@EnabledIfEnvironmentVariable(named="DAILY_LEARNING_DB",matches="economic_briefing_review_[a-z0-9_]+")
class DailyLearningReplayTest {
    @Test void replayFixedDay() throws Exception {
        String db=System.getenv("DAILY_LEARNING_DB");
        if (!db.matches("economic_briefing_review_[a-z0-9_]+")) throw new IllegalArgumentException("Isolated DB required");
        Path base=Path.of(System.getenv("DAILY_LEARNING_ROOT"));
        Path output=base.resolve(System.getenv("DAILY_LEARNING_ATTEMPT"));
        Files.createDirectory(output);
        String mode=System.getenv().getOrDefault("DAILY_LEARNING_MODE","pipeline");
        var json=new ObjectMapper().findAndRegisterModules();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://localhost:5432/"+db+"?stringtype=unspecified",
                System.getenv("PGUSER"),System.getenv("PGPASSWORD")));
        LocalDate date=LocalDate.parse("2026-09-12");
        List<ArticleEntity> window=new ArrayList<>();
        for(var value:json.readTree(base.resolve("window.json").toFile())) window.add(article(value));
        List<ArticleEntity> feedback=new ArrayList<>();
        for(var value:json.readTree(base.resolve("feedback-snapshots.json").toFile())) feedback.add(article(value));
        // Only this explicit opt-in isolated database receives the recovered source revisions.
        for(var a:feedback) jdbc.update("""
                INSERT INTO articles (id,source,source_article_id,title,summary,body,body_status,url,published_at,
                    collected_at,body_fetched_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,now(),?,now(),now())
                ON CONFLICT(id) DO UPDATE SET title=excluded.title,summary=excluded.summary,body=excluded.body,
                    body_status=excluded.body_status,url=excluded.url,published_at=excluded.published_at,
                    body_fetched_at=excluded.body_fetched_at
                """,a.getId(),a.getSource(),a.getSourceArticleId(),a.getTitle(),a.getSummary(),a.getBody(),
                a.getBodyStatus(),a.getUrl(),a.getPublishedAt(),a.getBodyFetchedAt());
        Map<String,ArticleEntity> all=new LinkedHashMap<>(); window.forEach(a->all.put(a.getId(),a)); feedback.forEach(a->all.put(a.getId(),a));
        window=new ArrayList<>(all.values()); window.sort(Comparator.comparing(ArticleEntity::getPublishedAt));
        var baseline=json.readTree(base.resolve("baseline-run.json").toFile());
        Set<String> candidateIds=new LinkedHashSet<>(); baseline.path("trace_json").path("prefilteredArticleIds").forEach(v->candidateIds.add(v.asText()));
        feedback.forEach(a->candidateIds.add(a.getId()));
        List<ArticleEntity> candidates=window.stream().filter(a->candidateIds.contains(a.getId())).toList();
        var properties=new OpenAiProperties(System.getenv("OPENAI_API_KEY"),Duration.ofSeconds(240),
                "gpt-5.6-luna","gpt-5.6-luna","gpt-5.6-terra","gpt-5.6-luna","text-embedding-3-large");
        var app=new AppProperties(new AppProperties.TimeoutProperties(Duration.ofSeconds(30)),
                new AppProperties.SchedulerProperties(false,"0 5 * * * *","0 10 5 * * *"),
                new AppProperties.BudgetProperties(50000,100000,15000,.14));
        var splitter=new ParagraphSplitter(); var client=new RecordedClient(properties,json,base.resolve("cache"),output);
        boolean frozenPrefilter=!"full".equals(mode);
        var llm=new EconomicFlowLlm(client,properties,json,splitter) {
            @Override public Call<List<String>> prefilter(LocalDate d,List<ArticleEntity> pool,int limit) {
                if (!frozenPrefilter) return super.prefilter(d,pool,limit);
                return new Call<>(pool.stream().map(ArticleEntity::getId).filter(candidateIds::contains).toList(),new OpenAiClient.Usage(0,0,0,0));
            }
        };
        var summary=json.createObjectNode().put("targetDate",date.toString()).put("mode",mode).put("candidateCount",candidates.size());
        try {
            if ("selection".equals(mode)) {
                var selected=llm.select(date,candidates); summary.set("selection",json.valueToTree(selected.value()));
            } else if ("extraction".equals(mode)) {
                var extracted=summary.putArray("extractions");
                for(var a:feedback) {
                    var result=llm.extract(new EconomicFlowLlm.SelectedArticle(a,"오늘 경제흐름의 원인·행동·반대 압력·제약을 설명하는 원문"),splitter.split(a.getBody()));
                    var item=extracted.addObject().put("articleId",a.getId()); item.set("call",json.valueToTree(result));
                }
            } else {
                // Replay from the same source corpus; let the request cache reproduce extraction usage.
                for(var a:window) jdbc.update("DELETE FROM article_observations WHERE article_id=?",a.getId());
                var articles=mock(ArticleRepository.class);
                List<ArticleEntity> frozenWindow=List.copyOf(window);
                when(articles.findByPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtAsc(any(),any())).thenReturn(frozenWindow);
                when(articles.findById(anyString())).thenAnswer(call->{
                    String id=call.getArgument(0); if(all.containsKey(id))return Optional.of(all.get(id));
                    var rows=jdbc.query("SELECT row_to_json(a)::text FROM articles a WHERE id=?",(rs,n)->rs.getString(1),id);
                    return rows.isEmpty()?Optional.empty():Optional.of(article(json.readTree(rows.getFirst())));
                });
                when(articles.save(any())).thenAnswer(call->{
                    ArticleEntity a=call.getArgument(0); all.put(a.getId(),a);
                    jdbc.update("UPDATE articles SET body=?,body_status=?,body_fetched_at=? WHERE id=?",a.getBody(),a.getBodyStatus(),a.getBodyFetchedAt(),a.getId()); return a;
                });
                var bodyCache=base.resolve("body-cache");Files.createDirectories(bodyCache);
                var fetcher=new YonhapBodyFetcher(app){@Override public String fetch(String url){
                    try {var file=bodyCache.resolve(ObservationStore.bodyHash(url)+".txt");
                        if(Files.exists(file))return Files.readString(file);
                        String body=super.fetch(url);Files.writeString(file,body);return body;
                    }catch(Exception e){throw new IllegalStateException(e);}
                }};
                var briefings=mock(DailyBriefingRepository.class);var latest=new AtomicReference<DailyBriefingEntity>();
                when(briefings.save(any())).thenAnswer(call->{latest.set(call.getArgument(0));return call.getArgument(0);});
                var service=new DailyBriefingService(articles,briefings,fetcher,splitter,new ObservationStore(jdbc,json,splitter),
                        new PrincipleStore(jdbc),llm,client,properties,app,json);
                try {var result=service.run(date,"USER_REVIEW",true).orElseThrow();assertEquals("SUCCESS",result.getStatus());
                    int writerOutput=0;for(var request:client.requests)if("economic_flow_writing".equals(request.path("stage").asText()))writerOutput+=request.path("maxOutputTokens").asInt();
                    assertTrue(writerOutput<=EconomicFlowLlm.WRITE_MAX_OUTPUT_TOKENS);}
                finally {var r=latest.get();if(r!=null){summary.put("status",r.getStatus()).put("error",r.getErrorMessage());
                    json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("run.json").toFile(),r);
                    summary.set("usage",json.readTree(r.getUsageJson()));summary.set("trace",json.readTree(r.getTraceJson()));
                    if(r.getResultJson()!=null)summary.set("result",json.readTree(r.getResultJson()));}}
            }
        } finally {
            summary.set("requests",client.requests);summary.put("newCalls",client.newCalls).put("cachedCalls",client.cachedCalls);
            summary.put("newEstimatedCostUsd",client.newCost);
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("result.json").toFile(),summary);
            System.out.println("Replay "+output+": new calls="+client.newCalls+", cache hits="+client.cachedCalls+", cost="+client.newCost);
        }
    }
    private static ArticleEntity article(JsonNode n){
        var a=new ArticleEntity();a.setId(n.path("id").asText());a.setTitle(n.path("title").asText());a.setSummary(n.path("summary").asText());
        a.setSource(n.path("source").asText());a.setSourceArticleId(n.path("source_article_id").asText());a.setUrl(n.path("url").asText());
        a.setBody(n.path("body").asText(null));a.setBodyStatus(n.path("body_status").asText());a.setPublishedAt(OffsetDateTime.parse(n.path("published_at").asText()));
        if(!n.path("body_fetched_at").isNull()&&!n.path("body_fetched_at").isMissingNode())a.setBodyFetchedAt(OffsetDateTime.parse(n.path("body_fetched_at").asText()));return a;
    }
    private static class RecordedClient extends OpenAiClient {
        final ObjectMapper json;final Path cache,output;final ArrayNode requests;int newCalls,cachedCalls;double newCost;
        RecordedClient(OpenAiProperties p,ObjectMapper j,Path c,Path o)throws Exception{super(p,j);json=j;cache=c;output=o;requests=j.createArrayNode();Files.createDirectories(c);}
        @Override public LlmResult complete(String model,String instructions,String input,String reasoning,String verbosity,String name,JsonNode schema,int max){
            try{
                var request=json.createObjectNode().put("model",model).put("instructions",instructions).put("input",input)
                        .put("reasoning",reasoning).put("verbosity",verbosity).put("schemaName",name).put("maxOutputTokens",max);request.set("schema",schema);
                String hash=ObservationStore.bodyHash(request.toString());Path file=cache.resolve(name+"-"+hash+".json");boolean hit=Files.exists(file);
                LlmResult result;
                if(hit){result=json.treeToValue(json.readTree(file.toFile()).path("response"),LlmResult.class);cachedCalls++;}
                else{result=super.complete(model,instructions,input,reasoning,verbosity,name,schema,max);newCalls++;
                    double rate=model.contains("terra")?2:.2;var u=result.usage();newCost+=((u.inputTokens()-u.cachedInputTokens())*rate+u.cachedInputTokens()*rate/10+u.outputTokens()*rate*6)/1_000_000;
                    var saved=json.createObjectNode();saved.set("request",request);saved.set("response",json.valueToTree(result));json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(),saved);}
                requests.addObject().put("stage",name).put("cached",hit).put("cacheFile",file.toString()).put("maxOutputTokens",max).set("usage",json.valueToTree(result.usage()));
                return result; // Preserve logical usage so replay cannot bypass production cost guards.
            }catch(Exception e){throw new IllegalStateException("Recorded "+name+" request failed",e);}
        }
        @Override public EmbeddingResult embed(List<String> input){
            try{String hash=ObservationStore.bodyHash(json.writeValueAsString(input));Path file=cache.resolve("embedding-"+hash+".json");
                if(Files.exists(file)){cachedCalls++;return json.treeToValue(json.readTree(file.toFile()),EmbeddingResult.class);}
                var result=super.embed(input);newCalls++;newCost+=result.inputTokens()*.13/1_000_000;json.writeValue(file.toFile(),result);return result;
            }catch(Exception e){throw new IllegalStateException("Recorded embedding failed",e);}
        }
    }
}
