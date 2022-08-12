package club.bottomservices.totems

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.github.reugn.sketch.util.SketchIO
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.system.exitProcess

fun main() {
    val prop = Properties()
    File("./config.properties").let { file ->
        if (file.exists()) {
            file.inputStream().use { prop.load(it) }
        }
    }
    val gson = GsonBuilder().setPrettyPrinting().create()
    val html = String(Thread.currentThread().contextClassLoader.getResourceAsStream("index.html")?.use { it.readBytes() } ?: exitProcess(1))
    embeddedServer(Netty, port = ((prop["port"] as String?)?.toInt() ?: 8080), host = (prop["host"] ?: "127.0.0.1") as String) {
        routing {
            get("/get-totem") {
                val data = call.request.queryParameters
                val username = data["username"]
                val version = data["version"]?.toIntOrNull()

                if (username == null || version == null) {
                    call.response.status(HttpStatusCode.BadRequest)
                    return@get
                }

                val image = withContext(Dispatchers.IO) {
                    URL("https://minotar.net/body/$username/64.png").openStream().use {
                        SketchIO.load(it)
                    }
                }

                val yScale = 64f / image.asBufferedImage().height

                val resized = image.resize(1f, yScale)

                val mcMeta = gson.toJson(JsonObject().apply { add("pack", JsonObject().apply {
                    addProperty("pack_format", version)
                    addProperty("description", "Generated totem")
                })}).toByteArray()

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "totem-pack.zip").toString()
                )

                @Suppress("BlockingMethodInNonBlockingContext")
                call.respondBytes(ContentType.Application.Zip) {
                    val internalStream = ByteArrayOutputStream()
                    val ostream = ZipOutputStream(internalStream)

                    ostream.putNextEntry(ZipEntry("assets/minecraft/textures/${if (version == 3) "items/totem.png" else "item/totem_of_undying.png"}")) // This isn't actual IO, stop yelling at me intellij :sob:
                    ImageIO.write(resized.asBufferedImage(), "png", ostream)
                    ostream.closeEntry()
                    ostream.putNextEntry(ZipEntry("pack.mcmeta"))
                    ostream.write(mcMeta)
                    ostream.closeEntry()
                    ostream.close()
                    return@respondBytes internalStream.toByteArray()
                }
            }

            get {
                call.respondText(html, ContentType.Text.Html)
            }
        }
    }.start(true)
}
