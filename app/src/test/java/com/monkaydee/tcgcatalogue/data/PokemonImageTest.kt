package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.remote.pokemonTcgImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PokemonImageTest {
    @Test fun mapsTcgdexIdsToPokemonTcgIo() {
        assertEquals("https://images.pokemontcg.io/swsh12tg/TG11_hires.png", pokemonTcgImage("swsh12tg-TG11", large = true))
        assertEquals("https://images.pokemontcg.io/sv3pt5/151.png", pokemonTcgImage("sv03.5-151", large = false))
        assertEquals("https://images.pokemontcg.io/swsh12pt5gg/GG01.png", pokemonTcgImage("swsh12.5gg-GG01", large = false))
        assertEquals("https://images.pokemontcg.io/sm75/60.png", pokemonTcgImage("sm7.5-60", large = false))
        assertEquals("https://images.pokemontcg.io/swsh45sv/SV090.png", pokemonTcgImage("swsh4.5sv-SV090", large = false))
        assertNull(pokemonTcgImage("ja:SV2a-001", large = false))
    }
}
