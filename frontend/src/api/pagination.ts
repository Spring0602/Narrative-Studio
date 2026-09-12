import type { Page } from "./playtests";

/** Follow all pages instead of silently showing only the first 100 records. */
export async function loadAllPages<T>(
  fetchPage: (page: number, size: number) => Promise<Page<T>>,
  maxRecords = 10000,
): Promise<Page<T>> {
  const first = await fetchPage(1, 100);
  if (first.total > maxRecords || first.pages > Math.ceil(maxRecords / 100)) {
    throw new Error(`记录超过 ${maxRecords} 条，暂不支持一次展示全部记录，请缩小数据范围`);
  }
  const items = [...first.items];
  for (let page = 2; page <= first.pages; page++) {
    const next = await fetchPage(page, 100);
    if (next.total !== first.total) {
      throw new Error("记录在加载期间发生变化，请刷新后重试");
    }
    items.push(...next.items);
  }
  return { ...first, items };
}
