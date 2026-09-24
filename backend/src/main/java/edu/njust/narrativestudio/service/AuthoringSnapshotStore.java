package edu.njust.narrativestudio.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import edu.njust.narrativestudio.dto.ProjectDtos;
import edu.njust.narrativestudio.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Only authored content is captured. SQL identifiers below are compile-time allowlists, never client input. */
@Component
public class AuthoringSnapshotStore {
    record Table(String name,String columns,String scope) {}
    private static final List<Table> TABLES=List.of(
        new Table("world_entry","id,entry_type,title,content,sort_order","project_id=?"),
        new Table("character_profile","id,name,summary,personality,goal,value_order,status","project_id=?"),
        new Table("story_node","id,node_key,title,content,node_type,scene,is_start,position_x,position_y","project_id=?"),
        new Table("state_variable","id,variable_key,display_name,value_type,initial_value,description,persistence_scope","project_id=?"),
        new Table("story_choice","id,source_node_id,target_node_id,choice_text,sort_order,enabled,unlock_rule","project_id=?"),
        new Table("choice_condition","id,choice_id,variable_id,operator,expected_value,condition_group,sort_order","choice_id IN (SELECT id FROM story_choice WHERE project_id=?)"),
        new Table("state_effect","id,choice_id,variable_id,operation,operand_value,sort_order","choice_id IN (SELECT id FROM story_choice WHERE project_id=?)"),
        new Table("character_relation","id,source_character_id,target_character_id,relation_type,description","project_id=?"),
        new Table("character_knowledge","id,character_id,knowledge_key,knowledge_level,description,acquired_node_id","project_id=?"),
        new Table("story_choice_draft","id,source_node_id,choice_text,sort_order","project_id=?"),
        new Table("node_character","node_id,character_id","node_id IN (SELECT id FROM story_node WHERE project_id=?)")
    );
    private static final Map<String,String> REFS=Map.ofEntries(
        Map.entry("source_node_id","story_node"),Map.entry("target_node_id","story_node"),Map.entry("node_id","story_node"),
        Map.entry("acquired_node_id","story_node"),Map.entry("choice_id","story_choice"),Map.entry("variable_id","state_variable"),
        Map.entry("source_character_id","character_profile"),Map.entry("target_character_id","character_profile"),Map.entry("character_id","character_profile"));
    private static final Map<String,String> UI_REFS=Map.ofEntries(
        Map.entry("nodeId","story_node"),Map.entry("sourceNodeId","story_node"),Map.entry("targetNodeId","story_node"),
        Map.entry("acquiredNodeId","story_node"),Map.entry("choiceId","story_choice"),Map.entry("variableId","state_variable"),
        Map.entry("characterId","character_profile"),Map.entry("sourceCharacterId","character_profile"),Map.entry("targetCharacterId","character_profile"),
        Map.entry("draftId","story_choice_draft"));
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final ProjectService projects;
    public AuthoringSnapshotStore(JdbcTemplate db,ObjectMapper json,ProjectService projects) {this.db=db;this.json=json;this.projects=projects;}
    public ObjectNode capture(Long project,JsonNode drafts) {
        if(drafts==null || !drafts.isObject() || encode(drafts).getBytes(StandardCharsets.UTF_8).length>1_000_000)
            throw FeatureScope.invalid("编辑草稿必须是对象且不能超过1MB");
        ObjectNode root=json.createObjectNode();root.put("schemaVersion",1);
        var p=db.queryForMap("SELECT name,description FROM narrative_project WHERE id=?",project);
        ObjectNode info=root.putObject("project");info.put("name",Objects.toString(p.get("name"),""));
        if(p.get("description")==null)info.putNull("description");else info.put("description",p.get("description").toString());
        ObjectNode tables=root.putObject("tables");
        for(var table:TABLES) {
            var rows=db.queryForList("SELECT "+table.columns()+" FROM "+table.name()+" WHERE "+table.scope()+" ORDER BY "+(table.name().equals("node_character")?"node_id,character_id":"id"),project);
            if(rows.size()>10000) throw FeatureScope.invalid("项目单类创作记录超过10000条，暂不能保存版本");
            ArrayNode array=tables.putArray(table.name());
            for(var row:rows) {
                ObjectNode item=array.addObject();
                for(String key:table.columns().split(",")) {
                    Object value=row.get(key);
                    if(value==null)item.putNull(key);
                    else if(key.equals("unlock_rule"))item.set(key,decode(value instanceof byte[] b?new String(b,StandardCharsets.UTF_8):value.toString()));
                    else item.set(key,json.valueToTree(value));
                }
            }
        }
        root.set("drafts",drafts.deepCopy());
        if(encode(root).getBytes(StandardCharsets.UTF_8).length>8_000_000)throw FeatureScope.invalid("项目快照超过8MB，请先精简内容");
        return root;
    }
    public String encode(JsonNode node) {try{return json.writeValueAsString(canonical(node));}catch(Exception ex){throw FeatureScope.invalid("快照编码失败");}}
    private JsonNode canonical(JsonNode value) {
        if(value.isObject()) {ObjectNode result=json.createObjectNode();var names=new ArrayList<String>();value.fieldNames().forEachRemaining(names::add);Collections.sort(names);for(String k:names)result.set(k,canonical(value.get(k)));return result;}
        if(value.isArray()) {ArrayNode result=json.createArrayNode();value.forEach(v->result.add(canonical(v)));return result;}
        return value;
    }
    public JsonNode decode(String text) {
        try {var node=json.readTree(text);return node.isTextual()?json.readTree(node.asText()):node;}
        catch(Exception ex){throw BusinessException.conflict("保存快照损坏，无法读取");}
    }
    public String hash(JsonNode snapshot) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(snapshot).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw BusinessException.conflict("无法计算保存摘要");}
    }
    public record Copy(ProjectDtos.Summary project,JsonNode drafts) {}
    public Copy copy(Long user,String name,JsonNode snapshot) {
        if(snapshot.path("schemaVersion").asInt()!=1) throw BusinessException.conflict("不支持的保存快照版本");
        var project=projects.create(user,new ProjectDtos.CreateRequest(name,snapshot.path("project").path("description").isNull()?null:snapshot.path("project").path("description").asText()));
        Map<String,Map<Long,Long>> ids=new HashMap<>();
        for(var table:TABLES) {
            var rows=snapshot.path("tables").path(table.name());
            if(!rows.isArray())throw BusinessException.conflict("保存快照缺少创作数据");
            Map<Long,Long> tableIds=new HashMap<>();ids.put(table.name(),tableIds);
            for(var row:rows) {
                LinkedHashMap<String,Object> values=new LinkedHashMap<>();
                if(table.scope().equals("project_id=?"))values.put("project_id",project.id());
                for(String key:table.columns().split(",")) {
                    if(key.equals("id"))continue;
                    var value=row.get(key);if(value==null)throw BusinessException.conflict("保存快照字段不完整");
                    Object scalar=value.isNull()?null:value.isNumber()?value.numberValue():value.isBoolean()?value.booleanValue():value.asText();
                    if(!value.isNull() && REFS.containsKey(key)) scalar=reference(ids,REFS.get(key),value.asLong());
                    if(key.equals("unlock_rule")&&!value.isNull())scalar=encode(value);
                    values.put(key,scalar);
                }
                if(table.name().equals("story_choice_draft"))values.put("created_by",user);
                var keys=new GeneratedKeyHolder();
                String sql="INSERT INTO "+table.name()+" ("+String.join(",",values.keySet())+") VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+")";
                if(table.name().equals("node_character"))db.update(sql,values.values().toArray());
                else {
                    db.update(connection->{var ps=connection.prepareStatement(sql,new String[]{"id"});int i=1;for(Object value:values.values())ps.setObject(i++,value);return ps;},keys);
                    tableIds.put(row.path("id").asLong(),Objects.requireNonNull(keys.getKey()).longValue());
                }
            }
        }
        JsonNode drafts=snapshot.path("drafts").deepCopy();
        if(drafts.isObject()) drafts.forEach(entry->{
            remapUi(entry,ids);
            if(entry.isObject()&&entry.hasNonNull("entityId")) {
                var table=ids.get(entry.path("entityTable").asText());
                Long mapped=table==null?null:table.get(entry.path("entityId").asLong());
                if(mapped==null)((ObjectNode)entry).putNull("entityId");else ((ObjectNode)entry).put("entityId",mapped);
            }
        });
        return new Copy(project,drafts);
    }
    private void remapUi(JsonNode node,Map<String,Map<Long,Long>> ids) {
        if(node.isArray())node.forEach(n->remapUi(n,ids));
        if(node.isObject()) {
            var names=new ArrayList<String>();node.fieldNames().forEachRemaining(names::add);
            for(String key:names) {
                var value=node.get(key);
                if(UI_REFS.containsKey(key)&&value.isNumber()) {
                    Long mapped=ids.getOrDefault(UI_REFS.get(key),Map.of()).get(value.asLong());
                    if(mapped==null)((ObjectNode)node).putNull(key);else ((ObjectNode)node).put(key,mapped);
                } else remapUi(value,ids);
            }
        }
    }
    private Long reference(Map<String,Map<Long,Long>> ids,String table,long id) {
        Long result=ids.getOrDefault(table,Map.of()).get(id);
        if(result==null)throw BusinessException.conflict("保存快照引用不完整，恢复已取消");
        return result;
    }
}
