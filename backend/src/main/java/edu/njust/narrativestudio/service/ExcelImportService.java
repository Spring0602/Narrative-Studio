package edu.njust.narrativestudio.service;

import edu.njust.narrativestudio.dto.*;
import edu.njust.narrativestudio.dto.ExcelImportDtos.*;
import edu.njust.narrativestudio.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.Map;

@Service
public class ExcelImportService {
    // 【解释】负责写入流程；解析交给 parser，节点与连线创建复用已有业务服务。
    // 两个写方法都保留事务，避免节点已插入而连线失败后留下半成品。
    private final ExcelStoryParser parser;
    private final StoryTransferService transfer;
    private final ProjectMutationGuard guard;
    private final StoryGraphService graph;
    private final ProjectService projects;
    public ExcelImportService(ExcelStoryParser parser,StoryTransferService transfer,ProjectMutationGuard guard,
                              StoryGraphService graph,ProjectService projects) {
        this.parser=parser;this.transfer=transfer;this.guard=guard;this.graph=graph;this.projects=projects;
    }
    @Transactional
    public ProjectDtos.Summary create(Long user,MultipartFile file,Request request) {
        // 重新解析并确认摘要，再交给通用导入服务创建新项目，不直接信任前端预览数据。
        var preview=confirmed(file,request);
        return transfer.importStory(user,preview.document());
    }
    @Transactional
    public ProjectDtos.Summary intoEmpty(Long user,Long project,MultipartFile file,Request request) {
        // 检查编辑权限并取得项目锁，然后判断图是否为空，防止检查与写入之间被并发修改。
        // 【常见修改：提示文字】可改下方 conflict 的字符串，不能通过删除判断来支持覆盖或合并。
        // 合并需要另行处理重复节点编号、起点和已有规则，不属于改一行就完成的功能。
        guard.editor(user,project);
        if(!graph.getGraph(user,project).nodes().isEmpty())
            throw BusinessException.conflict("当前剧情图已有节点，不会覆盖；请从项目列表导入为新项目");
        var doc=confirmed(file,request).document();
        // 先插入全部节点，建立表格编号到数据库 ID 的对照，例如 N001 -> 101。
        // 再创建连线，把 N001/N002 换成 101/102；不能把表格编号当成数据库主键使用。
        Map<String,Long> ids=new HashMap<>();
        for(var node:doc.nodes()) ids.put(node.nodeKey(),graph.createNode(user,project,node).id());
        for(var choice:doc.choices()) graph.createChoice(user,project,ids.get(choice.sourceNodeKey()),
                new StoryGraphDtos.ChoiceRequest(ids.get(choice.targetNodeKey()),choice.choiceText(),choice.sortOrder(),true));
        return projects.getAccessible(user,project);
    }
    private Preview confirmed(MultipartFile file,Request request) {
        // preview 会再次校验文件与配置，并对生成后的文档计算摘要。
        // confirm 必须为 true，且新摘要与用户确认的 expectedDigest 一致，才允许写入。
        // 【验证重点】正常确认成功；换成导致预览内容改变的文件/映射后仍带旧摘要，应拒绝且不落库。
        // 错误提示可改，但不能删除摘要检查；摘要不是密码或权限凭证，也不能代替权限校验。
        var preview=parser.preview(file,request);
        if(!Boolean.TRUE.equals(request.confirm()) || request.expectedDigest()==null || !request.expectedDigest().equals(preview.digest()))
            throw BusinessException.conflict("请先预览并确认；文件或映射变化后需要重新预览");
        return preview;
    }
}
