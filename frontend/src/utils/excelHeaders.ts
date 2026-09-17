// Guess only unambiguous aliases. Users can map any other header by its column index.
const aliases:Record<string,string[]>={
  nodeKey:["节点编号","节点id","节点标识","剧情编号","剧情id","场景编号","章节编号","nodekey","nodeid","id"],
  title:["节点标题","剧情标题","场景标题","章节标题","节点名称","剧情名称","标题","名称","title","nodetitle","name"],
  content:["剧情内容","节点内容","正文","内容","对白","对话内容","剧本","content","body","text"],
  nodeType:["节点类型","剧情类型","类型","nodetype","type"],
  scene:["场景","场景名称","地点","所属章节","章节","scene","location","chapter"],
  isStart:["是否起点","起点标记","是否开始","isstart","start"],
  sourceNodeKey:["源节点","源节点编号","起始节点","来源节点","来源编号","当前节点","source","sourceid","sourcenodekey","from"],
  targetNodeKey:["目标节点","目标节点编号","下一节点","跳转目标","跳转节点","目标编号","target","targetid","targetnodekey","next","to"],
  choiceText:["选项文本","选择文本","选项","选择","连线文本","分支文本","choicetext","choice","label"],
};
export function normalizeHeader(value:string) {return value.trim().toLowerCase().replace(/[\s_\-()（）]/g,"");}
export function guessColumns(headers:string[],fields:string[]) {
  const result:Record<string,number>={};const used=new Set<number>();
  for(const field of fields) {
    const found=headers.map((h,i)=>aliases[field]?.includes(normalizeHeader(h))?i:-1).filter(i=>i>=0);
    if(found.length===1&&!used.has(found[0])) {result[field]=found[0];used.add(found[0]);}
  }
  return result;
}
export function guessHeaderRow(rows:string[][],fields:string[]) {
  let best=0,score=0;
  rows.forEach((row,i)=>{const n=Object.keys(guessColumns(row,fields)).length;if(n>score){score=n;best=i;}});
  return best+1;
}
export function guessBranches(headers:string[],used:number[]) {
  const result:{targetColumn:number;textColumn?:number}[]=[];
  const plain=guessColumns(headers,["targetNodeKey","choiceText"]);
  if(plain.targetNodeKey!==undefined&&!used.includes(plain.targetNodeKey))
    result.push({targetColumn:plain.targetNodeKey,textColumn:plain.choiceText});
  const normalized=headers.map(normalizeHeader);
  normalized.forEach((h,i)=>{
    if(used.includes(i)||result.some(b=>b.targetColumn===i))return;
    const match=h.match(/^(?:选项|选择|分支|option|choice)(\d+)(?:目标|跳转|目标节点|target)$/)
      ?? h.match(/^(?:目标|跳转|目标节点|target)(\d+)$/);
    if(!match)return;
    const id=match[1];
    const candidates=normalized.map((x,j)=>new RegExp("^(?:选项|选择|分支|option|choice)"+id+"(?:文本|text)?$").test(x)?j:-1).filter(j=>j>=0);
    result.push({targetColumn:i,...(candidates.length===1?{textColumn:candidates[0]}:{})});
  });
  return result;
}
