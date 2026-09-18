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
        var preview=confirmed(file,request);
        return transfer.importStory(user,preview.document());
    }
    @Transactional
    public ProjectDtos.Summary intoEmpty(Long user,Long project,MultipartFile file,Request request) {
        guard.editor(user,project);
        if(!graph.getGraph(user,project).nodes().isEmpty())
            throw BusinessException.conflict("当前剧情图已有节点，不会覆盖；请从项目列表导入为新项目");
        var doc=confirmed(file,request).document();
        Map<String,Long> ids=new HashMap<>();
        for(var node:doc.nodes()) ids.put(node.nodeKey(),graph.createNode(user,project,node).id());
        for(var choice:doc.choices()) graph.createChoice(user,project,ids.get(choice.sourceNodeKey()),
                new StoryGraphDtos.ChoiceRequest(ids.get(choice.targetNodeKey()),choice.choiceText(),choice.sortOrder(),true));
        return projects.getAccessible(user,project);
    }
    private Preview confirmed(MultipartFile file,Request request) {
        var preview=parser.preview(file,request);
        if(!Boolean.TRUE.equals(request.confirm()) || request.expectedDigest()==null || !request.expectedDigest().equals(preview.digest()))
            throw BusinessException.conflict("请先预览并确认；文件或映射变化后需要重新预览");
        return preview;
    }
}
