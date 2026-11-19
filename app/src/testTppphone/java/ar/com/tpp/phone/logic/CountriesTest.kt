package ar.com.tpp.phone.logic

import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CountriesTest {
    private val es = Locale.forLanguageTag("es-AR")
    private val countries = orderedCountries { Locale("", it).getDisplayCountry(es) }

    @Test
    fun priorityOrder() = assertEquals(
        listOf("AR", "BO", "PY", "PE", "CL", "CO", "BR", "UY"),
        countries.take(8).map { it.region }
    )

    @Test
    fun restIsAlphabeticalInSpanish() {
        val rest = countries.drop(8).map { it.name }
        assertEquals(rest.sortedWith(Collator.getInstance(es)), rest)
    }

    @Test
    fun noDuplicates() = assertEquals(countries.size, countries.map { it.region }.toSet().size)

    @Test
    fun argentinaData() {
        val ar = countries.first()
        assertEquals(54, ar.callingCode)
        assertEquals("🇦🇷", ar.flag)
    }

    @Test
    fun defaultRegionRules() {
        assertEquals("BO", defaultRegion("bo", null))
        assertEquals("PY", defaultRegion("", "py"))
        assertEquals("AR", defaultRegion(null, null))
        assertEquals("AR", defaultRegion("zz", "xx"))
    }
}
