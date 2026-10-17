package edu.njust.narrativestudio.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import edu.njust.narrativestudio.dto.WorldEntryDtos;
import edu.njust.narrativestudio.entity.WorldEntry;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.WorldEntryMapper;
import edu.njust.narrativestudio.service.ProjectAccessService;
import edu.njust.narrativestudio.service.WorldEntryService;

@Service
/*
 * 答辩修改导航（下面的示例都是注释，不会自动生效）：
 * 1. 排序、筛选、标题搜索：看 list。
 * 2. 创建时默认值：看 create；新增和修改共用的赋值：看 apply。
 * 3. 修改时保留原排序值：看 update。
 * 4. 仅创建者可删除：看 delete。
 * 5. 错误提示：看 requireEntry；返回字段：看 toSummary。
 * 修改前先确认需求作用于哪个操作。标记“替换”的代码不能直接追加执行。
 * 保留项目归属检查、权限检查和写操作的 @Transactional，不靠删检查解决报错。
 */
public class WorldEntryServiceImpl implements WorldEntryService {
    private final WorldEntryMapper entryMapper;
    private final ProjectAccessService accessService;
    private final edu.njust.narrativestudio.service.ProjectMutationGuard guard;

    public WorldEntryServiceImpl(WorldEntryMapper entryMapper, ProjectAccessService accessService,edu.njust.narrativestudio.service.ProjectMutationGuard guard) {
        this.entryMapper = entryMapper;
        this.accessService = accessService;
        this.guard=guard;
    }

    @Override
    public List<WorldEntryDtos.Summary> list(Long userId, Long projectId, String entryType) {
        // 查询也要检查成员身份；不能为了让页面不报错而删除这行。
        accessService.requireMember(userId, projectId);
        LambdaQueryWrapper<WorldEntry> query = new LambdaQueryWrapper<WorldEntry>()
                // 必须保留项目过滤，新增搜索条件时也不能查到其他项目的数据。
                .eq(WorldEntry::getProjectId, projectId);
        // 【修改点1：类型筛选】传入类型才筛选；null/空白表示不限制类型。
        // 若要求忽略类型参数两端空格，将下面 eq 的第二个参数替换为 entryType.trim()：
        // if (entryType != null && !entryType.isBlank()) {
        //     query.eq(WorldEntry::getEntryType, entryType.trim());
        // }
        if (entryType != null && !entryType.isBlank()) query.eq(WorldEntry::getEntryType, entryType);
        /*
         * 【修改点2：新增标题搜索】不能只粘贴查询条件，还要同步传入 keyword。
         * A. 本方法改为：
         * public List<WorldEntryDtos.Summary> list(Long userId, Long projectId,
         *                                        String entryType, String keyword)
         * B. WorldEntryService 接口的 list 声明也增加 String keyword。
         * C. WorldEntryController.list 参数增加：
         *    @RequestParam(required = false) String keyword
         *    调用改为：entryService.list(currentUser.id(auth), projectId, entryType, keyword)
         * D. 在排序代码前增加：
         * if (keyword != null && !keyword.isBlank()) {
         *     query.like(WorldEntry::getTitle, keyword.trim());
         * }
         * E. 查找 list 的所有调用，修改测试等调用处；不筛选时传 null。
         * 请求示例：GET /api/projects/8/world-entries?keyword=王城
         * 若要页面可操作，还要在前端增加搜索输入并传参。
         * 验证：有匹配、无匹配、空关键字、其他项目有同名条目但不能被查出。
         */
        // 【修改点3：排序】当前排序值越小越靠前；相同时ID越小越靠前。
        // 全部排序值为0时，就按ID升序排列。
        query.orderByAsc(WorldEntry::getSortOrder).orderByAsc(WorldEntry::getId);
        // 以下选一种“替换”上面一行，不能保留原排序后追加：orderBy会累加条件！
        // 最近修改优先：
        // query.orderByDesc(WorldEntry::getUpdatedAt).orderByDesc(WorldEntry::getId);
        // 最早修改优先：
        // query.orderByAsc(WorldEntry::getUpdatedAt).orderByAsc(WorldEntry::getId);
        // 保留手动排序，同排序值时最近修改优先（时间必须放在唯一ID之前）：
        // query.orderByAsc(WorldEntry::getSortOrder)
        //      .orderByDesc(WorldEntry::getUpdatedAt).orderByDesc(WorldEntry::getId);
        // 验证：准备不同排序值、相同排序值及不同更新时间的记录，核对返回顺序。
        // selectList才实际查询；后面把每个Entity转成Summary，再收集为列表。
        return entryMapper.selectList(query).stream().map(this::toSummary).toList();
    }

    @Override
    public WorldEntryDtos.Summary get(Long userId, Long projectId, Long entryId) {
        accessService.requireMember(userId, projectId);
        return toSummary(requireEntry(projectId, entryId));
    }

