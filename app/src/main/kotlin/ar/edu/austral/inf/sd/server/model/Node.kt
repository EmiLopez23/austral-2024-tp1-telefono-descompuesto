package ar.edu.austral.inf.sd.server.model

import java.util.UUID

data class Node(
    val host: String,
    val port: Int,
    val name: String,
    val uuid: UUID,
    val salt: String
)