package com.valb3r.bpmn.intellij.plugin.render

import com.intellij.psi.PsiFile
import com.intellij.ui.EditorTextField
import com.valb3r.bpmn.intellij.plugin.events.updateEventsRegistry
import com.valb3r.bpmn.intellij.plugin.flowable.parser.FlowableParser
import javax.swing.JTable

class CanvasBuilder {

    private val updateEvents = updateEventsRegistry()

    fun build(properties: JTable, editorFactory: (value: String) -> EditorTextField, canvas: Canvas, bpmnFile: PsiFile) {
        updateEvents.reset()
        bpmnFile.virtualFile.inputStream.use {
            val process = FlowableParser().parse(it)
            canvas.reset(properties, editorFactory, process.toView(), BpmnProcessRenderer())
        }
    }
}