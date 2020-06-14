package com.valb3r.bpmn.intellij.plugin

import com.intellij.ui.JBColor
import java.awt.Color

enum class Colors(val color: JBColor) {
    SERVICE_TASK_COLOR(JBColor(Color(0xF9F9F9), Color(0x535353))),
    TRANSACTION_COLOR(JBColor(Color(0xFDFEFF), Color(0x292B2D))),
    TRANSACTION_ELEMENT_BORDER_COLOR(JBColor(Color(0x292B2D), Color(0xC9C9C9))),
    PROCESS_COLOR(JBColor(Color(0xFDFEFF), Color(0x292B2D))),
    CALL_ACTIVITY_COLOR(JBColor(Color(0xF9F9F9), Color(0x535353))),
    ELEMENT_BORDER_COLOR(JBColor(Color(0xC9C9C9), Color(0x6E6E6E))),
    SUBPROCESS_TEXT_COLOR(JBColor(Color(0x292B2D), Color(0xBBBBBB))),
    INNER_TEXT_COLOR(JBColor(Color(0x292B2D), Color(0xBBBBBB))),
    ARROW_COLOR(JBColor(Color(0x292B2D), Color(0xBBBBBB))),
    ANCHOR_COLOR(JBColor(Color(0xFFAA00), Color(0xFFAA00))),
    WAYPOINT_COLOR(JBColor(Color(0xFF0000), Color(0xFF0000))),
    MID_WAYPOINT_COLOR(JBColor(Color(0x0088FF), Color(0x0000FF))),
    BACKGROUND_COLOR(JBColor(Color(0xFDFEFF), Color(0x2B2B2B))),
    ACTIONS_BORDER_COLOR(JBColor(Color(0x626466), Color(0x949698))),
    SELECTED_COLOR(JBColor(Color(0x00EE00), Color(0x009900))),
    START_EVENT(JBColor(Color(0x00AA00), Color(0x009900))),
    END_EVENT(JBColor(Color(0xFF0000), Color(0xDD0000))),
}
