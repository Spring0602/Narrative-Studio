package edu.njust.narrativestudio.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipInputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.njust.narrativestudio.dto.ExcelImportDtos.*;
import edu.njust.narrativestudio.dto.ExcelImportDtos.Inspection;
import edu.njust.narrativestudio.dto.ExcelImportDtos.Preview;
import edu.njust.narrativestudio.dto.ExcelImportDtos.Request;
import edu.njust.narrativestudio.dto.ExcelImportDtos.SheetInfo;
import edu.njust.narrativestudio.dto.StoryGraphDtos.NodeRequest;
import edu.njust.narrativestudio.dto.StoryTransferDtos.*;
import edu.njust.narrativestudio.dto.StoryTransferDtos.Choice;
import edu.njust.narrativestudio.dto.StoryTransferDtos.Document;
import edu.njust.narrativestudio.exception.BusinessException;
import jakarta.validation.Validator;

/** Stateless parsing: preview and commit independently validate the same bounded upload. No formulas evaluated. */
@Service
public class ExcelStoryParser {
    // 【链路导航】inspect 看结构 -> preview/parse 生成预览 -> 导入服务确认摘要后落库。
    // 本类不写数据库；提供节点、连线和坐标，不直接生成图片，也不调用 AI。
    // 【常见修改 1：上传上限改为 10 MiB】MAX_FILE 改成 10*1024*1024，并同步 open 的两处提示。
    // 同时调整 application.yml 的 multipart.max-file-size 与 max-request-size（请求还包括 options）。
    // MAX_EXPANDED 是解压后限制，不是上传限制；扩大容量还要评估内存、前端限制和相关测试。
    private static final int MAX_FILE=5*1024*1024, MAX_EXPANDED=20*1024*1024;
    private final ObjectMapper json;
    private final Validator validator;
    public ExcelStoryParser(ObjectMapper json,Validator validator) {this.json=json;this.validator=validator;}

    // 【解释】只返回各工作表的名称、样例、行数和隐藏状态，帮助前端展示并配置映射。
    // try(...) 会自动关闭工作簿；样例中的公式只提示，不执行。
    // 【常见修改 2：样例最多 10 行】下方 Math.min(30,...) 改成 Math.min(10,...)。
    // 这只减少样例，不减少实际导入行数；表头仍可配置到第 30 行，要考虑是否影响用户选择。
    // 样例截断若改为 200 字符，value.length()>120 和 substring(0,120) 两处一起改为 200。
    public Inspection inspect(MultipartFile file) {
        try (Workbook book=open(file))   {
            List<SheetInfo> sheets=new ArrayList<>();
            for(int i=0;i<book.getNumberOfSheets();i++) {
                var sheet=book.getSheetAt(i);dimensions(sheet);
                List<List<String>> sample=new ArrayList<>();
                for(int r=0;r<Math.min(30,sheet.getLastRowNum()+1);r++) {
                    List<String> cells=new ArrayList<>();
                    var row=sheet.getRow(r);
                    for(int c=0;c<(row==null?0:row.getLastCellNum());c++) {
                        var cell=row.getCell(c);
                        String value=cell!=null && cell.getCellType()==CellType.FORMULA?"[公式，请粘贴为值]":value(cell);
                        cells.add(value.length()>120?value.substring(0,120):value);
                    }
                    sample.add(cells);
                }
                sheets.add(new SheetInfo(sheet.getSheetName(),sheet.getLastRowNum()+1,sample,book.isSheetHidden(i)||book.isSheetVeryHidden(i)));
            }
            return new Inspection(sheets);
        } catch(BusinessException ex) {throw ex;}
        catch(Exception ex) {throw FeatureScope.invalid("无法读取Excel，请使用未加密的有效.xlsx或.xls文件");}
    }

