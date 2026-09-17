import { AxiosHeaders, type AxiosAdapter } from "axios";

const now = "2026-09-11T13:21:03+08:00";

const members = [
  {
    id: 1,
    userId: 1,
    username: "owner",
    displayName: "项目创建者",
    memberRole: "OWNER",
    joinedAt: "2026-09-09T10:00:00+08:00",
  },
  {
    id: 2,
    userId: 2,
    username: "writer01",
    displayName: "剧情编辑",
    memberRole: "EDITOR",
    joinedAt: "2026-09-09T11:00:00+08:00",
  },
  {
    id: 3,
    userId: 3,
    username: "tester02",
    displayName: "体验测试员",
    memberRole: "TESTER",
    joinedAt: "2026-09-09T19:07:29+08:00",
  },
];

const worldEntries = [
  {
    id: 1,
    projectId: 1,
    entryType: "LOCATION",
    title: "雾港",
    content: "终年被潮雾笼罩的旧港，废弃航线与秘密交易在此交汇。",
    sortOrder: 10,
    updatedAt: now,
  },
  {
    id: 2,
    projectId: 1,
    entryType: "FACTION",
    title: "星图议会",
    content: "负责维护航线与星图档案的组织，对失踪事件保持沉默。",
    sortOrder: 20,
    updatedAt: now,
  },
  {
    id: 3,
    projectId: 1,
    entryType: "RULE",
    title: "潮汐记忆",
    content: "每次大潮都会改变部分航标，也会唤醒被掩埋的记忆。",
    sortOrder: 30,
    updatedAt: now,
  },
];

const characters = [
  {
    id: 1,
    projectId: 1,
    name: "林澈",
    summary: "年轻的空艇领航员，正在调查父亲失踪的真相。",
    personality: "敏锐、克制、重视承诺",
    goal: "找到父亲留下的最后一张航线图",
    valueOrder: "家人 > 真相 > 责任 > 规则",
    status: "ACTIVE",
    updatedAt: now,
  },
  {
    id: 2,
    projectId: 1,
    name: "苏弥",
    summary: "星图议会派驻雾港的年轻记录官。",
    personality: "理性、谨慎、富有同理心",
    goal: "找回失窃的传送门星图",
    valueOrder: "秩序 > 责任 > 真相 > 个人感情",
    status: "ACTIVE",
    updatedAt: now,
  },
  {
    id: 3,
    projectId: 1,
    name: "岑叔",
    summary: "经营雾港旧船坞的修理师，熟悉被废弃的夜间航线。",
    personality: "沉稳、寡言、外冷内热",
    goal: "保护雾港居民并偿还旧日承诺",
    valueOrder: "承诺 > 同伴 > 自由 > 权威",
    status: "ACTIVE",
    updatedAt: now,
  },
];

const nodes = [
  {
    id: 1,
    projectId: 1,
    nodeKey: "START_HARBOR",
    title: "雾港来信",
    content: "林澈在清晨收到一封没有署名的信。",
    nodeType: "NORMAL",
    scene: "旧船坞",
    isStart: true,
    positionX: 80,
    positionY: 160,
    updatedAt: now,
  },
  {
    id: 2,
    projectId: 1,
    nodeKey: "MAP_ROOM",
    title: "封存的星图室",
    content: "尘封的星图上出现了一条从未记录的航线。",
    nodeType: "NORMAL",
    scene: "议会档案馆",
    isStart: false,
    positionX: 390,
    positionY: 80,
    updatedAt: now,
  },
  {
    id: 3,
    projectId: 1,
    nodeKey: "NIGHT_ROUTE",
    title: "夜航之前",
    content: "众人必须决定是否相信岑叔提供的坐标。",
    nodeType: "NORMAL",
    scene: "雾港灯塔",
    isStart: false,
    positionX: 390,
    positionY: 250,
    updatedAt: now,
  },
  {
    id: 4,
    projectId: 1,
    nodeKey: "ENDING_TRUTH",
    title: "潮汐之后",
    content: "航线重新点亮，失踪事件的真相终于浮出水面。",
    nodeType: "ENDING",
    scene: "晨曦航道",
    isStart: false,
    positionX: 720,
    positionY: 160,
    updatedAt: now,
  },
];

