@file:Suppress("ReplaceGetOrSet")

package com.intellij.tools.ide.metrics.collector.telemetry

internal class OpentelemetryJsonParserWithChildrenFiltering(
  spanFilter: SpanFilter,
  private val childFilter: SpanFilter,
) : OpentelemetryJsonParser(spanFilter) {
  override fun processChild(result: MutableSet<SpanElement>, parent: SpanElement, index: Map<String, Collection<SpanElement>>) {
    index.get(parent.spanId)?.forEach {
      if (parent.isWarmup) {
        it.isWarmup = true
      }
      if (!childFilter.filter(it)) {
        return@forEach
      }
      result.add(it)
      processChild(result, it, index)
    }
  }
}

