package com.valb3r.bpmn.intellij.plugin.render

import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.events.Event
import com.valb3r.bpmn.intellij.plugin.events.ProcessModelUpdateEvents
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import kotlin.math.max
import kotlin.math.min

data class ElementInteractionContext(
        val draggedIds: Set<DiagramElementId>,
        val dragEndCallbacks: MutableMap<DiagramElementId, (dx: Float, dy: Float, droppedOn: BpmnElementId?) -> List<Event>>,
        val dragSelectionRect: SelectionRect?,
        val clickCallbacks: MutableMap<DiagramElementId, (dest: ProcessModelUpdateEvents) -> Unit>,
        val anchorsHit: AnchorHit?,
        val start: Point2D.Float,
        val current: Point2D.Float
)

data class AnchorHit(val dragged: Point2D.Float, val anchors: Map<AnchorType, Point2D.Float>)

enum class AnchorType {
    VERTICAL,
    HORIZONTAL,
    POINT
}

data class SelectionRect(val start: Point2D.Float, val end: Point2D.Float) {

    fun toRect(): Rectangle2D.Float {
        val xmin = min(start.x, end.x)
        val ymin = min(start.y, end.y)
        val xmax = max(start.x, end.x)
        val ymax = max(start.y, end.y)
        return Rectangle2D.Float(xmin, ymin, xmax - xmin, ymax - ymin)
    }
}