package com.valb3r.bpmn.intellij.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.EditorTextField
import com.intellij.util.messages.MessageBusConnection
import com.valb3r.bpmn.intellij.plugin.bpmn.api.BpmnParser
import com.valb3r.bpmn.intellij.plugin.events.FileCommitter
import com.valb3r.bpmn.intellij.plugin.events.initializeUpdateEventsRegistry
import com.valb3r.bpmn.intellij.plugin.events.updateEventsRegistry
import com.valb3r.bpmn.intellij.plugin.flowable.parser.FlowableObjectFactory
import com.valb3r.bpmn.intellij.plugin.newelements.NewElementsProvider
import com.valb3r.bpmn.intellij.plugin.newelements.newElementsFactory
import com.valb3r.bpmn.intellij.plugin.render.BpmnProcessRenderer
import com.valb3r.bpmn.intellij.plugin.render.Canvas
import java.nio.charset.StandardCharsets.UTF_8
import javax.swing.JTable


class CanvasBuilder(val bpmnProcessRenderer: BpmnProcessRenderer) {

    private var newObjectsFactory: NewElementsProvider? = null
    private var currentConnection: MessageBusConnection? = null

    fun build(committerFactory: (BpmnParser) -> FileCommitter, parser: BpmnParser, properties: JTable, editorFactory: (value: String) -> EditorTextField, canvas: Canvas, project: Project, bpmnFile: VirtualFile) {
        initializeUpdateEventsRegistry(committerFactory.invoke(parser))

        val data = readFile(bpmnFile)
        val process = parser.parse(data)
        newObjectsFactory = newElementsFactory(FlowableObjectFactory())
        canvas.reset(properties, editorFactory, data, process.toView(newObjectsFactory!!), bpmnProcessRenderer)

        currentConnection?.let { it.disconnect(); it.dispose() }
        currentConnection = attachFileChangeListener(project, bpmnFile) {
            build(committerFactory, parser, properties, editorFactory, canvas, project, it)
        }
    }

    private fun attachFileChangeListener(project: Project, bpmnFile: VirtualFile, onUpdate: ((bpmnFile: VirtualFile) -> Unit)): MessageBusConnection {
        val connection = project.messageBus.connect()
        val registry = updateEventsRegistry()

        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                super.after(events)
                val event = events
                        .filter { it.file == bpmnFile }
                        .filterIsInstance<VFileContentChangeEvent>()
                        .lastOrNull()
                        ?: return

                if (!registry.fileStateMatches(readFile(event.file))) {
                    onUpdate(event.file)
                }
            }
        })

        return connection
    }

    private fun readFile(bpmnFile: VirtualFile) = String(bpmnFile.contentsToByteArray(), UTF_8)
}