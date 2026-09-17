package edu.njust.narrativestudio.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.njust.narrativestudio.dto.ExcelImportDtos.*;
import edu.njust.narrativestudio.exception.BusinessException;
import jakarta.validation.Validation;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.util.*;

class ExcelStoryParserTest {
    static jakarta.validation.ValidatorFactory factory;
    ExcelStoryParser parser;
    @BeforeAll static void init() {factory=Validation.buildDefaultValidatorFactory();}
    @AfterAll static void close() {factory.close();}
    @BeforeEach void setup() {parser=new ExcelStoryParser(new ObjectMapper(),factory.getValidator());}
    static void row(Sheet s,int index,String... cells) {
        Row r=s.createRow(index);for(int c=0;c<cells.length;c++)r.createCell(c).setCellValue(cells[c]);
    }
    static MockMultipartFile file(Workbook book,String name) throws Exception {
        var out=new ByteArrayOutputStream();book.write(out);
        return new MockMultipartFile("file",name,"application/octet-stream",out.toByteArray());
    }
    static Workbook basic(boolean xlsx) {
        Workbook book=xlsx?new XSSFWorkbook():new HSSFWorkbook();
        Sheet s=book.createSheet("节点");
        row(s,0,"编号","标题","类型","目标","选项","路线");
        row(s,1,"start","出发","普通","end","前进","主线");
        row(s,2,"end","抵达","结局","","","主线");
        return book;
    }
    static Mapping mapping() {
        return new Mapping("节点",1,Map.of("nodeKey",0,"title",1,"nodeType",2),null,1,Map.of(),
                List.of(new Branch(3,4)),Map.of(),null,true);
    }
    static Request request(Mapping m) {return new Request("测试剧情","导入测试",m,null,null);}
    @Test void xlsxCustomHeadersPreserveExtraAndLayout() throws Exception {
        try(var book=basic(true)) {
            var p=parser.preview(file(book,"story.xlsx"),request(mapping()));
            assertEquals(2,p.document().nodes().size());assertEquals(1,p.document().choices().size());
            assertTrue(p.document().nodes().getFirst().isStart());
            assertTrue(p.document().nodes().getFirst().content().contains("路线：主线"));
            assertTrue(p.document().nodes().get(1).positionX().compareTo(p.document().nodes().getFirst().positionX())>0);
            assertEquals(64,p.digest().length());
            assertEquals(p.digest(),parser.preview(file(book,"story.xlsx"),request(mapping())).digest());
            assertTrue(p.warnings().stream().anyMatch(w->w.contains("首")||w.contains("第一个")));
        }
    }
    @Test void supportsLegacyXlsAndInspection() throws Exception {
        try(var book=basic(false)) {
            var f=file(book,"story.xls");
            assertEquals("节点",parser.inspect(f).sheets().getFirst().name());
            assertEquals(1,parser.preview(f,request(mapping())).document().choices().size());
        }
    }
    @Test void independentEdgeSheetNonFirstHeadersAndCustomTypes() throws Exception {
        try(var book=new XSSFWorkbook()) {
            var n=book.createSheet("场景");row(n,0,"使用说明");row(n,1,"代号","名字","类别");
            row(n,2,"a","审讯","推理");row(n,3,"z","真相","真结局");
            var e=book.createSheet("选择");row(e,0,"来自","去往","对白");row(e,1,"a","z","揭晓");
            var m=new Mapping("场景",2,Map.of("nodeKey",0,"title",1,"nodeType",2),"选择",1,
                    Map.of("sourceNodeKey",0,"targetNodeKey",1,"choiceText",2),List.of(),Map.of("推理","NORMAL","真结局","ENDING"),"a",true);
            var p=parser.preview(file(book,"story.xlsx"),request(m));
            assertEquals("ENDING",p.document().nodes().get(1).nodeType());assertEquals("揭晓",p.document().choices().getFirst().choiceText());
        }
    }
    @Test void rejectsDuplicateKeysCaseInsensitive() throws Exception {
        try(var b=basic(true)) {
            b.getSheetAt(0).getRow(2).getCell(0).setCellValue("START");
            var ex=assertThrows(BusinessException.class,()->parser.preview(file(b,"s.xlsx"),request(mapping())));
            assertTrue(ex.getMessage().contains("第3行"));assertTrue(ex.getMessage().contains("重复"));
        }
    }
    @Test void rejectsMissingTargetAndDoesNotInventNodes() throws Exception {
        try(var b=basic(true)) {
            b.getSheetAt(0).getRow(1).getCell(3).setCellValue("missing");
            assertTrue(assertThrows(BusinessException.class,()->parser.preview(file(b,"s.xlsx"),request(mapping()))).getMessage().contains("不存在"));
        }
    }
    @Test void rejectsEndingOutgoingAndStartEnding() throws Exception {
        try(var b=basic(true)) {
            b.getSheetAt(0).getRow(2).getCell(3).setCellValue("start");
            assertThrows(BusinessException.class,()->parser.preview(file(b,"s.xlsx"),request(mapping())));
            b.getSheetAt(0).getRow(2).getCell(3).setCellValue("");
            var m=mapping();
            var bad=new Mapping(m.nodeSheet(),1,m.columns(),null,1,Map.of(),m.branches(),Map.of(),"end",true);
            assertThrows(BusinessException.class,()->parser.preview(file(b,"s.xlsx"),request(bad)));
        }
    }
    @Test void rejectsFormulasEvenWithCachedValues() throws Exception {
        try(var b=basic(true)) {
            b.getSheetAt(0).getRow(1).getCell(1).setCellFormula("1+1");
            var f=file(b,"s.xlsx");
            assertTrue(parser.inspect(f).sheets().getFirst().sampleRows().get(1).get(1).contains("公式"));
            assertTrue(assertThrows(BusinessException.class,()->parser.preview(f,request(mapping()))).getMessage().contains("粘贴为值"));
        }
    }
    @Test void rejectsMergedDataButAllowsMergedTitleAboveHeader() throws Exception {
        try(var b=basic(true)) {
            b.getSheetAt(0).addMergedRegion(new CellRangeAddress(1,1,0,1));
            assertThrows(BusinessException.class,()->parser.preview(file(b,"s.xlsx"),request(mapping())));
        }
        try(var b=new XSSFWorkbook()) {
            var s=b.createSheet("节点");row(s,0,"说明");s.addMergedRegion(new CellRangeAddress(0,0,0,1));
            row(s,1,"id","title");row(s,2,"s","开头");
            var m=new Mapping("节点",2,Map.of("nodeKey",0,"title",1),null,1,Map.of(),List.of(),Map.of(),null,true);
            assertEquals(1,parser.preview(file(b,"s.xlsx"),request(m)).document().nodes().size());
        }
    }
    @Test void rejectsUnknownTypeUntilMapped() throws Exception {
        try(var b=basic(true)) {
            b.getSheetAt(0).getRow(1).getCell(2).setCellValue("战斗");
            assertTrue(assertThrows(BusinessException.class,()->parser.preview(file(b,"s.xlsx"),request(mapping()))).getMessage().contains("类型"));
        }
    }
    @Test void rejectsOversizeAndRenamedFiles() throws Exception {
        assertThrows(BusinessException.class,()->parser.inspect(new MockMultipartFile("file","s.xlsx","",new byte[5*1024*1024+1])));
        assertThrows(BusinessException.class,()->parser.inspect(new MockMultipartFile("file","s.xlsx","","not excel".getBytes())));
        try(var b=basic(true)) {assertThrows(BusinessException.class,()->parser.inspect(file(b,"s.xls")));}
    }
    @Test void rejectsDuplicateAndMissingMappings() throws Exception {
        try(var b=basic(true)) {
            var m=new Mapping("节点",1,Map.of("nodeKey",0,"title",0),null,1,Map.of(),List.of(),Map.of(),null,true);
            assertThrows(BusinessException.class,()->parser.preview(file(b,"s.xlsx"),request(m)));
            var missing=new Mapping("节点",1,Map.of("nodeKey",0),null,1,Map.of(),List.of(),Map.of(),null,true);
            assertThrows(BusinessException.class,()->parser.preview(file(b,"s.xlsx"),request(missing)));
        }
    }
    @Test void preservesLeadingZerosUsingDisplayedIdentifiersAndSkipsBlankRows() throws Exception {
        try(var b=basic(true)) {
            var s=b.getSheetAt(0);s.getRow(1).getCell(0).setCellValue(1);
            var style=b.createCellStyle();style.setDataFormat(b.createDataFormat().getFormat("000"));
            s.getRow(1).getCell(0).setCellStyle(style);
            row(s,3,"","","","","","");s.getRow(1).setZeroHeight(true);
            var p=parser.preview(file(b,"s.xlsx"),request(mapping()));
            assertEquals("001",p.document().nodes().getFirst().nodeKey());assertEquals(2,p.document().nodes().size());
            assertTrue(p.warnings().stream().anyMatch(w->w.contains("隐藏行")));
        }
    }
    @Test void multipleBranchesAndCyclesGetUniqueFinitePositions() throws Exception {
        try(var b=new XSSFWorkbook()) {
            var s=b.createSheet("节点");row(s,0,"id","title","target1","target2");
            row(s,1,"a","起","b","c");row(s,2,"b","循环","a","c");row(s,3,"c","合流","","");row(s,4,"d","独立","","");
            var m=new Mapping("节点",1,Map.of("nodeKey",0,"title",1),null,1,Map.of(),List.of(new Branch(2,null),new Branch(3,null)),Map.of(),null,true);
            var p=parser.preview(file(b,"s.xlsx"),request(m));assertEquals(4,p.document().choices().size());
            assertEquals(4,p.document().nodes().stream().map(n->List.of(n.positionX(),n.positionY())).distinct().count());
            assertTrue(p.warnings().stream().anyMatch(w->w.contains("不可达")));
        }
    }
    @Test void rejectsDanglingTextAndOverlongContent() throws Exception {
        try(var b=basic(true)) {
            b.getSheetAt(0).getRow(2).getCell(4).setCellValue("没有目标");
            assertThrows(BusinessException.class,()->parser.preview(file(b,"s.xlsx"),request(mapping())));
            b.getSheetAt(0).getRow(2).getCell(4).setCellValue("");
            b.getSheetAt(0).getRow(1).getCell(5).setCellValue("a".repeat(10000));
            assertThrows(BusinessException.class,()->parser.preview(file(b,"s.xlsx"),request(mapping())));
        }
    }
    @Test void capacityLimitAndUnknownHeaderDataAreNotSilentlyTruncated() throws Exception {
        try(var b=new XSSFWorkbook()) {
            var s=b.createSheet("节点");row(s,0,"id","title");
            for(int i=1;i<=501;i++) row(s,i,"n"+i,"节点");
            var m=new Mapping("节点",1,Map.of("nodeKey",0,"title",1),null,1,Map.of(),List.of(),Map.of(),null,true);
            assertThrows(BusinessException.class,()->parser.preview(file(b,"s.xlsx"),request(m)));
        }
        try(var b=basic(true)) {
            b.getSheetAt(0).getRow(1).createCell(6).setCellValue("无表头数据");
            assertThrows(BusinessException.class,()->parser.preview(file(b,"s.xlsx"),request(mapping())));
        }
    }
}