const choices = [
  {
    id: 1,
    projectId: 1,
    sourceNodeId: 1,
    targetNodeId: 2,
    choiceText: "前往议会查阅旧星图",
    sortOrder: 10,
    enabled: true,
    updatedAt: now,
  },
  {
    id: 2,
    projectId: 1,
    sourceNodeId: 1,
    targetNodeId: 3,
    choiceText: "先去旧船坞寻找线索",
    sortOrder: 20,
    enabled: true,
    updatedAt: now,
  },
  {
    id: 3,
    projectId: 1,
    sourceNodeId: 2,
    targetNodeId: 4,
    choiceText: "公开星图并启动航线",
    sortOrder: 10,
    enabled: true,
    updatedAt: now,
  },
  {
    id: 4,
    projectId: 1,
    sourceNodeId: 3,
    targetNodeId: 4,
    choiceText: "相信岑叔并进入潮汐区",
    sortOrder: 10,
    enabled: true,
    updatedAt: now,
  },
];

const variables = [
  {
    id: 1,
    projectId: 1,
    variableKey: "trust_sumi",
    displayName: "苏弥信任度",
    valueType: "INTEGER",
    initialValue: "0",
    description: "记录苏弥对主角的信任程度",
  },
  {
    id: 2,
    projectId: 1,
    variableKey: "found_map",
    displayName: "已取得星图",
    valueType: "BOOLEAN",
    initialValue: "false",
    description: "是否已经找到封存星图",
  },
  {
    id: 3,
    projectId: 1,
    variableKey: "route_name",
    displayName: "选择的航线",
    valueType: "STRING",
    initialValue: "未决定",
    description: "记录玩家最终选择的航线",
  },
];

const playtest = {
  id: 1,
  projectId: 1,
  testerId: 3,
  status: "RUNNING",
  currentNode: {
    id: 1,
    nodeKey: "START_HARBOR",
    title: "雾港来信",
    content: "林澈在清晨收到一封没有署名的信。",
    nodeType: "NORMAL",
  },
  stepNo: 0,
  state: { trust_sumi: "0", found_map: "false", route_name: "未决定" },
  knowledge: { 林澈: "知道父亲曾进入潮汐区" },
  availableChoices: [
    { id: 1, targetNodeId: 2, choiceText: "前往议会查阅旧星图", sortOrder: 10 },
    { id: 2, targetNodeId: 3, choiceText: "先去旧船坞寻找线索", sortOrder: 20 },
  ],
  deadEnd: false,
  startedAt: now,
};

const page = <T>(items: T[]) => ({
  items,
  page: 1,
  size: 100,
  total: items.length,
  pages: items.length ? 1 : 0,
});

