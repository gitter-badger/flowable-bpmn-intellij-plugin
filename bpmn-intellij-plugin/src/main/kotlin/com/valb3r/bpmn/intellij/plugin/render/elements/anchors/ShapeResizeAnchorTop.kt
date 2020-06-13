package com.valb3r.bpmn.intellij.plugin.render.elements.anchors

import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.events.Event
import com.valb3r.bpmn.intellij.plugin.render.*
import com.valb3r.bpmn.intellij.plugin.render.elements.RenderState
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.util.*
import javax.swing.Icon

// TODO code duplication with ShapeResizeAnchorBottom
class ShapeResizeAnchorTop(
        override val elementId: DiagramElementId,
        private val bottomPoint: Point2D.Float,
        private val onDragEndCallback: (() -> MutableList<Event>),
        state: RenderState
) : IconAnchorElement(elementId, bottomPoint, state) {

    override fun currentRect(camera: Camera): Rectangle2D.Float {
        val icon = icon()
        val iconLeft = camera.fromCameraView(Point2D.Float(0.0f, 0.0f))
        val iconRight = camera.fromCameraView(Point2D.Float(icon.iconWidth.toFloat(), icon.iconHeight.toFloat()))
        val width = iconRight.x - iconLeft.x
        val height = iconRight.y - iconLeft.y

        return viewTransform.transform(
                Rectangle2D.Float(
                        bottomPoint.x - width,
                        bottomPoint.y - height,
                        width,
                        height
                )
        )
    }

    override fun doOnDragEndWithoutChildren(dx: Float, dy: Float, droppedOn: BpmnElementId?, allDroppedOn: SortedMap<AreaType, BpmnElementId>): MutableList<Event> {
        val events = mutableListOf<Event>()
        events += super.doOnDragEndWithoutChildren(dx, dy, droppedOn, allDroppedOn)
        events += onDragEndCallback()
        return events
    }

    override fun doRenderWithoutChildren(ctx: RenderContext): Map<DiagramElementId, AreaWithZindex> {
        if (!isVisible() || multipleElementsSelected()) {
            return mutableMapOf()
        }

        return super.doRenderWithoutChildren(ctx)
    }

    override fun icon(): Icon {
        return state.icons.dragToResizeTop
    }

    override fun acceptsInternalEvents(): Boolean {
        return false
    }

    override fun zIndex(): Int {
        return ICON_Z_INDEX
    }
}