    @Override
    @Transactional
    public WorldEntryDtos.Summary create(Long userId, Long projectId, WorldEntryDtos.SaveRequest request) {
        // guard.editor内部已检查编辑权限，并在事务中锁定项目记录。
        // 下一行requireEditor是现有的重复校验；不要随意删掉guard或事务注解。
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        LocalDateTime now = LocalDateTime.now();
        WorldEntry entry = new WorldEntry();
        entry.setProjectId(projectId);
        apply(entry, request);
        // 【修改点4：只修改“新增时”的默认排序值】在apply之后、insert之前增加：
        // if (request.sortOrder() == null) {
        //     entry.setSortOrder(10);
        // }
        // 这样只有新增且未提供排序值才默认10；用户明确传0时仍保存0。
        // 不要写在apply之前，否则会被apply中的默认值覆盖。
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        entryMapper.insert(entry);
        return toSummary(entry);
    }

    @Override
    @Transactional
    public WorldEntryDtos.Summary update(Long userId, Long projectId, Long entryId,
                                         WorldEntryDtos.SaveRequest request) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        WorldEntry entry = requireEntry(projectId, entryId);
        // 【修改点5：更新时未传排序值，则保留旧值】将下面apply这一行替换为：
        // Integer oldSortOrder = entry.getSortOrder();
        // apply(entry, request);
        // if (request.sortOrder() == null) {
        //     entry.setSortOrder(oldSortOrder);
        // }
        // 旧值必须在apply之前保存，因为apply会把未传的排序值设置成0。
        // 验证：旧值20、不传排序值仍为20；明确传0则改为0；新增行为不受影响。
        // 这里只让排序字段可省略，不代表标题/正文等字段也支持局部更新。
        apply(entry, request);
        // 更新只修改updatedAt，不要重设createdAt，也不要修改条目的projectId/id。
        entry.setUpdatedAt(LocalDateTime.now());
        entryMapper.updateById(entry);
        return toSummary(entry);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long projectId, Long entryId) {
        guard.editor(userId,projectId);
        // 【修改点6：只有项目创建者可以删除】保留guard，将下一行替换为：
        // accessService.requireOwner(userId, projectId);
        // 只改变本接口，不要把ProjectAccessService中的全局EDIT_ROLES改为只有OWNER。
        // 验证：OWNER允许；EDITOR/TESTER拒绝；归档项目仍拒绝；非本项目条目不能删除。
        accessService.requireEditor(userId, projectId);
        requireEntry(projectId, entryId);
        entryMapper.deleteById(entryId);
    }

    private WorldEntry requireEntry(Long projectId, Long entryId) {
        WorldEntry entry = entryMapper.selectById(entryId);
        if (entry == null || !projectId.equals(entry.getProjectId())) {
            // 【修改点7：修改错误提示】只替换字符串，例如：
            // throw BusinessException.notFound("未找到当前项目下的世界观条目");
            // 不能删除项目归属判断；Long的值比较使用equals，不要换成==。
            // 记录不存在/属于其他项目都返回不存在，避免泄露其他项目数据。
            throw BusinessException.notFound("世界观条目不存在");
        }
        return entry;
    }

    private void apply(WorldEntry entry, WorldEntryDtos.SaveRequest request) {
        /*
         * 【修改点8：标题长度等参数限制】主要在WorldEntryDtos.SaveRequest中修改，
         * 不是在这里截断字符串。标题最多50字符的字段定义示例：
         * @NotBlank @Size(max = 50) String title
         * Controller需保留@Valid；同步核对前端限制。
         * 验证空标题、50字符和51字符。放宽长度时还必须核对MySQL列长度。
         * 本方法依赖请求已通过非空校验；直接从其他Java代码调用Service时，
         * 不会仅因为DTO有注解就自动触发Controller的@Valid。
         */
        entry.setEntryType(request.entryType());
        // trim仅去掉两端空白，不清除标题中间的空格。
        entry.setTitle(request.title().trim());
        // 若明确要求保留正文首尾空白，替换为 entry.setContent(request.content());
        // 这会同时影响新增和修改；正文@NotBlank约束仍保留。
        entry.setContent(request.content().trim());
        // 【修改点9：新增和修改统一使用默认排序10】将下面这一行替换为：
        // entry.setSortOrder(request.sortOrder() == null ? 10 : request.sortOrder());
        // 修改的是三元表达式问号后的默认值；不是把所有条目的排序强制改成10。
        // 此改动不批量更新数据库旧记录，只影响之后调用apply且未传排序值的请求。
        entry.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private WorldEntryDtos.Summary toSummary(WorldEntry entry) {
        /*
         * 【修改点10：返回创建时间】数据库和Entity已有createdAt，无需新增列。
         * A. 在WorldEntryDtos.Summary的参数末尾追加：LocalDateTime createdAt
         * B. 在下面new Summary的参数末尾追加：entry.getCreatedAt()
         *    即最后一段改为：
         *    entry.getTitle(), entry.getContent(), entry.getSortOrder(),
         *    entry.getUpdatedAt(), entry.getCreatedAt());
         * C. 查找其他new WorldEntryDtos.Summary(...)，同步补参数；参数顺序要与DTO一致。
         * D. 检查列表、详情、新增、修改响应；若要页面显示，前端类型/页面也需修改。
         * 对于数据库尚未存在的新字段，不能只在这里加参数：还需SQL增量、Entity、
         * 请求DTO、apply赋值及测试；已有库不可通过重建表来实现字段修改。
         */
        return new WorldEntryDtos.Summary(entry.getId(), entry.getProjectId(), entry.getEntryType(),
                entry.getTitle(), entry.getContent(), entry.getSortOrder(), entry.getUpdatedAt());
    }
}
