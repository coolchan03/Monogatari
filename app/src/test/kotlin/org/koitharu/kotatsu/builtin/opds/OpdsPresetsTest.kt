package org.koitharu.kotatsu.builtin.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsPresetsTest {
    @Test fun presetsAreValidHttpsAndUnique() {
        assertTrue(OpdsPresets.all.isNotEmpty())
        assertTrue(OpdsPresets.all.all(OpdsPresets::isValid))
        assertEquals(OpdsPresets.all.size, OpdsPresets.all.map { it.url }.distinct().size)
        assertEquals(OpdsPresets.all.size, OpdsPresets.all.map { OpdsSource.fromUrl(it.url).name }.distinct().size)
    }

    @Test fun notAddedUntilRegistered() {
        val preset = OpdsPresets.all.first()
        OpdsSourceRegistry.unregister(preset.url)
        assertEquals(false, OpdsPresets.isAdded(preset))
        OpdsSourceRegistry.register(preset.url)
        assertEquals(true, OpdsPresets.isAdded(preset))
        OpdsSourceRegistry.unregister(preset.url)
    }
}
