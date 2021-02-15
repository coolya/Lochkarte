package ws.logv.lochkarte.ide

import com.sun.istack.logging.Logger
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.HttpRequestHandler
import ws.logv.lochkarte.template.github.CheckResult
import ws.logv.lochkarte.template.github.checkUrl

const val URL_PREFIX = "/lochkarte/"
const val URL_QUERY_PARAMETER = "repo_url"
const val API = "api"

class CreateProjectRequestHandler : HttpRequestHandler() {

    private val logger = Logger.getLogger(this.javaClass)


    override fun isSupported(request: FullHttpRequest): Boolean {
        return (request.method() == HttpMethod.GET || request.method() == HttpMethod.POST)  && (request.uri().startsWith(URL_PREFIX))
    }

    override fun process(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): Boolean {
        val path = urlDecoder.path()

        when {
            request.isGet && path.endsWith(URL_PREFIX + "ping") -> logger.info("ping")
            request.isPost && path.endsWith(URL_PREFIX + "create") -> {
                //TODO: return code
                val repoUrl = urlDecoder.parameters()[URL_QUERY_PARAMETER]?.firstOrNull() ?: return false

                when(val checkResult = checkUrl(repoUrl)) {
                    // TODO: forward error message
                    is CheckResult.Error -> return false
                }


                logger.info("create")
            }
            else -> return false
        }
        return false
    }
}


private val FullHttpRequest.isGet
    get() = this.method() == HttpMethod.GET

private val FullHttpRequest.isPost
    get() = this.method() == HttpMethod.POST