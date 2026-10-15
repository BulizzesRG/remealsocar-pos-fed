package com.posfab.shared.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalNamesTest {
    @Test
    fun known_codes_map_to_display_names() {
        assertEquals("Caja 1", terminalDisplayName("POS1"))
        assertEquals("Caja 2", terminalDisplayName("POS2"))
        assertEquals("Cocina", terminalDisplayName("COCINA"))
    }

    @Test
    fun lowercase_codes_are_recognized() {
        assertEquals("Caja 1", terminalDisplayName("pos1"))
        assertEquals("Cocina", terminalDisplayName("cocina"))
    }

    @Test
    fun unknown_code_is_returned_unchanged() {
        assertEquals("SURTIDO", terminalDisplayName("SURTIDO"))
        assertEquals("pos9", terminalDisplayName("pos9"))
    }
}
