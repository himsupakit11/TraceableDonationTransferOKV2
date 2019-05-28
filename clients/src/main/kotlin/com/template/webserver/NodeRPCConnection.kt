package com.template.webserver

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCException
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.finance.plugin.registerFinanceJSONMappers
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootApplication
@Configuration
@ConfigurationProperties
class NodeRPCConnection{
//    @Value("\${corda.host}")
//    lateinit var cordaHost: String
//
//    @Value("\${corda.user}")
//    lateinit var cordaUser: String
//
//    @Value("\${corda.password}")
//    lateinit var cordaPassword: String
/**
 * Notary = 10003
 * Fundraiser = 10006
 * Bank = 10009
 * Recipient = 10012
 * Donor = 10015*/
//    val cordaHost = "localhost:10006" //Fundraiser
//    val cordaHost = "localhost:10009" //Bank
//    val cordaHost = "localhost:10012" //Recipient
    val cordaHost = "localhost:10015" //Donor
     val cordaUser = "user1"
     val cordaPassword = "test"

    @Bean
    fun rpcClient(): CordaRPCOps {
        log.info("Connecting to Corda on $cordaHost using username $cordaUser and password $cordaPassword")
        // TODO remove this when CordaRPC gets proper connection retry, please
        var maxRetries = 100
        do {
            try {
                return CordaRPCClient(NetworkHostAndPort.parse(cordaHost)).start(cordaUser, cordaPassword).proxy
            } catch (ex: RPCException) {
                if (maxRetries-- > 0) {
                    Thread.sleep(1000)
                } else {
                    throw ex
                }
            }
        } while (true)
    }

    @Bean
    fun objectMapper(@Autowired cordaRPCOps: CordaRPCOps): ObjectMapper {
        val mapper = JacksonSupport.createDefaultMapper(cordaRPCOps)
        registerFinanceJSONMappers(mapper)
        return mapper
    }

    // running as standalone java app
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(NodeRPCConnection::class.java, *args)
            log.info("APPLICATION STARTED")
        }
    }
}


///**
// * Wraps an RPC connection to a Corda node.
// *
// * The RPC connection is configured using command line arguments.
// *
// * @param host The host of the node we are connecting to.
// * @param rpcPort The RPC port of the node we are connecting to.
// * @param username The username for logging into the RPC client.
// * @param password The password for logging into the RPC client.
// * @property proxy The RPC proxy.
// */
//@Component
//open class NodeRPCConnection(
//        @Value("\${$CORDA_NODE_HOST}") private val host: String,
//        @Value("\${$CORDA_USER_NAME}") private val username: String,
//        @Value("\${$CORDA_USER_PASSWORD}") private val password: String,
//        @Value("\${$CORDA_RPC_PORT}") private val rpcPort: Int): AutoCloseable {
//
//    lateinit var rpcConnection: CordaRPCConnection
//        private set
//    lateinit var proxy: CordaRPCOps
//        private set
//
//    @PostConstruct
//    fun initialiseNodeRPCConnection() {
//            val rpcAddress = NetworkHostAndPort(host, rpcPort)
//            val rpcClient = CordaRPCClient(rpcAddress)
//            val rpcConnection = rpcClient.start(username, password)
//            proxy = rpcConnection.proxy
//    }
//
//    @PreDestroy
//    override fun close() {
//        rpcConnection.notifyServerAndClose()
//    }
//}

