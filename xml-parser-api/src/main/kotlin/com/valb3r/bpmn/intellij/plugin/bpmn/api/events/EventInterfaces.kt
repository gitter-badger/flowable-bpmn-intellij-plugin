package com.valb3r.bpmn.intellij.plugin.bpmn.api.events

import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.WithParentId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.ShapeElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.Translatable
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.WaypointElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.WithDiagramId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.info.Property
import com.valb3r.bpmn.intellij.plugin.bpmn.api.info.PropertyType
import java.awt.geom.Point2D

interface Event

data class EventBlock(val size: Int)

interface EventOrder<T: Event> {
    val order: Int
    val event: T
    val block: EventBlock?
}

interface LocationUpdateWithId: Event {
    val diagramElementId: DiagramElementId
    val dx: Float
    val dy: Float
    val parentElementId: DiagramElementId?
    val internalPos: Int?
}

interface NewWaypoints: Event {
    val edgeElementId: DiagramElementId
    val waypoints: List<IdentifiableWaypoint>
    val epoch: Int
}

interface DiagramElementRemoved: Event {
    val elementId: DiagramElementId
}

interface BpmnElementRemoved: Event {
    val elementId: BpmnElementId
}

interface BpmnShapeObjectAdded: Event {
    val bpmnObject: WithParentId
    val shape: ShapeElement
    val props: Map<PropertyType, Property>
}

interface BpmnShapeResizedAndMoved: Event {
    val diagramElementId: DiagramElementId
    val cx: Float
    val cy: Float
    val coefW: Float
    val coefH: Float

    fun transform(point: Point2D.Float): Point2D.Float
}

interface BpmnEdgeObjectAdded: Event {
    val bpmnObject: WithParentId
    val edge: EdgeWithIdentifiableWaypoints
    val props: Map<PropertyType, Property>
}

interface BpmnParentChanged: Event {
    val bpmnElementId: BpmnElementId
    val newParentId: BpmnElementId
    val propagateToXml: Boolean
}

interface PropertyUpdateWithId: Event {
    val bpmnElementId: BpmnElementId
    val property: PropertyType
    val newValue: Any
    val referencedValue: Any?
    val newIdValue: BpmnElementId?
}

interface IdentifiableWaypoint: Translatable<IdentifiableWaypoint>, WithDiagramId {
    val x: Float
    val y: Float
    val origX: Float
    val origY: Float
    val physical: Boolean
    val internalPhysicalPos: Int

    fun moveTo(dx: Float, dy: Float): IdentifiableWaypoint
    fun asPhysical(): IdentifiableWaypoint
    fun originalLocation(): IdentifiableWaypoint
    fun asWaypointElement(): WaypointElement
}

interface EdgeWithIdentifiableWaypoints: WithDiagramId {
    val bpmnElement: BpmnElementId?
    val waypoint: MutableList<IdentifiableWaypoint>
    val epoch: Int

    fun updateBpmnElemId(newId: BpmnElementId): EdgeWithIdentifiableWaypoints
}