    // 【解释】先校验配置对象，再打开文件并调用 parse；保留明确的业务错误，包装其他解析异常。
    // 显式 validator.validate 使内部调用也会检查配置，不只依赖 Controller 的 @Valid。
    public Preview preview(MultipartFile file,Request request) {
        if(request==null || !validator.validate(request).isEmpty()) throw FeatureScope.invalid("Excel映射或项目名称格式不正确");
        try (Workbook book=open(file)) {
            return parse(book,request);
        } catch(BusinessException ex) {throw ex;}
        catch(Exception ex) {throw FeatureScope.invalid("Excel解析失败，请检查文件格式和表头映射");}
    }

    // 【解释】核心步骤：映射检查 -> 逐行生成节点/连线 -> 引用检查 -> 唯一起点 -> 布局 -> 摘要。
    // 映射的是固定业务字段到 Excel 列下标，不要求 Excel 表头名称固定，也不是自动理解自然语言。
    private Preview parse(Workbook book,Request request) throws Exception {
        var m=request.mapping();
        var sheet=table(book,m.nodeSheet(),m.headerRow());
        checkColumns(sheet,m.headerRow(),m.columns(),Set.of("nodeKey","title","content","nodeType","scene","isStart"),Set.of("nodeKey","title"));
        Set<Integer> consumed=new HashSet<>(m.columns().values());
        for(var b:m.branches()) {
            column(sheet,m.headerRow(),b.targetColumn());
            if(!consumed.add(b.targetColumn())) throw FeatureScope.invalid("同一列不能重复映射到节点字段或多个分支");
            if(b.textColumn()!=null) {
                column(sheet,m.headerRow(),b.textColumn());
                if(!consumed.add(b.textColumn())) throw FeatureScope.invalid("分支文本列不能重复映射");
            }
        }
        if(m.choiceSheet()!=null && !m.choiceSheet().isBlank() && !m.branches().isEmpty())
            throw FeatureScope.invalid("请选择单表分支或独立连线表，不能同时导入两套关系");
        // warning 是允许继续但需要用户确认的提示；fail/throw 才会中止导入。
        // 可改提示用词，但不要把“推荐 AI 整理”写成“系统已接入 AI”，也不要把自然语言当执行规则。
        List<String> warnings=new ArrayList<>();
        warnings.add("Excel用于整理全部剧情节点；推荐先用AI整理，人工核对节点编号和跳转。当前未接入AI生成Excel。");
        warnings.add("本次只生成节点与连线，不把自然语言条件、效果或补充字段自动转换为可执行规则；请在规则编辑器中补充。");
        if(book.isSheetHidden(book.getSheetIndex(sheet)) || book.isSheetVeryHidden(book.getSheetIndex(sheet)))
            warnings.add("所选节点表是隐藏工作表，仍按完整数据导入。");
        if(!m.preserveExtra()) warnings.add("已关闭补充字段保留：未映射的列不会导入。");
        if(m.preserveExtra()) {
            var header=sheet.getRow(m.headerRow()-1);
            List<String> extra=new ArrayList<>();
            for(int c=0;c<header.getLastCellNum();c++) if(!consumed.contains(c) && !value(header.getCell(c)).isBlank()) extra.add(value(header.getCell(c)));
            if(!extra.isEmpty()) warnings.add("未映射节点列将作为正文补充说明保留："+String.join("、",extra));
        }
        // 保持表格节点顺序；caseKeys 用于不区分大小写的重复检测，实际连线引用仍要求编号匹配。
        LinkedHashMap<String,NodeRequest> nodes=new LinkedHashMap<>();
        List<Choice> choices=new ArrayList<>();
        Map<String,String> caseKeys=new HashMap<>();
        boolean hiddenRows=false;
        for(int r=m.headerRow();r<=sheet.getLastRowNum();r++) {
            var row=sheet.getRow(r);
            if(blank(row)) continue;
            hiddenRows|=row.getZeroHeight();
            String key=read(row,m.columns(),"nodeKey"),title=read(row,m.columns(),"title");
            if(key.isBlank() || title.isBlank()) fail(sheet,r,"节点编号和标题不能为空（说明行请放在表头之前）");
            if(!key.matches("[A-Za-z0-9_-]{1,64}")) fail(sheet,r,"节点编号仅支持1–64位字母、数字、下划线或短横线；请保留稳定编号");
            String old=caseKeys.putIfAbsent(key.toLowerCase(Locale.ROOT),key);
            if(old!=null) fail(sheet,r,"重复节点编号："+key+"（与"+old+"冲突，不区分大小写）");
            String rawType=read(row,m.columns(),"nodeType"),type=nodeType(rawType,m.typeMappings());
            if(type==null) fail(sheet,r,"无法识别节点类型“"+rawType+"”，请在类型映射中指定普通节点或结局");
            boolean start=startFlag(read(row,m.columns(),"isStart"),sheet,r);
            if(Set.of("START","起点","开始").contains(rawType.toUpperCase(Locale.ROOT))) start=true;
            String content=read(row,m.columns(),"content");
            if(m.preserveExtra()) {
                var header=sheet.getRow(m.headerRow()-1);
                var extra=new StringBuilder();
                for(int c=0;c<row.getLastCellNum();c++) if(!consumed.contains(c) && !value(row.getCell(c)).isBlank()) {
                    String label=value(header.getCell(c));
                    if(label.isBlank()) fail(sheet,r,"有数据的第"+(c+1)+"列缺少表头，请补充或删除该列");
                    extra.append("\n").append(label).append("：").append(value(row.getCell(c)));
                }
                if(!extra.isEmpty()) content+=(content.isBlank()?"":"\n\n")+"【表格补充字段】"+extra;
            }
            var n=new NodeRequest(key,title,content,type,read(row,m.columns(),"scene"),start,BigDecimal.ZERO,BigDecimal.ZERO);
            if(!validator.validate(n).isEmpty()) fail(sheet,r,"标题最多120字、正文（含补充字段）最多10000字、场景最多100字，请检查长度");
            nodes.put(key,n);
            // 【常见修改 3：节点上限】例如仅缩小 Excel 导入上限为 300：判断 >300，提示改为 300。
            // 若扩大超过 500，必须同步 StoryTransferDtos.Document.nodes 的 @Size(max=500)，
            // 并核对图服务容量、前端提示和测试；只改此处仍可能在文档校验时被拒绝。
            // 验证恰好达到上限允许、多一条拒绝；失败不能留下半个项目。
            if(nodes.size()>500) fail(sheet,r,"每次最多500个节点");
            for(var b:m.branches()) {
                String target=value(row.getCell(b.targetColumn()));
                String text=b.textColumn()==null?"":value(row.getCell(b.textColumn()));
                if(target.isBlank()) {if(!text.isBlank()) fail(sheet,r,"分支有文本但缺少目标节点");continue;}
                addChoice(choices,key,target,text,sheet,r);
            }
        }
        if(hiddenRows) warnings.add("节点表包含隐藏行，已按原始内容导入，未按Excel筛选结果丢弃行。");
        if(nodes.isEmpty()) throw FeatureScope.invalid("节点表没有可导入的数据");
        if(m.choiceSheet()!=null && !m.choiceSheet().isBlank()) {
            if(m.choiceSheet().equals(m.nodeSheet())) throw FeatureScope.invalid("独立连线表不能与节点表相同");
            var edgeSheet=table(book,m.choiceSheet(),m.choiceHeaderRow());
            checkColumns(edgeSheet,m.choiceHeaderRow(),m.choiceColumns(),Set.of("sourceNodeKey","targetNodeKey","choiceText"),Set.of("sourceNodeKey","targetNodeKey"));
            warnings.add("连线表只导入映射的源节点、目标节点和选项文本；其他列不作为规则执行，也不保留。");
            for(int r=m.choiceHeaderRow();r<=edgeSheet.getLastRowNum();r++) {
                var row=edgeSheet.getRow(r);if(blank(row))continue;
                addChoice(choices,read(row,m.choiceColumns(),"sourceNodeKey"),read(row,m.choiceColumns(),"targetNodeKey"),
                        read(row,m.choiceColumns(),"choiceText"),edgeSheet,r);
            }
        }
        // 全部节点读取完后再校验连线，允许前面的行指向后面才定义的节点。
        // 不存在的引用、结局出边、完全重复的连线属于错误，不能只删检查来“容错”。
        Set<List<String>> seenChoices=new HashSet<>();
        for(var c:choices) {
            if(!nodes.containsKey(c.sourceNodeKey()) || !nodes.containsKey(c.targetNodeKey()))
                throw FeatureScope.invalid("连线引用不存在的节点："+c.sourceNodeKey()+" → "+c.targetNodeKey()+"；请检查编号与大小写");
            if("ENDING".equals(nodes.get(c.sourceNodeKey()).nodeType())) throw FeatureScope.invalid("结局节点不能有出边："+c.sourceNodeKey());
            if(!seenChoices.add(List.of(c.sourceNodeKey(),c.targetNodeKey(),c.choiceText()))) throw FeatureScope.invalid("重复连线："+c.sourceNodeKey()+" → "+c.targetNodeKey()+"（"+c.choiceText()+"）");
        }
        // 明确指定起点优先；否则检查表格标记；无标记时用第一个节点并警告，多个起点则拒绝。
        // 【修改策略】若要求无起点必须报错，只在 starts.isEmpty() 分支替换为
        // throw FeatureScope.invalid("请在导入设置中指定起点"); 不要删掉多起点/结局起点检查。
        String startKey=m.startNodeKey()==null?"":m.startNodeKey().trim();
        if(!startKey.isBlank()) {
            if(!nodes.containsKey(startKey)) throw FeatureScope.invalid("指定的起点编号不存在："+startKey);
            for(var n:new ArrayList<>(nodes.values())) nodes.put(n.nodeKey(),withStart(n,n.nodeKey().equals(startKey)));
        } else {
            var starts=nodes.values().stream().filter(n->Boolean.TRUE.equals(n.isStart())).toList();
            if(starts.size()>1) throw FeatureScope.invalid("存在多个起点，请修改表格或在导入设置中指定唯一的起点编号");
            if(starts.isEmpty()) {
                startKey=nodes.keySet().iterator().next();
                nodes.put(startKey,withStart(nodes.get(startKey),true));
                warnings.add("未提供起点，使用表格第一个节点 "+startKey+"；如不符合剧情，请在设置中指定起点后重新预览。");
            } else startKey=starts.getFirst().nodeKey();
        }
        if("ENDING".equals(nodes.get(startKey).nodeType())) throw FeatureScope.invalid("结局不能作为起点，请指定普通节点");
        List<NodeRequest> positioned=layout(nodes,choices,startKey,warnings);
        var doc=new Document(2,request.name().trim(),request.description(),positioned,List.of(),choices);
        if(!validator.validate(doc).isEmpty()) throw FeatureScope.invalid("生成的剧情超过项目字段或容量限制");
        // 摘要针对生成后的 Document，不是文件名；导入时重新解析并比较，确保落库内容与确认结果一致。
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(doc)));
        return new Preview(doc,digest,warnings);
    }

    // 【解释】构建邻接表，用队列从起点广度优先遍历；depth 同时记录层数和是否已访问，防止循环死转。
    // 同层节点纵向排列，不可达节点放独立列并警告；这是基础布局，不保证复杂图完全无交叉。
    // 【常见修改 4：拉大间距】下方 60L+360L*d 改成 60L+450L*d，横向间隔变为 450；
    // 60L+240L*lane 改成 60L+300L*lane，纵向间隔变为 300。60 是初始偏移，不是间距。
    // 只影响重新预览/新导入坐标，不重排已有项目；验证分支、循环、不可达节点和画布效果。
    private List<NodeRequest> layout(LinkedHashMap<String,NodeRequest> nodes,List<Choice> choices,String start,List<String> warnings) {
        Map<String,List<String>> edges=new HashMap<>();
        for(var c:choices) edges.computeIfAbsent(c.sourceNodeKey(),k->new ArrayList<>()).add(c.targetNodeKey());
        Map<String,Integer> depth=new LinkedHashMap<>();
        ArrayDeque<String> queue=new ArrayDeque<>();depth.put(start,0);queue.add(start);
        while(!queue.isEmpty()) {var key=queue.remove();for(var target:edges.getOrDefault(key,List.of())) if(!depth.containsKey(target)) {
            depth.put(target,depth.get(key)+1);queue.add(target);
        }}
        int unreachable=nodes.size()-depth.size();
        if(unreachable>0) warnings.add("有"+unreachable+"个节点从起点不可达，已放在独立列；请检查缺失连线或保留为待补剧情。");
        if(choices.isEmpty()) warnings.add("未生成连线：请映射目标列或选择连线表，否则仅生成独立节点。");
        long dead=nodes.values().stream().filter(n->"NORMAL".equals(n.nodeType())&&!edges.containsKey(n.nodeKey())).count();
        if(dead>0) warnings.add("有"+dead+"个普通节点没有出边，请确认是否应设为结局或补充分支。");
        int isolated=depth.values().stream().mapToInt(Integer::intValue).max().orElse(0)+1;
        Map<Integer,Integer> counts=new HashMap<>();
        List<NodeRequest> result=new ArrayList<>();
        for(var n:nodes.values()) {
            int d=depth.getOrDefault(n.nodeKey(),isolated),lane=counts.merge(d,1,Integer::sum)-1;
            result.add(new NodeRequest(n.nodeKey(),n.title(),n.content(),n.nodeType(),n.scene(),n.isStart(),
                    BigDecimal.valueOf(60L+360L*d),BigDecimal.valueOf(60L+240L*lane)));
        }
        return result;
    }

    // record 没有 setter，所以重新构造节点，只替换起点标记，其他字段照旧。
    private NodeRequest withStart(NodeRequest n,boolean start) {
        return new NodeRequest(n.nodeKey(),n.title(),n.content(),n.nodeType(),n.scene(),start,n.positionX(),n.positionY());
    }
    // 【解释】用户自定义类型映射优先，其次使用下面的内置别名；无法识别返回 null，由 parse 报错。
    // 【常见修改 5：增加类型别名】若“终章”应表示结局，在 ENDING 那一组 case 中追加 "终章"。
    // 它仍转换为 ENDING，不是新增一种数据库节点类型；不要直接返回未被全链路支持的 "TRUE_END"。
    private String nodeType(String input,Map<String,String> custom) {
        if(custom.containsKey(input)) return custom.get(input);
        return switch(input.toUpperCase(Locale.ROOT)) {
            case "", "NORMAL","普通","普通节点","剧情","剧情节点","对话","分支","START","起点","开始" -> "NORMAL";
            case "ENDING","END","结局","结束","终点","结局节点" -> "ENDING";
            default -> null;
        };
    }
    // 将“是/否、true/false、1/0”等转换为布尔值；未知文字拒绝，避免默默选错起点。
    // 若要支持“开始”，在返回 true 的 case 中添加 "开始"，并同步错误提示/测试即可。
    private boolean startFlag(String value,Sheet sheet,int row) {
        return switch(value.toLowerCase(Locale.ROOT)) {
            case "", "false","0","否","不是","no","n" -> false;
            case "true","1","是","起点","yes","y" -> true;
            default -> {fail(sheet,row,"起点标记无法识别："+value+"（支持是/否、true/false、1/0）");yield false;}
        };
    }
    // 【解释】统一生成连线，默认启用、按加入顺序排序；条件和效果为空，不自动生成可执行规则。
    // 【常见修改 6：默认选项文案】下方 text="继续" 改为 text="下一段"，只影响没有填写文本的选项。
    // 【常见修改 7：连线上限】改 choices.size()>1000 及提示；扩大上限需同步
    // StoryTransferDtos.Document.choices 的 @Size(max=1000)，并核对图服务和前端容量。
    // 文本长度另受通用 Choice/ChoiceRequest 等校验约束，不要只扩大这里的 500。
    private void addChoice(List<Choice> choices,String source,String target,String text,Sheet sheet,int row) {
        if(source.isBlank() || target.isBlank()) fail(sheet,row,"连线的源节点和目标节点不能为空");
        if(text.isBlank()) text="继续";
        if(text.length()>500) fail(sheet,row,"选项文本不能超过500字");
        choices.add(new Choice(source,target,text,choices.size(),true,List.of(),List.of(),null));
        if(choices.size()>1000) fail(sheet,row,"每次最多1000条连线");
    }
    // 检查映射只含允许的业务字段、覆盖必填字段，而且不能把一列重复映射给多个字段。
    private void checkColumns(Sheet sheet,int header,Map<String,Integer> columns,Set<String> allowed,Set<String> required) {
        if(!allowed.containsAll(columns.keySet()) || !columns.keySet().containsAll(required) || columns.values().stream().anyMatch(Objects::isNull))
            throw FeatureScope.invalid("字段映射缺少必填字段或包含未知字段");
        if(new HashSet<>(columns.values()).size()!=columns.size()) throw FeatureScope.invalid("同一列不能映射到多个字段");
        for(int c:columns.values()) column(sheet,header,c);
    }
    // header 是用户看到的行号，所以减 1；col 已是从 0 开始的下标，不再减 1。
    private void column(Sheet sheet,int header,int col) {
        var row=sheet.getRow(header-1);
        if(col<0 || col>=64 || row==null || value(row.getCell(col)).isBlank()) throw FeatureScope.invalid("映射列缺少表头："+sheet.getSheetName()+" 第"+(col+1)+"列");
    }
    // 定位工作表，检查表头/数据区无合并、公式和错误值。说明区在表头之前，不按数据区处理。
    // 不要为了支持公式就删检查：当前方案要求先在 Excel 粘贴为值，没有启用公式求值。
    private Sheet table(Workbook book,String name,int header) {
        var sheet=book.getSheet(name);
        if(sheet==null) throw FeatureScope.invalid("工作表不存在："+name);
        dimensions(sheet);
        if(sheet.getRow(header-1)==null) throw FeatureScope.invalid("所选表头行为空："+name);
        for(var merged:sheet.getMergedRegions()) if(merged.getLastRow()>=header-1)
            throw FeatureScope.invalid(name+" 的表头或数据区存在合并单元格，请取消合并并补全每行");
        for(Row row:sheet) if(row.getRowNum()>=header-1) for(Cell cell:row) {
            if(cell.getCellType()==CellType.FORMULA || cell.getCellType()==CellType.ERROR)
                fail(sheet,row.getRowNum(),"第"+(cell.getColumnIndex()+1)+"列含公式或错误值，请在Excel中复制并粘贴为值");
            if(value(cell).length()>10000) fail(sheet,row.getRowNum(),"单元格内容超过10000字");
        }
        return sheet;
    }
    // 【解释】工作表物理尺寸保护，与最终有效节点/连线数量是不同限制。
    // 【常见修改 8：行列上限】getLastRowNum 从 0 开始，因此 >=2030 拒绝的是第 2031 行及以后。
    // 若列上限改为 80，需同步这里 >64 为 >80、column 的 col>=64 为 >=80、
    // ExcelImportDtos 中列下标 @Max(63) 为 @Max(79)，以及前端列选择范围/提示/测试。
    private void dimensions(Sheet sheet) {
        if(sheet.getLastRowNum()>=2030) throw FeatureScope.invalid("每张工作表最多2030行（含表头和说明），请删除多余空白格式行");
        for(Row row:sheet) if(row.getLastCellNum()>64) throw FeatureScope.invalid("每张工作表最多64列，请移除多余格式列");
    }
    // 按业务字段找映射列再取值，未映射的可选字段返回空串。
    private String read(Row row,Map<String,Integer> columns,String key) {var col=columns.get(key);return col==null?"":value(row.getCell(col));}
    // 空行跳过，不生成节点；单元格格式读取统一交给 value。
    private boolean blank(Row row) {if(row==null)return true;for(Cell c:row) if(!value(c).isBlank())return false;return true;}
    private String value(Cell cell) {
        // 使用 Excel 显示格式取文本，再去掉首尾空白；不会在此执行公式。
        if(cell==null)return "";
        return new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
    }
    // 将内部从 0 开始的行下标 +1，返回用户在 Excel 中看到的行号，便于定位错误。
    private void fail(Sheet sheet,int row,String message) {throw FeatureScope.invalid(sheet.getSheetName()+" 第"+(row+1)+"行："+message);}

    // 【解释】限制上传大小和格式，检查 xlsx 压缩包后交给 Apache POI 解析。
    // 扩展名校验不能代替内容校验；压缩后体积小也可能解压很大，所以保留双重上限。
    // 【常见修改 9：工作表数量】下面 >10 改为 >5，可限制最多 5 张，同时同步“1至10张”提示。
    // 不要为兼容 .xlsm/CSV 只放开后缀：这些格式的安全检查和解析支持需要另行设计。
    private Workbook open(MultipartFile file) throws IOException {
        if(file==null || file.isEmpty() || file.getSize()>MAX_FILE) throw FeatureScope.invalid("请选择不超过5MB的Excel文件");
        String name=Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if(!name.endsWith(".xlsx") && !name.endsWith(".xls")) throw FeatureScope.invalid("仅支持.xlsx和.xls文件，不支持宏工作簿、CSV或改名文件");
        byte[] bytes;
        try(var in=file.getInputStream()) {bytes=in.readNBytes(MAX_FILE+1);}
        if(bytes.length>MAX_FILE) throw FeatureScope.invalid("Excel文件不能超过5MB");
        boolean zip=bytes.length>=4 && bytes[0]=='P' && bytes[1]=='K';
        if(name.endsWith(".xlsx")!=zip) throw FeatureScope.invalid("文件扩展名与实际Excel格式不一致");
        if(zip) {
            int count=0,total=0;Set<String> entries=new HashSet<>();
            try(var in=new ZipInputStream(new ByteArrayInputStream(bytes))) {
                java.util.zip.ZipEntry e;
                byte[] buffer=new byte[8192];
                while((e=in.getNextEntry())!=null) {
                    if(++count>1000 || !entries.add(e.getName())) throw FeatureScope.invalid("Excel压缩包结构异常或文件项过多");
                    String path=e.getName().toLowerCase(Locale.ROOT);
                    if(path.contains("vbaproject") || path.startsWith("xl/embeddings/")) throw FeatureScope.invalid("请移除宏和嵌入对象后另存为纯数据Excel");
                    int n;while((n=in.read(buffer))!=-1) {total+=n;if(total>MAX_EXPANDED) throw FeatureScope.invalid("Excel解压后超过20MB，请移除图片和无关工作表");}
                }
            }
        }
        Workbook book=WorkbookFactory.create(new ByteArrayInputStream(bytes));
        if(book.getNumberOfSheets()<1 || book.getNumberOfSheets()>10) {book.close();throw FeatureScope.invalid("Excel应包含1至10张工作表");}
        return book;
    }
}
