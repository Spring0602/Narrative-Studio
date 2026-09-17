package edu.njust.narrativestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.njust.narrativestudio.dto.*;
import edu.njust.narrativestudio.dto.ExcelImportDtos.*;
import edu.njust.narrativestudio.dto.StoryGraphDtos.NodeRequest;
import edu.njust.narrativestudio.dto.StoryTransferDtos.*;
import edu.njust.narrativestudio.exception.BusinessException;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Stateless parsing: preview and commit independently validate the same bounded upload. No formulas evaluated. */
@Service
public class ExcelStoryParser {
    private static final int MAX_FILE=5*1024*1024, MAX_EXPANDED=20*1024*1024;
    private final ObjectMapper json;
    private final Validator validator;
    public ExcelStoryParser(ObjectMapper json,Validator validator) {this.json=json;this.validator=validator;}

    public Inspection inspect(MultipartFile file) {
        try (Workbook book=open(file)) {
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

    public Preview preview(MultipartFile file,Request request) {
        if(request==null || !validator.validate(request).isEmpty()) throw FeatureScope.invalid("Excel映射或项目名称格式不正确");
        try (Workbook book=open(file)) {
            return parse(book,request);
        } catch(BusinessException ex) {throw ex;}
        catch(Exception ex) {throw FeatureScope.invalid("Excel解析失败，请检查文件格式和表头映射");}
    }

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
        Set<List<String>> seenChoices=new HashSet<>();
        for(var c:choices) {
            if(!nodes.containsKey(c.sourceNodeKey()) || !nodes.containsKey(c.targetNodeKey()))
                throw FeatureScope.invalid("连线引用不存在的节点："+c.sourceNodeKey()+" → "+c.targetNodeKey()+"；请检查编号与大小写");
            if("ENDING".equals(nodes.get(c.sourceNodeKey()).nodeType())) throw FeatureScope.invalid("结局节点不能有出边："+c.sourceNodeKey());
            if(!seenChoices.add(List.of(c.sourceNodeKey(),c.targetNodeKey(),c.choiceText()))) throw FeatureScope.invalid("重复连线："+c.sourceNodeKey()+" → "+c.targetNodeKey()+"（"+c.choiceText()+"）");
        }
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
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(doc)));
        return new Preview(doc,digest,warnings);
    }

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

    private NodeRequest withStart(NodeRequest n,boolean start) {
        return new NodeRequest(n.nodeKey(),n.title(),n.content(),n.nodeType(),n.scene(),start,n.positionX(),n.positionY());
    }
    private String nodeType(String input,Map<String,String> custom) {
        if(custom.containsKey(input)) return custom.get(input);
        return switch(input.toUpperCase(Locale.ROOT)) {
            case "", "NORMAL","普通","普通节点","剧情","剧情节点","对话","分支","START","起点","开始" -> "NORMAL";
            case "ENDING","END","结局","结束","终点","结局节点" -> "ENDING";
            default -> null;
        };
    }
    private boolean startFlag(String value,Sheet sheet,int row) {
        return switch(value.toLowerCase(Locale.ROOT)) {
            case "", "false","0","否","不是","no","n" -> false;
            case "true","1","是","起点","yes","y" -> true;
            default -> {fail(sheet,row,"起点标记无法识别："+value+"（支持是/否、true/false、1/0）");yield false;}
        };
    }
    private void addChoice(List<Choice> choices,String source,String target,String text,Sheet sheet,int row) {
        if(source.isBlank() || target.isBlank()) fail(sheet,row,"连线的源节点和目标节点不能为空");
        if(text.isBlank()) text="继续";
        if(text.length()>500) fail(sheet,row,"选项文本不能超过500字");
        choices.add(new Choice(source,target,text,choices.size(),true,List.of(),List.of(),null));
        if(choices.size()>1000) fail(sheet,row,"每次最多1000条连线");
    }
    private void checkColumns(Sheet sheet,int header,Map<String,Integer> columns,Set<String> allowed,Set<String> required) {
        if(!allowed.containsAll(columns.keySet()) || !columns.keySet().containsAll(required) || columns.values().stream().anyMatch(Objects::isNull))
            throw FeatureScope.invalid("字段映射缺少必填字段或包含未知字段");
        if(new HashSet<>(columns.values()).size()!=columns.size()) throw FeatureScope.invalid("同一列不能映射到多个字段");
        for(int c:columns.values()) column(sheet,header,c);
    }
    private void column(Sheet sheet,int header,int col) {
        var row=sheet.getRow(header-1);
        if(col<0 || col>=64 || row==null || value(row.getCell(col)).isBlank()) throw FeatureScope.invalid("映射列缺少表头："+sheet.getSheetName()+" 第"+(col+1)+"列");
    }
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
    private void dimensions(Sheet sheet) {
        if(sheet.getLastRowNum()>=2030) throw FeatureScope.invalid("每张工作表最多2030行（含表头和说明），请删除多余空白格式行");
        for(Row row:sheet) if(row.getLastCellNum()>64) throw FeatureScope.invalid("每张工作表最多64列，请移除多余格式列");
    }
    private String read(Row row,Map<String,Integer> columns,String key) {var col=columns.get(key);return col==null?"":value(row.getCell(col));}
    private boolean blank(Row row) {if(row==null)return true;for(Cell c:row) if(!value(c).isBlank())return false;return true;}
    private String value(Cell cell) {
        if(cell==null)return "";
        return new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
    }
    private void fail(Sheet sheet,int row,String message) {throw FeatureScope.invalid(sheet.getSheetName()+" 第"+(row+1)+"行："+message);}

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
