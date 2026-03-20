import type { XmlNode } from '@/utils/markdownToXml'

/**
 * Check if a node is expandable (has children).
 */
export function isExpandable(node: XmlNode): boolean {
  return node.children.length > 0
}

/**
 * Compute the set of expanded keys for the default expand state.
 * Only level-1 nodes (top-level) are expanded by default.
 */
export function computeDefaultExpandedKeys(nodes: XmlNode[], defaultExpandLevel: number = 1): Set<string> {
  const keys = new Set<string>()
  collectKeysAtLevel(nodes, 1, defaultExpandLevel, keys)
  return keys
}

function collectKeysAtLevel(nodes: XmlNode[], currentDepth: number, maxDepth: number, keys: Set<string>): void {
  for (const node of nodes) {
    if (node.children.length > 0 && currentDepth <= maxDepth) {
      keys.add(node.key)
      collectKeysAtLevel(node.children, currentDepth + 1, maxDepth, keys)
    }
  }
}

/**
 * Compute the set of all expandable (non-leaf) node keys in the tree.
 * Used by "Expand All".
 */
export function computeAllExpandableKeys(nodes: XmlNode[]): Set<string> {
  const keys = new Set<string>()
  collectAllExpandable(nodes, keys)
  return keys
}

function collectAllExpandable(nodes: XmlNode[], keys: Set<string>): void {
  for (const node of nodes) {
    if (node.children.length > 0) {
      keys.add(node.key)
      collectAllExpandable(node.children, keys)
    }
  }
}
