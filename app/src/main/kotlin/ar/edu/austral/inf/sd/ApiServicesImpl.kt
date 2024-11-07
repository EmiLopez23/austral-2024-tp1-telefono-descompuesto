package ar.edu.austral.inf.sd

import ar.edu.austral.inf.sd.server.api.PlayApiService
import ar.edu.austral.inf.sd.server.api.RegisterNodeApiService
import ar.edu.austral.inf.sd.server.api.RelayApiService
import ar.edu.austral.inf.sd.server.api.BadRequestException
import ar.edu.austral.inf.sd.server.api.GatewayTimeoutException
import ar.edu.austral.inf.sd.server.api.InternalServerErrorException
import ar.edu.austral.inf.sd.server.api.ReconfigureApiService
import ar.edu.austral.inf.sd.server.api.UnauthorizedException
import ar.edu.austral.inf.sd.server.api.ServiceUnavailableException
import ar.edu.austral.inf.sd.server.api.UnregisterNodeApiService
import ar.edu.austral.inf.sd.server.model.Node
import ar.edu.austral.inf.sd.server.model.PlayResponse
import ar.edu.austral.inf.sd.server.model.RegisterResponse
import ar.edu.austral.inf.sd.server.model.Signature
import ar.edu.austral.inf.sd.server.model.Signatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.system.exitProcess