function dataFor(url: string) {
  if (/\/members$/.test(url)) return members;
  if (/\/world-entries$/.test(url)) return worldEntries;
  if (/\/character-relations$/.test(url))
    return [
      {
        id: 1,
        projectId: 1,
        sourceCharacterId: 1,
        targetCharacterId: 2,
        relationType: "合作伙伴",
        description: "因调查失踪航线而暂时结盟",
      },
    ];
  if (/\/characters\/\d+\/knowledge$/.test(url))
    return [
      {
        id: 1,
        projectId: 1,
        characterId: 1,
        knowledgeKey: "hidden_route",
        knowledgeLevel: "SUSPECTED",
        description: "怀疑父亲曾进入未登记的航线",
        acquiredNodeId: 1,
      },
    ];
  if (/\/story-nodes\/\d+\/characters$/.test(url)) return [1, 2];
  if (/\/characters$/.test(url)) return characters;
  if (/\/story-nodes\/\d+\/choices$/.test(url)) {
    const nodeId = Number(url.match(/story-nodes\/(\d+)/)?.[1]);
    return choices.filter((choice) => choice.sourceNodeId === nodeId);
  }
  if (/\/story-nodes\/\d+\/choices\/\d+\/rules$/.test(url))
    return {
      choiceId: 1,
      conditions: [
        {
          id: 1,
          variableId: 1,
          operator: "GTE",
          expectedValue: "0",
          conditionGroup: 1,
          sortOrder: 10,
        },
      ],
      effects: [
        {
          id: 1,
          variableId: 1,
          operation: "ADD",
          operandValue: "1",
          sortOrder: 10,
        },
      ],
    };
  if (/\/story-nodes$/.test(url)) return { nodes, choices };
  if (/\/state-variables$/.test(url)) return variables;
  if (/\/choice-drafts$/.test(url))
    return page([
      {
        id: 1,
        projectId: 1,
        sourceNodeId: 3,
        choiceText: "暂缓启航，等待潮雾散去",
        sortOrder: 30,
        createdBy: 2,
        createdAt: now,
        updatedAt: now,
      },
    ]);
  if (/\/playtests\/\d+\/steps$/.test(url)) return page([]);
  if (/\/playtests\/\d+$/.test(url)) return playtest;
  if (/\/playtests$/.test(url)) return page([playtest]);
  if (/\/releases$/.test(url))
    return page([
      {
        id: 1,
        projectId: 1,
        versionNo: 1,
        publishedBy: 1,
        publishedAt: now,
        schemaVersion: 1,
      },
    ]);
  if (/\/issues$/.test(url))
    return page([
      {
        id: 1,
        projectId: 1,
        issueType: "DEAD_END",
        severity: "WARNING",
        targetType: "NODE",
        targetId: 3,
        message: "节点“夜航之前”需要检查是否保留备用出口",
        status: "OPEN",
        detectedAt: now,
      },
    ]);
  if (/\/feedback$/.test(url))
    return page([
      {
        id: 1,
        projectId: 1,
        sessionId: 1,
        reporterId: 3,
        title: "星图选项提示可以更清楚",
        description: "建议在选择前展示该选项可能影响的状态类别。",
        status: "OPEN",
        createdAt: now,
        updatedAt: now,
      },
    ]);
  if (/\/account$/.test(url))
    return {
      id: 1,
      username: "preview_owner",
      displayName: "预览用户",
      email: "preview@example.com",
      emailVerifiedAt: now,
      role: "USER",
      status: "ACTIVE",
    };
  return null;
}

function requestData<T>(data: unknown): T {
  return (typeof data === "string" ? JSON.parse(data) : data) as T;
}

function createPreviewNode(input: Record<string, unknown>) {
  const created = {
    ...input,
    id: Math.max(0, ...nodes.map((node) => node.id)) + 1,
    projectId: 1,
    updatedAt: new Date().toISOString(),
  };
  nodes.push(created as (typeof nodes)[number]);
  return created;
}

function responseData(config: Parameters<AxiosAdapter>[0]) {
  const url = config.url || "";
  const method = config.method?.toUpperCase() || "GET";
  if (method === "POST" && /\/story-nodes\/batch$/.test(url)) {
    const payload = requestData<{ nodes: Record<string, unknown>[] }>(
      config.data,
    );
    return payload.nodes.map(createPreviewNode);
  }
  if (method === "POST" && /\/story-nodes$/.test(url)) {
    return createPreviewNode(requestData<Record<string, unknown>>(config.data));
  }
  return dataFor(url);
}

export const previewAdapter: AxiosAdapter = async (config) => ({
  data: { success: true, data: responseData(config) },
  status: 200,
  statusText: "OK",
  headers: new AxiosHeaders({ "content-type": "application/json" }),
  config,
});
