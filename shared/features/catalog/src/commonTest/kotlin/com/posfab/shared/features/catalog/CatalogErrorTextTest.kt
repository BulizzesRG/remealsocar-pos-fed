package com.posfab.shared.features.catalog

import com.posfab.shared.core.result.AppError
import com.posfab.shared.features.catalog.common.CatalogErrorText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogErrorTextTest {
    @Test
    fun known_bed_messages_map_to_spanish() {
        assertEquals(
            "Un producto de precio abierto no se cuenta",
            CatalogErrorText.from(AppError.Validation("OPEN products cannot be counted")),
        )
        assertEquals(
            "Por kilo no lleva piezas por paquete",
            CatalogErrorText.from(AppError.Validation("WEIGHT products cannot have piecesPerPack")),
        )
        assertEquals(
            "El precio con tarjeta no puede ser menor que el de efectivo",
            CatalogErrorText.from(AppError.Validation("listTakeaway must be >= cashTakeaway")),
        )
    }

    @Test
    fun unknown_validation_message_is_wrapped() {
        val text = CatalogErrorText.from(AppError.Validation("something new"))
        assertTrue(text.startsWith("No se pudo guardar"))
    }

    @Test
    fun conflict_means_code_already_used() {
        assertEquals("Ese código ya lo tiene otro producto", CatalogErrorText.from(AppError.Conflict))
    }
}
