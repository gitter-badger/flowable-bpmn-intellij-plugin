package com.valb3r.bpmn.intellij.plugin.render

import com.google.common.annotations.VisibleForTesting
import com.google.common.cache.CacheBuilder
import com.valb3r.bpmn.intellij.plugin.Colors
import com.valb3r.bpmn.intellij.plugin.bpmn.api.BpmnProcessObjectView
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.BpmnSequenceFlow
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElementId
import com.valb3r.bpmn.intellij.plugin.events.updateEventsRegistry
import com.valb3r.bpmn.intellij.plugin.properties.PropertiesVisualizer
import com.valb3r.bpmn.intellij.plugin.properties.propertiesVisualizer
import com.valb3r.bpmn.intellij.plugin.state.currentStateProvider
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Canvas(private val settings: CanvasConstants) : JPanel() {
    private val stateProvider = currentStateProvider()

    private var selectedElements: MutableSet<DiagramElementId> = mutableSetOf()
    private var camera = Camera(settings.defaultCameraOrigin, Point2D.Float(settings.defaultZoomRatio, settings.defaultZoomRatio))
    private var interactionCtx: ElementInteractionContext = ElementInteractionContext(emptySet(), emptySet(), mutableMapOf(), null, mutableMapOf(), null, Point2D.Float(), Point2D.Float())
    private var renderer: BpmnProcessRenderer? = null
    private var areaByElement: Map<DiagramElementId, AreaWithZindex>? = null
    private var propsVisualizer: PropertiesVisualizer? = null

    private val cachedIcons = CacheBuilder.newBuilder()
            .expireAfterAccess(10L, TimeUnit.SECONDS)
            .maximumSize(100)
            .build<String, BufferedImage>()

    @VisibleForTesting
    public override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)

        val graphics2D = setupGraphics(graphics)
        areaByElement = renderer?.render(
                RenderContext(
                        CanvasPainter(graphics2D, camera.copy(), cachedIcons),
                        selectedElements.toSet(),
                        interactionCtx.copy(),
                        stateProvider
                )
        )
    }

    fun reset(fileContent: String, processObject: BpmnProcessObjectView, renderer: BpmnProcessRenderer) {
        this.renderer = renderer
        this.camera = Camera(settings.defaultCameraOrigin, Point2D.Float(settings.defaultZoomRatio, settings.defaultZoomRatio))
        this.propsVisualizer = propertiesVisualizer()
        this.propsVisualizer?.clear()
        this.stateProvider.resetStateTo(fileContent, processObject)
        selectedElements = mutableSetOf()
        repaint()
    }

    fun click(location: Point2D.Float) {
        val clickedElements = elemUnderCursor(location)
        clickedElements.forEach { interactionCtx.clickCallbacks[it]?.invoke(updateEventsRegistry()) }

        this.selectedElements.clear()
        interactionCtx = ElementInteractionContext(emptySet(), emptySet(), mutableMapOf(), null, mutableMapOf(), null, Point2D.Float(), Point2D.Float())
        this.selectedElements.addAll(clickedElements)

        repaint()

        val propertiesForElement = if (selectedElements.isEmpty()) elemUnderCursor(location, setOf()) else selectedElements
        val elementIdForPropertiesTable = propertiesForElement.firstOrNull()
        val state = stateProvider.currentState()
        state
                .elementByDiagramId[elementIdForPropertiesTable]
                ?.let { elemId ->
                    state.elemPropertiesByStaticElementId[elemId]?.let { propsVisualizer?.visualize(state.elemPropertiesByStaticElementId, elemId) }
                } ?: propsVisualizer?.clear()
    }

    fun dragCanvas(start: Point2D.Float, current: Point2D.Float) {
        val newCameraOrigin = Point2D.Float(
                camera.origin.x - current.x + start.x,
                camera.origin.y - current.y + start.y
        )
        camera = Camera(newCameraOrigin, Point2D.Float(camera.zoom.x, camera.zoom.y))
        repaint()
    }

    fun startCanvasDragWithButton(current: Point2D.Float) {
        val point = camera.fromCameraView(current)
        val elemsUnderCursor = elemUnderCursor(current)
        if (selectedElements.intersect(elemsUnderCursor).isEmpty()) {
            interactionCtx = ElementInteractionContext(emptySet(), emptySet(), mutableMapOf(), null, mutableMapOf(), null, point, point)
            return
        }
    }

    fun startSelectionOrDrag(current: Point2D.Float) {
        val point = camera.fromCameraView(current)
        if (selectedElements.isNotEmpty()) {
            interactionCtx = interactionCtx.copy(
                    draggedIds = selectedElements,
                    dragTargetedIds = emptySet(),
                    dragStart = point,
                    dragCurrent = point
            )
            return
        }

        val elemsUnderCursor = elemUnderCursor(current)

        if (elemsUnderCursor.isEmpty()) {
            interactionCtx = ElementInteractionContext(emptySet(), emptySet(), mutableMapOf(), SelectionRect(current, current), mutableMapOf(), null, point, point)
            return
        }

        interactionCtx = interactionCtx.copy(
                draggedIds = selectedElements,
                dragTargetedIds = emptySet(),
                dragStart = point,
                dragCurrent = point,
                dragSelectionRect = SelectionRect(current, current)
        )
    }

    fun attractToAnchors(ctx: ElementInteractionContext): ElementInteractionContext {
        val anchors: MutableSet<AnchorDetails> = mutableSetOf()
        val cameraPoint = camera.toCameraView(ctx.dragCurrent)
        val dragged = ctx.draggedIds.minBy {
            val bounds = areaByElement?.get(it)?.area?.bounds2D ?: Rectangle2D.Float()
            return@minBy Point2D.Float(bounds.centerX.toFloat(), bounds.centerY.toFloat()).distance(cameraPoint)
        }

        val draggedArea = areaByElement?.get(dragged) ?: return ctx
        val draggedType =  draggedArea.areaType
        val draggedAnchors = if (AreaType.SHAPE == draggedType) {
            draggedArea.anchorsForShape
        } else {
            draggedArea.anchorsForWaypoints
        }

        if (draggedType == AreaType.SELECTS_DRAG_TARGET) {
            return selectDragTarget(dragged!!, ctx)
        }

        val anchorsToSearchIn = areaByElement
                ?.filter { !ctx.draggedIds.contains(it.key) }
                // shape is not affected by waypoints
                ?.filter {
                    if (draggedType == AreaType.SHAPE || draggedType == AreaType.SHAPE_THAT_NESTS) {
                        it.value.areaType == AreaType.SHAPE || it.value.areaType == AreaType.SHAPE_THAT_NESTS
                    } else {
                        true
                    }
                }

        for ((_, searchIn) in anchorsToSearchIn?.filter { it.value.index <= draggedArea.index }.orEmpty()) {
            for (draggedAnchor in draggedAnchors) {
                val targetAnchors = if (AreaType.SHAPE == draggedType) searchIn.anchorsForShape else searchIn.anchorsForWaypoints
                for (anchor in targetAnchors) {
                    val attractsX = abs(draggedAnchor.point.x - anchor.point.x) < settings.anchorAttractionThreshold
                    val attractsY = abs(draggedAnchor.point.y - anchor.point.y) < settings.anchorAttractionThreshold

                    if (attractsX && attractsY) {
                        anchors += AnchorDetails(anchor.point, draggedAnchor.point, AnchorType.POINT)
                    } else if (attractsX) {
                        anchors += AnchorDetails(anchor.point, draggedAnchor.point, AnchorType.HORIZONTAL)
                    } else if (attractsY) {
                        anchors += AnchorDetails(anchor.point, draggedAnchor.point, AnchorType.VERTICAL)
                    }
                }
            }
        }

        val pointAnchor = anchors.filter { it.type == AnchorType.POINT }.minBy { it.anchor.distance(it.objectAnchor) }
        val anchorX = anchors.filter { it.type == AnchorType.HORIZONTAL }.minBy { it.anchor.distance(it.objectAnchor) }
        val anchorY = anchors.filter { it.type == AnchorType.VERTICAL }.minBy { it.anchor.distance(it.objectAnchor) }

        val selectedAnchors: AnchorHit = if (null == pointAnchor) applyOrthoAnchors(anchorX, anchorY, ctx) else applyPointAnchor(pointAnchor, ctx)

        return ctx.copy(
                dragCurrent = Point2D.Float(selectedAnchors.dragged.x, selectedAnchors.dragged.y),
                anchorsHit = selectedAnchors
        )
    }

    private fun selectDragTarget(dragged: DiagramElementId, ctx: ElementInteractionContext): ElementInteractionContext {
        val areas = areaByElement!!
        val area = areas[dragged]!!.area
        val point = Point2D.Float(area.bounds2D.centerX.toFloat(), area.bounds2D.centerY.toFloat())
        val target = dragTargettableElements(cursorRect(point))
                .filter { !ctx.draggedIds.contains(it) }
                .filter { areas[it]?.areaType == AreaType.SHAPE || areas[it]?.areaType == AreaType.SHAPE_THAT_NESTS }
                .maxBy { areas[it]?.index ?: ICON_Z_INDEX }

        return ctx.copy(dragTargetedIds = if (null != target) setOf(target) else emptySet())
    }

    fun dragWithWheel(previous: Point2D.Float, current: Point2D.Float) {
        dragCanvas(previous, current)
    }

    fun dragOrSelectWithLeftButton(previous: Point2D.Float, current: Point2D.Float) {
        val point = camera.fromCameraView(current)
        if (null != interactionCtx.dragSelectionRect) {
            interactionCtx = interactionCtx.copy(dragSelectionRect = SelectionRect(
                    interactionCtx.dragSelectionRect!!.start,
                    current
            ))

            this.selectedElements.addAll(
                    elemsUnderRect(
                            interactionCtx.dragSelectionRect!!.toRect(),
                            excludeAreas = setOf(AreaType.PARENT_PROCESS_SHAPE, AreaType.SHAPE_THAT_NESTS, AreaType.SHAPE_ATTACHED_TO_ELEMENT)
                    )
            )

            repaint()
            return
        }

        if (selectedElements.isEmpty() && interactionCtx.draggedIds.isEmpty()) {
            dragCanvas(previous, current)
            return
        }

        interactionCtx = interactionCtx.copy(dragCurrent = point)
        interactionCtx = attractToAnchors(interactionCtx)
        repaint()
    }

    fun stopDragOrSelect() {
        if (null != interactionCtx.dragSelectionRect) {
            interactionCtx = ElementInteractionContext(emptySet(), emptySet(), mutableMapOf(), null, mutableMapOf(), null, Point2D.Float(), Point2D.Float())
            repaint()
            return
        }

        if (interactionCtx.draggedIds.isNotEmpty() && (interactionCtx.dragCurrent.distance(interactionCtx.dragStart) > settings.epsilon)) {
            interactionCtx = attractToAnchors(interactionCtx)
            val dx = interactionCtx.dragCurrent.x - interactionCtx.dragStart.x
            val dy = interactionCtx.dragCurrent.y - interactionCtx.dragStart.y
            updateEventsRegistry().addEvents(interactionCtx.draggedIds.flatMap {
                val droppedOn = bpmnElemsUnderDragCurrent()
                interactionCtx.dragEndCallbacks[it]?.invoke(
                        dx,
                        dy,
                        if (droppedOn.isEmpty()) null else droppedOn.keys.first(),
                        droppedOn)
                        ?: emptyList()
            })
        }

        interactionCtx = interactionCtx.copy(draggedIds = emptySet(), dragTargetedIds = mutableSetOf(), dragCurrent = interactionCtx.dragStart, dragSelectionRect = null)
        repaint()
    }

    fun zoom(anchor: Point2D.Float, factor: Int) {
        val scale = Math.pow(settings.zoomFactor.toDouble(), factor.toDouble()).toFloat()

        if (min(camera.zoom.x, camera.zoom.y) * scale < 0.3f || max(camera.zoom.x, camera.zoom.y) * scale > 2.0f) {
            return
        }

        val scenePoint = camera.fromCameraView(anchor)
        camera = Camera(
                Point2D.Float(
                        camera.origin.x + scenePoint.x * camera.zoom.x * (scale - 1.0f),
                        camera.origin.y + scenePoint.y * camera.zoom.y * (scale - 1.0f)
                ),
                Point2D.Float(camera.zoom.x * scale, camera.zoom.y * scale)
        )
        repaint()
    }

    fun fromCameraView(point: Point2D.Float): Point2D.Float {
        return camera.fromCameraView(point)
    }

    fun parentableElementAt(point: Point2D.Float): BpmnElementId {
        return parentableElemUnderCursor(point)
    }

    private fun applyOrthoAnchors(anchorX: AnchorDetails?, anchorY: AnchorDetails?, ctx: ElementInteractionContext): AnchorHit {
        val selectedAnchors: MutableMap<AnchorType, Point2D.Float> = mutableMapOf()
        val targetX = anchorX?.let { ctx.dragCurrent.x + it.anchor.x - it.objectAnchor.x } ?: ctx.dragCurrent.x
        val targetY = anchorY?.let { ctx.dragCurrent.y + it.anchor.y - it.objectAnchor.y } ?: ctx.dragCurrent.y
        val objectAnchorX = anchorX?.objectAnchor?.x ?: ctx.dragCurrent.x
        val objectAnchorY = anchorY?.objectAnchor?.y ?: ctx.dragCurrent.y
        anchorX?.apply { selectedAnchors[AnchorType.HORIZONTAL] = this.anchor }
        anchorY?.apply { selectedAnchors[AnchorType.VERTICAL] = this.anchor }
        return AnchorHit(Point2D.Float(targetX, targetY), Point2D.Float(objectAnchorX, objectAnchorY), selectedAnchors)
    }

    private fun applyPointAnchor(anchor: AnchorDetails, ctx: ElementInteractionContext): AnchorHit {
        val selectedAnchors: MutableMap<AnchorType, Point2D.Float> = mutableMapOf()
        val targetX = ctx.dragCurrent.x + anchor.anchor.x - anchor.objectAnchor.x
        val targetY = ctx.dragCurrent.y + anchor.anchor.y - anchor.objectAnchor.y
        selectedAnchors[AnchorType.POINT] = anchor.anchor
        return AnchorHit(Point2D.Float(targetX, targetY), Point2D.Float(targetX, targetY), selectedAnchors)
    }

    private fun bpmnElemsUnderDragCurrent(): Map<BpmnElementId, AreaWithZindex> {
        val onScreen = camera.toCameraView(interactionCtx.dragCurrent)
        val cursor = cursorRect(onScreen)
        // Correct order would require non-layered but computed z-index
        val elems = areaByElement
                ?.filter { it.value.area.intersects(cursor) }
                ?.toList()
                ?.sortedByDescending { it.second.index }
                ?.filter { !interactionCtx.draggedIds.contains(it.first) && !selectedElements.contains(it.first) } ?: emptyList()

        val result = linkedMapOf<BpmnElementId, AreaWithZindex>()
        for (elem in elems) {
            val elemId = elem.second.bpmnElementId
            if (null == elemId || result.containsKey(elemId)) {
                continue
            }

            val bpmnId = setOf(stateProvider.currentState().elementByDiagramId[elem.first], elem.second.bpmnElementId).filterNotNull().firstOrNull() ?: continue
            val bpmnElem = stateProvider.currentState().elementByBpmnId[bpmnId]
            if (bpmnElem?.element is BpmnSequenceFlow) {
                continue
            }
            result[bpmnId] = elem.second
        }

        return result
    }

    private fun parentableElemUnderCursor(cursorPoint: Point2D.Float): BpmnElementId {
        val withinRect = cursorRect(cursorPoint)
        val intersection = areaByElement?.filter { it.value.area.intersects(withinRect) }
        val shapesThatCanParent = setOf(AreaType.PARENT_PROCESS_SHAPE, AreaType.SHAPE_THAT_NESTS)

        return intersection
                ?.filter { null != it.value.bpmnElementId }
                ?.filter { shapesThatCanParent.contains(it.value.areaType) }
                ?.toList()
                ?.sortedByDescending { it.second.index }
                ?.map { it.second.bpmnElementId }
                ?.filterNotNull()
                ?.first()!!
    }

    private fun elemUnderCursor(cursorPoint: Point2D.Float, excludeAreas: Set<AreaType> = setOf(AreaType.PARENT_PROCESS_SHAPE)): List<DiagramElementId> {
        val withinRect = cursorRect(cursorPoint)
        val intersection = areaByElement?.filter { it.value.area.intersects(withinRect) }
        val maxZindex = intersection?.maxBy { it.value.index }
        val result = mutableListOf<DiagramElementId>()
        val centerRect = Point2D.Float(withinRect.centerX.toFloat(), withinRect.centerY.toFloat())
        intersection
                ?.filter { !excludeAreas.contains(it.value.areaType) }
                ?.filter { it.value.index == maxZindex?.value?.index }
                ?.minBy { Point2D.Float(it.value.area.bounds2D.centerX.toFloat(), it.value.area.bounds2D.centerY.toFloat()).distance(centerRect) }
                ?.let { result += it.key; it.value.parentToSelect?.apply { result += this } }
        return result
    }

    private fun elemsUnderRect(withinRect: Rectangle2D, excludeAreas: Set<AreaType> = setOf(AreaType.PARENT_PROCESS_SHAPE)): List<DiagramElementId> {
        val intersection = areaByElement?.filter { withinRect.contains(it.value.area.bounds2D) }

        val result = mutableListOf<DiagramElementId>()
        intersection
                ?.filter { !excludeAreas.contains(it.value.areaType) }
                ?.forEach { result += it.key; it.value.parentToSelect?.apply { result += this } }
        return result
    }

    private fun dragTargettableElements(withinRect: Rectangle2D, excludeAreas: Set<AreaType> = setOf(AreaType.PARENT_PROCESS_SHAPE)): List<DiagramElementId> {
        val intersection = areaByElement?.filter { withinRect.intersects(it.value.area.bounds2D) }

        val result = mutableListOf<DiagramElementId>()
        intersection
                ?.filter { !excludeAreas.contains(it.value.areaType) }
                ?.forEach { result += it.key; it.value.parentToSelect?.apply { result += this } }
        return result
    }


    private fun setupGraphics(graphics: Graphics): Graphics2D {
        // set up the drawing panel
        val graphics2D = graphics as Graphics2D
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // fill the background for entire canvas
        graphics2D.color = Colors.BACKGROUND_COLOR.color
        graphics2D.fillRect(0, 0, this.width, this.height)

        return graphics2D
    }

    // to handle small area shapes
    private fun cursorRect(location: Point2D.Float): Rectangle2D {
        return Rectangle2D.Float(
                location.x - settings.baseCursorSize / 2.0f,
                location.y - settings.baseCursorSize / 2.0f,
                settings.baseCursorSize,
                settings.baseCursorSize
        )
    }

    private data class AnchorDetails(
            val anchor: Point2D.Float,
            val objectAnchor: Point2D.Float,
            val type: AnchorType
    )
}