@Component
class ApiServicesImpl : RegisterNodeApiService, RelayApiService, PlayApiService, UnregisterNodeApiService,
    ReconfigureApiService {

    @Value("\${server.name:nada}")
    private val myServerName: String = ""
    @Value("\${server.port:8080}")
    private val myServerPort: Int = 0
    @Value("\${server.host:localhost}")
    private val myServerHost: String = "localhost"
    @Value("\${server.timeout:20}")
    private val timeout: Int = 20
    @Value("\${register.host:localhost}")
    private val registerHost: String = "localhost"
    @Value("\${register.port:8081}")
    private val registerPort: Int = 8081


    private var timeoutsAmount = 0
    private val nodes: MutableList<Node> = mutableListOf()


    private var xGameTimestamp: Int = 0
    private var nextNode: RegisterResponse? = null

    private var nodeTimeout = -1
    private var nodeTimestamp = -1
    private var nodeUUID=UUID.randomUUID()
    private val nodeSalt = newSalt()
    private val messageDigest = MessageDigest.getInstance("SHA-512")


    private val currentRequest
        get() = (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
    private var resultReady = CountDownLatch(1)
    private var currentMessageWaiting = MutableStateFlow<PlayResponse?>(null)
    private var currentMessageResponse = MutableStateFlow<PlayResponse?>(null)

    override fun registerNode(host: String?, port: Int?, uuid: UUID?, salt: String?, name: String?): ResponseEntity<RegisterResponse> {
        val existingNode = nodes.find { it.uuid == uuid }

        if (existingNode !== null){
            //HTTP 401 if uuid already exists but salt is invalid
            if(existingNode.salt !== salt) throw UnauthorizedException("Invalid salt")

            val nextIndex = nodes.indexOf(existingNode) -1
            val nextNode = nodes[nextIndex]
            val response = RegisterResponse(nextNode.host, nextNode.port, timeout, xGameTimestamp)
            //HTTP 202 node already exists and both salt and UUID are the same
            return ResponseEntity(response, HttpStatus.ACCEPTED)
        }

        val nextNode: RegisterResponse = if (nodes.isEmpty()) {
            // if nodes list is empty then I'm the next node so requester has to send to me the data
            val me = RegisterResponse(myServerHost, myServerPort, timeout, xGameTimestamp)
            val node = Node(myServerHost, myServerPort, myServerName, nodeUUID, nodeSalt)
            nodes.add(node)
            me
        } else {
            val latestNode = nodes.last()
            val node = RegisterResponse(latestNode.host, latestNode.port,timeout, xGameTimestamp)
            node
        }

        //register the requester as a node in the list
        val node = Node(host!!, port!!, name!!, uuid!!, salt!!)
        nodes.add(node)

        val response = RegisterResponse(nextNode.nextHost, nextNode.nextPort, timeout, xGameTimestamp)
        //HTTP 200 if register node was successful and has been added to node list.
        return ResponseEntity(response, HttpStatus.OK)
    }

    override fun relayMessage(message: String, signatures: Signatures, xGameTimestamp: Int?): Signature {
        val receivedHash = doHash(message.encodeToByteArray(), nodeSalt)
        val receivedContentType = currentRequest.getPart("message")?.contentType ?: "nada"
        val receivedLength = message.length
        if (nextNode != null) {
            val updatedSignatures = signatures.items + clientSign(message, receivedContentType)
            sendRelayMessage(message, receivedContentType, nextNode!!, Signatures(updatedSignatures), xGameTimestamp!!)
        } else {
            // me llego algo, no lo tengo que pasar
            if (currentMessageWaiting.value == null) throw BadRequestException("no waiting message")
            val current = currentMessageWaiting.getAndUpdate { null }!!
            val response = current.copy(
                contentResult = if (receivedHash == current.originalHash) "Success" else "Failure",
                receivedHash = receivedHash,
                receivedLength = receivedLength,
                receivedContentType = receivedContentType,
                signatures = signatures
            )
            currentMessageResponse.update { response }
            resultReady.countDown()
        }
        return Signature(
            name = myServerName,
            hash = receivedHash,
            contentType = receivedContentType,
            contentLength = receivedLength
        )
    }

    override fun sendMessage(body: String): PlayResponse {
        //HTTP 400 game is closed and can't receive more plays
        if (timeoutsAmount > timeout) throw BadRequestException("Timeout reached, game is closed")

        if (nodes.isEmpty()) {
            // if node list is empty then start with me as the next node
            val me = Node(currentRequest.serverName, myServerPort, myServerName, nodeUUID,nodeSalt)
            nodes.add(me)
        }

        currentMessageWaiting.update { newResponse(body) }

        val contentType = currentRequest.contentType

        val lastNode= nodes.last()

        val responseNode= RegisterResponse(lastNode.host, lastNode.port, timeout, xGameTimestamp)

        sendRelayMessage(body, contentType,responseNode, Signatures(listOf()), xGameTimestamp)

        // wait until timeout is reached
        resultReady.await(timeout.toLong(), TimeUnit.SECONDS)
        resultReady = CountDownLatch(1)

        if (currentMessageResponse.value==null){
            timeoutsAmount++
            //HTTP 504 relay was not received within the expected time
            throw GatewayTimeoutException("Relay was not received on time")
        }

        if(doHash(body.encodeToByteArray(), nodeSalt) !== currentMessageResponse.value!!.receivedHash){
            //HTTP 503 message didn't return as expected
            throw ServiceUnavailableException("Response not received")
        }

        if (!validateSignatures(body)){
            //HTTP 500 message is correct but there are missing signatures
            throw InternalServerErrorException("Missing signatures")
        }

        return currentMessageResponse.value!!
    }

    private fun validateSignatures(body: String): Boolean{
        val bodyBytes = body.encodeToByteArray()
        val expectedSignaturesSet = nodes.mapTo(HashSet()) { node ->
            doHash(bodyBytes, node.salt)
        }
        val currentSignatureHashSet = currentMessageResponse.value!!.signatures.items.mapTo(HashSet()) { it.hash }

        return currentSignatureHashSet.containsAll(expectedSignaturesSet)
    }

    override fun unregisterNode(uuid: UUID?, salt: String?): String {
        val node= nodes.find {node -> node.uuid == uuid!! && node.salt == salt!! }
        if (node == null ){
            throw BadRequestException("invalid UUID or salt")
        }
        val nodeIndex = nodes.indexOf(node)
        val isLastNode = nodeIndex == nodes.size -1
        if (isLastNode){
            nodes.removeAt(nodeIndex)
        }
        else{
            val previousNode= nodes[nodeIndex+1]
            val nextNode= nodes[nodeIndex-1]
            val url="http://${previousNode.host}:${previousNode.port}/reconfigure" +
                    "?uuid=${previousNode.uuid}&salt=${previousNode.salt}&nextHost=${nextNode.host}&nextPort=${nextNode.port}"
            val restTemplate= RestTemplate()
            val httpHeaders = HttpHeaders().apply {
                add("X-Game-Timestamp", xGameTimestamp.toString())
            }
            try{
                val httpEntity= HttpEntity<Nothing?>(httpHeaders)
                restTemplate.postForEntity<String>(url, httpEntity)
                nodes.removeAt(nodeIndex)
            }
            catch (e: RestClientException){
                println(e.message)
                throw ServiceUnavailableException("Error while trying to unregister node")
            }
        }
        return "Node unregistered"
    }

    override fun reconfigure(
        uuid: UUID?,
        salt: String?,
        nextHost: String?,
        nextPort: Int?,
        xGameTimestamp: Int?
    ): String {
        if (uuid == nodeUUID && salt == nodeSalt){
            throw BadRequestException("Invalid data")
        }

        nextNode= RegisterResponse(nextHost!!, nextPort!!, timeout, xGameTimestamp!!)
        return "Node reconfigured"
    }

    internal fun registerToServer(registerHost: String, registerPort: Int) {
        val restTemplate= RestTemplate()
        val registerUrl = "http://$registerHost:$registerPort/register-node"
        val registerParams = "?host=localhost&port=$myServerPort&name=$myServerName&uuid=$nodeUUID&salt=$nodeSalt&name=$myServerName"
        val url = registerUrl + registerParams


        try {
            val response = restTemplate.postForEntity<RegisterResponse>(url)
            val registerNodeResponse: RegisterResponse = response.body!!
            println("nextNode = $registerNodeResponse")
            xGameTimestamp = registerNodeResponse.xGameTimestamp
            nodeTimeout = registerNodeResponse.timeout
            nextNode = with(registerNodeResponse) {
                RegisterResponse(nextHost, nextPort, timeout, registerNodeResponse.xGameTimestamp)
            }
        } catch (e: RestClientException){
            println("Could not register to: $registerUrl")
            println("Params: $registerParams")
            println("Error: ${e.message}")
            println("Shutting down")
            exitProcess(1)
        }
    }

    private fun sendRelayMessage(
        body: String,
        contentType: String,
        relayNode: RegisterResponse,
        signatures: Signatures,
        timestamp: Int
    ) {
        if (timestamp < nodeTimestamp){
            //HTTP 400 if X-Game-Timestamp header is invalid.
            throw BadRequestException("Invalid timestamp")
        }

        val restTemplate = RestTemplate()
        val relayEndpoint = "http://${relayNode.nextHost}:${relayNode.nextPort}/relay"

        //create client signature and add it to signatures list
        val clientSignature = clientSign(body, contentType)
        val updatedSignatures = signatures.items + clientSignature
        val newSignatures = Signatures(updatedSignatures)

        //add content-type header
        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType(contentType)

        // Create HttpEntity for message
        val messageEntity = HttpEntity(body, headers)

        // Add the message and signatures into multipart map
        val multiPartBody: MultiValueMap<String, Any> = LinkedMultiValueMap<String, Any>().apply {
            add("message", messageEntity)
            add("signatures", newSignatures)
        }

        //set content type and timestamp header
        val requestHeaders = HttpHeaders().apply {
            setContentType(MediaType.MULTIPART_FORM_DATA)
            add("X-Game-Timestamp", timestamp.toString())
        }
        val request = HttpEntity(multiPartBody, requestHeaders)

        try {
            restTemplate.postForEntity(relayEndpoint, request, Signature::class.java)
            nodeTimestamp=timestamp
        } catch (e: RestClientException) {
            // Send failed play to center node
            val hostUrl = "http://${registerHost}:${registerPort}/relay"
            restTemplate.postForEntity<Map<String, Any>>(hostUrl, request)

            throw ServiceUnavailableException("Error while trying to send relay")
        }
    }

    private fun clientSign(message: String, contentType: String): Signature {
        val receivedHash = doHash(message.encodeToByteArray(), nodeSalt)
        return Signature(myServerName, receivedHash, contentType, message.length)
    }

    private fun newResponse(body: String) = PlayResponse(
        "Unknown",
        currentRequest.contentType,
        body.length,
        doHash(body.encodeToByteArray(), nodeSalt),
        "Unknown",
        -1,
        "N/A",
        Signatures(listOf())
    )

    private fun doHash(body: ByteArray, salt: String): String {
        val saltBytes = Base64.getDecoder().decode(salt)
        messageDigest.update(saltBytes)
        val digest = messageDigest.digest(body)
        return Base64.getEncoder().encodeToString(digest)
    }

    companion object {
        fun newSalt(): String = Base64.getEncoder().encodeToString(Random.nextBytes(9))
    }
}