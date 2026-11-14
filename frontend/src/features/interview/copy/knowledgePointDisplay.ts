const KNOWLEDGE_POINT_DISPLAY = new Map([
  ['collection_semantics', '集合的选择与语义'],
  ['transaction_acid', '事务的 ACID 特性'],
])

export function displayKnowledgePoint(knowledgePoint: string): string {
  return KNOWLEDGE_POINT_DISPLAY.get(knowledgePoint) ?? knowledgePoint
}
