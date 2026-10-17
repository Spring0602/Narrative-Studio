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
        // 【常见修改 8：调整快照容量】当前按 UTF-8 字节数限制，不是字符串字符数。
        // 草稿若改成最多 2,000,000 字节：下方 1_000_000 改为 2_000_000，并同步“1MB”提示。
        // 整体仍受下方 8_000_000 字节限制；若也要调整总量，单独修改该数值和“8MB”提示。
        // 注意此处用十进制 MB；中文通常占多个 UTF-8 字节，边界测试要按字节构造。
        // 扩大容量还需核对 HTTP 请求限制、数据库容量和性能，不能认为改一个数字就保证支持。
        if(drafts==null || !drafts.isObject() || encode(drafts).getBytes(StandardCharsets.UTF_8).length>1_000_000)
            throw FeatureScope.invalid("编辑草稿必须是对象且不能超过1MB");
        ObjectNode root=json.createObjectNode();root.put("schemaVersion",1);
        var p=db.queryForMap("SELECT name,description FROM narrative_project WHERE id=?",project);
        ObjectNode info=root.putObject("project");info.put("name",Objects.toString(p.get("name"),""));
        if(p.get("description")==null)info.putNull("description");else info.put("description",p.get("description").toString());
        ObjectNode tables=root.putObject("tables");
        for(var table:TABLES) {
            var rows=db.queryForList("SELECT "+table.columns()+" FROM "+table.name()+" WHERE "+table.scope()+" ORDER BY "+(table.name().equals("node_character")?"node_id,character_id":"id"),project);
            // 【常见修改 9：单类记录上限】例如改为 5000：将判断改成 rows.size()>5000，提示也改为 5000。
            // 正好 5000 条允许、5001 条拒绝；这是每张快照表的上限，不是所有表合计，也不是模拟步数。
            // capture 被概览和保存共同使用，所以超限可能连保存面板概览也无法读取，需要说明影响。
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
    // 【恢复副本总览】不是把原项目改回旧版本，而是用保存快照创建一个新项目。
    // user：执行恢复的用户；name：新项目名称；snapshot：用户选中的历史快照。
    // 关键问题：新记录会获得新的数据库 ID，选项等关联字段也必须改用这些新 ID。
    // 例：旧节点 10、20 复制后变成 101、102，旧选项的起点 10/终点 20 就要换成 101/102。
    // 此方法由 ProjectSaveService.restore 的事务包围；中途抛出运行时异常时，
    // 这次新建项目和复制数据一起回滚。这里不删除或覆盖原项目及其历史记录。
    public Copy copy(Long user,String name,JsonNode snapshot) {
        // 【联动修改提醒】若给某类创作内容新增持久化字段，除了 Entity/DTO/SQL，还要检查上方 TABLES。
        // 例如 world_entry 新增 description 后，要把该表 columns 清单加上 description 才会捕获和恢复它。
        // 但旧快照没有新字段，本方法会报“字段不完整”；因此真实新增字段还需设计旧快照默认值或版本迁移。
        // 不要只改 schemaVersion 数字来绕过兼容问题，也不要删除 reference 的检查来强行恢复。
        // 第 1 步：确认能读懂快照格式。schemaVersion 是快照数据格式版本，不是项目保存次数。
        if(snapshot.path("schemaVersion").asInt()!=1) throw BusinessException.conflict("不支持的保存快照版本");
        // 第 2 步：用用户填写的新名称、快照里的项目描述，创建一个新项目。
        var project=projects.create(user,new ProjectDtos.CreateRequest(name,snapshot.path("project").path("description").isNull()?null:snapshot.path("project").path("description").asText()));
        // 第 3 步：准备“旧 ID -> 新 ID”对照表，按数据库表名分开存。
        // 外层 key 是表名，内层 key 是旧 ID、value 是新 ID。
        // 例如 ids 中的 story_node 对应 {10=101, 20=102}；角色表可以有自己独立的 ID 对照。
        Map<String,Map<Long,Long>> ids=new HashMap<>();
        // TABLES 的顺序有意义：先复制节点、变量等，再复制引用它们的选项、条件和效果。
        // 不能随便调整顺序，否则处理选项时，可能还没有它所引用节点的新 ID。
        for(var table:TABLES) {
            // 从快照的 tables 中拿到当前表对应的全部记录；每张表应当是一个 JSON 数组。
            var rows=snapshot.path("tables").path(table.name());
            if(!rows.isArray())throw BusinessException.conflict("保存快照缺少创作数据");
            // 当前表的对照表放入总表；后面每成功插入一条记录，就补充一个旧/新 ID 对应关系。
            Map<Long,Long> tableIds=new HashMap<>();ids.put(table.name(),tableIds);
            for(var row:rows) {
                // values 装的是“待插入的列名 -> 值”。LinkedHashMap 保留顺序，保证列和参数一一对应。
                LinkedHashMap<String,Object> values=new LinkedHashMap<>();
                // 直接属于项目的记录必须改挂到新项目，不能继续使用原项目 ID。
                if(table.scope().equals("project_id=?"))values.put("project_id",project.id());
                for(String key:table.columns().split(",")) {
                    // 不复制原来的主键 id，让数据库生成新 ID，避免主键冲突及新旧项目混用记录。
                    if(key.equals("id"))continue;
                    // 没有这个字段属于快照不完整；字段存在但值为 JSON null 则是另一回事。
                    var value=row.get(key);if(value==null)throw BusinessException.conflict("保存快照字段不完整");
                    // 将 JSON 值转换为 JDBC 能接收的 Java 值：null、数字、布尔值或字符串。
                    Object scalar=value.isNull()?null:value.isNumber()?value.numberValue():value.isBoolean()?value.booleanValue():value.asText();
                    // 第 4 步：遇到关联列就查对照表换 ID。REFS 规定该列引用哪张表。
                    // 例：source_node_id=10，通过 reference 查到新节点 101，实际插入 101。
                    if(!value.isNull() && REFS.containsKey(key)) scalar=reference(ids,REFS.get(key),value.asLong());
                    // 解锁规则是结构化 JSON，需要编码成 JSON 文本，不能直接当普通文本值提取。
                    if(key.equals("unlock_rule")&&!value.isNull())scalar=encode(value);
                    values.put(key,scalar);
                }
                // 新副本中的选项草稿，其创建人设置为这次执行恢复的用户。
                if(table.name().equals("story_choice_draft"))values.put("created_by",user);
                // 用来接收数据库自动生成的新主键。
                var keys=new GeneratedKeyHolder();
                // 拼出 INSERT 的列和 ? 占位符。表名/列名来自代码固定清单，不来自用户输入。
                // 真正的数据在下一步绑定到 ?，不是直接拼接进 SQL。
                String sql="INSERT INTO "+table.name()+" ("+String.join(",",values.keySet())+") VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+")";
                // 节点-角色关联表没有这里需要获取的独立自增 id，直接插入已转换的关联 ID 即可。
                if(table.name().equals("node_character"))db.update(sql,values.values().toArray());
                else {
                    // 其他表：执行插入，并要求 JDBC 返回新生成的 id。
                    db.update(connection->{var ps=connection.prepareStatement(sql,new String[]{"id"});int i=1;for(Object value:values.values())ps.setObject(i++,value);return ps;},keys);
                    // 记录映射，例如旧节点 id=10、新生成 id=101，就存入 10 -> 101。
                    // 后续复制选项时，才能找到它应该指向的新节点。
                    tableIds.put(row.path("id").asLong(),Objects.requireNonNull(keys.getKey()).longValue());
                }
            }
        }
        // 第 5 步：数据库内容复制完，再处理前端未提交的表单草稿。
        // deepCopy 产生独立副本，调整草稿 ID 时不修改传入的原始快照。
        JsonNode drafts=snapshot.path("drafts").deepCopy();
        if(drafts.isObject()) drafts.forEach(entry->{
            // 替换草稿内部已知的 nodeId、choiceId、characterId 等关联字段。
            remapUi(entry,ids);
            // entityId 表示“这个表单正在编辑哪条记录”，它对应的表由 entityTable 指定。
            if(entry.isObject()&&entry.hasNonNull("entityId")) {
                var table=ids.get(entry.path("entityTable").asText());
                Long mapped=table==null?null:table.get(entry.path("entityId").asLong());
                // 找不到对应记录就清空这个草稿 ID，不能让新项目的表单仍指向旧项目记录。
                if(mapped==null)((ObjectNode)entry).putNull("entityId");else ((ObjectNode)entry).put("entityId",mapped);
            }
        });
        // 第 6 步：新项目内容已写入数据库；返回新项目概要与转换后的草稿，供前端继续编辑。
        return new Copy(project,drafts);
    }
    private void remapUi(JsonNode node,Map<String,Map<Long,Long>> ids) {
        // 递归遍历：草稿可能包含数组和嵌套对象，所以不能只检查最外层。
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
        // 按“表名 + 旧 ID”找新 ID。数据库关联必须完整，找不到就拒绝恢复，交给外层事务回滚。
        // 与可清空的前端草稿引用不同，不能把缺失的数据库关联静默改成 null 后继续插入。
        Long result=ids.getOrDefault(table,Map.of()).get(id);
        if(result==null)throw BusinessException.conflict("保存快照引用不完整，恢复已取消");
        return result;
    }
}
