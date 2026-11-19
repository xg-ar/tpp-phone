package ar.com.tpp.phone.prov

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProvResultsTest {
    @Test
    fun startSent() = assertEquals(
        StartResult.Sent("abc", 30),
        parseStart(200, """{"id_verificacion":"abc","reenviar_en":30}""")
    )

    @Test
    fun startErrors() {
        assertEquals(StartResult.InvalidNumber, parseStart(400, """{"error":"numero_invalido"}"""))
        assertEquals(StartResult.RateLimited, parseStart(429, """{"error":"limite"}"""))
    }

    @Test
    fun startOddBodies() {
        assertEquals(StartResult.Failed, parseStart(502, "<html>Bad Gateway</html>"))
        assertEquals(StartResult.Failed, parseStart(200, ""))
        assertEquals(StartResult.Failed, parseStart(200, """{"reenviar_en":30}"""))
        assertEquals(StartResult.Failed, parseStart(-1, null))
    }

    @Test
    fun activated() = assertEquals(
        ActivationResult.Activated(LineConfig("5491162406120", "s3cr", "fs.xg.net.ar", "91162406120")),
        parseActivation(
            200,
            """{"usuario_sip":"5491162406120","contrasena":"s3cr","dominio":"fs.xg.net.ar","numero_para_interno":"91162406120","expira_registro":604800}"""
        )
    )

    @Test
    fun activationErrors() {
        assertEquals(
            ActivationResult.WrongCode(3),
            parseActivation(422, """{"error":"codigo_incorrecto","intentos_restantes":3}""")
        )
        assertEquals(ActivationResult.Expired, parseActivation(410, """{"error":"codigo_vencido"}"""))
        assertEquals(ActivationResult.ActivationFailed, parseActivation(503, """{"error":"activacion_fallida"}"""))
        assertEquals(ActivationResult.InvalidNumber, parseActivation(400, """{"error":"numero_invalido"}"""))
        assertEquals(ActivationResult.RateLimited, parseActivation(429, """{"error":"limite"}"""))
    }

    @Test
    fun activationOddBodies() {
        assertEquals(ActivationResult.Failed, parseActivation(422, """{"detail":[{"msg":"x"}]}"""))
        assertEquals(ActivationResult.Failed, parseActivation(503, ""))
        assertEquals(ActivationResult.Failed, parseActivation(200, """{"usuario_sip":"1"}"""))
        assertEquals(ActivationResult.Failed, parseActivation(-1, null))
    }

    @Test
    fun lineStatus() {
        assertEquals(LineStatus.ACTIVE, parseLineStatus(200, """{"estado":"activa"}"""))
        assertEquals(LineStatus.REPLACED, parseLineStatus(200, """{"estado":"reemplazada"}"""))
        assertEquals(LineStatus.UNAUTHORIZED, parseLineStatus(401, """{"error":"no_autorizado"}"""))
        assertEquals(LineStatus.UNKNOWN, parseLineStatus(429, """{"error":"limite"}"""))
        assertEquals(LineStatus.UNKNOWN, parseLineStatus(-1, null))
        assertEquals(LineStatus.UNKNOWN, parseLineStatus(200, "x"))
    }

    @Test
    fun passwordNeverInToString() =
        assertFalse(LineConfig("5491162406120", "s3cr", "fs.xg.net.ar", "91162406120").toString().contains("s3cr"))

    @Test
    fun helpKey() {
        assertEquals(HelpKeyResult.Created("K7QF-2MXP"), parseHelpKey(200, """{"clave":"K7QF-2MXP","vence_en_min":15}"""))
        assertEquals(HelpKeyResult.RateLimited, parseHelpKey(429, """{"error":"limite"}"""))
        assertEquals(HelpKeyResult.Failed, parseHelpKey(200, "{}"))
        assertEquals(HelpKeyResult.Failed, parseHelpKey(-1, null))
    }

    @Test
    fun claimWaitsForTheWhatsAppMessage() {
        assertEquals(ActivationResult.Pending, parseClaim(202, """{"estado":"pendiente"}"""))
        assertEquals(ActivationResult.Expired, parseClaim(410, """{"error":"codigo_vencido"}"""))
        assertEquals(
            ActivationResult.Activated(LineConfig("5491153282884", "s", "fs.xg.net.ar", "91153282884")),
            parseClaim(200, """{"usuario_sip":"5491153282884","contrasena":"s","dominio":"fs.xg.net.ar","numero_para_interno":"91153282884"}""")
        )
    }

    @Test
    fun testCall() {
        assertEquals(TestCallResult.SCHEDULED, parseTestCall(202, """{"en_segundos":60}"""))
        assertEquals(TestCallResult.IN_PROGRESS, parseTestCall(429, """{"error":"limite","motivo":"en_curso"}"""))
        assertEquals(TestCallResult.LIMIT_DAY, parseTestCall(429, """{"error":"limite","motivo":"linea"}"""))
        assertEquals(TestCallResult.BUSY, parseTestCall(429, """{"error":"limite","motivo":"global"}"""))
        assertEquals(TestCallResult.BUSY, parseTestCall(429, """{"error":"limite"}"""))
        assertEquals(TestCallResult.UNAVAILABLE, parseTestCall(503, """{"error":"no_disponible"}"""))
        assertEquals(TestCallResult.UNAUTHORIZED, parseTestCall(401, """{"error":"no_autorizado"}"""))
        assertEquals(TestCallResult.FAILED, parseTestCall(-1, null))
        assertEquals(TestCallResult.FAILED, parseTestCall(500, "<html>"))
    }

    @Test
    fun deleteAccount() {
        assertEquals(DeleteResult.DELETED, parseDeleteAccount(200))
        assertEquals(DeleteResult.UNAUTHORIZED, parseDeleteAccount(401))
        assertEquals(DeleteResult.FAILED, parseDeleteAccount(503))
        assertEquals(DeleteResult.FAILED, parseDeleteAccount(500))
        assertEquals(DeleteResult.FAILED, parseDeleteAccount(-1))
    }
}
